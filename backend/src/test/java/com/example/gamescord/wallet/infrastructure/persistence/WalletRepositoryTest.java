package com.example.gamescord.wallet.infrastructure.persistence;

import com.example.gamescord.support.IntegrationTestSupport;
import com.example.gamescord.wallet.domain.model.Account;
import com.example.gamescord.wallet.domain.model.AccountType;
import com.example.gamescord.wallet.domain.model.LedgerLine;
import com.example.gamescord.wallet.domain.model.LedgerTransaction;
import com.example.gamescord.wallet.domain.model.RefTableType;
import com.example.gamescord.wallet.domain.model.TransactionType;
import com.example.gamescord.wallet.domain.repository.AccountRepository;
import com.example.gamescord.wallet.domain.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 지갑 리포지토리의 실제 쿼리를 MySQL 컨테이너에서 확인한다.
 * 동시성 확인을 위해 실제로 커밋해야 하므로 @Transactional 대신 TransactionTemplate을 쓴다.
 */
class WalletRepositoryTest extends IntegrationTestSupport {

    private static final long USER_ID = 7L;

    @Autowired AccountRepository accountRepository;
    @Autowired LedgerTransactionRepository ledgerTransactionRepository;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM ledger_entry");
        jdbc.update("DELETE FROM ledger_transaction");
        jdbc.update("DELETE FROM account WHERE owner_user_id <> 0"); // 시스템 계좌는 서버 시작 시 한 번만 만들어지므로 남긴다
    }

    @Test
    @DisplayName("유저 계좌 없으면 생성을 두 번 호출해도 계좌는 하나이고 잔액 0으로 시작한다")
    void insertUserAccountIfAbsentTwice() {
        Account account = Account.createUserAccount(AccountType.USER_COIN, USER_ID);

        tx.executeWithoutResult(s -> accountRepository.insertIfAbsent(account));
        tx.executeWithoutResult(s -> accountRepository.insertIfAbsent(account));

        assertThat(countAccounts(AccountType.USER_COIN)).isEqualTo(1);
        Account saved = findUserCoin();
        assertThat(saved.getOwnerUserId()).isEqualTo(USER_ID);
        assertThat(saved.getBalance()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("시스템 계좌 없으면 생성을 두 번 호출해도 계좌는 하나다 (owner 0이라 UNIQUE에 걸림)")
    void insertSystemAccountIfAbsentTwice() {
        Account account = Account.createSystemAccount(AccountType.COIN_ISSUANCE);

        tx.executeWithoutResult(s -> accountRepository.insertIfAbsent(account));
        tx.executeWithoutResult(s -> accountRepository.insertIfAbsent(account));

        assertThat(countAccounts(AccountType.COIN_ISSUANCE)).isEqualTo(1);
        Account saved = accountRepository
                .findByAccountTypeAndOwnerUserId(AccountType.COIN_ISSUANCE, Account.SYSTEM_OWNER_ID)
                .orElseThrow();
        assertThat(saved.getBalance()).isNull();
    }

    @Test
    @DisplayName("잔액을 더하면 1행이 바뀌고 다시 읽은 잔액에 반영된다")
    void addBalance() {
        Long accountId = createUserCoin();

        Integer updated = tx.execute(s -> accountRepository.addBalance(accountId, 500));

        assertThat(updated).isEqualTo(1);
        assertThat(accountRepository.findBalanceById(accountId)).isEqualTo(500L);
    }

    @Test
    @DisplayName("잔액보다 많이 빼면 0행이 바뀌고 잔액은 그대로다")
    void rejectOverdraw() {
        Long accountId = createUserCoin();
        tx.execute(s -> accountRepository.addBalance(accountId, 300));

        Integer updated = tx.execute(s -> accountRepository.addBalance(accountId, -500));

        assertThat(updated).isZero();
        assertThat(accountRepository.findBalanceById(accountId)).isEqualTo(300L);
    }

    @Test
    @DisplayName("시스템 계좌는 잔액을 관리하지 않으므로 잔액 UPDATE가 0행이다")
    void systemAccountBalanceNotUpdated() {
        tx.executeWithoutResult(s -> accountRepository.insertIfAbsent(
                Account.createSystemAccount(AccountType.COIN_ISSUANCE)));
        Long accountId = accountRepository
                .findByAccountTypeAndOwnerUserId(AccountType.COIN_ISSUANCE, Account.SYSTEM_OWNER_ID)
                .orElseThrow().getId();

        Integer updated = tx.execute(s -> accountRepository.addBalance(accountId, 500));

        assertThat(updated).isZero();
        assertThat(accountRepository.findBalanceById(accountId)).isNull();
    }

    @Test
    @DisplayName("같은 계좌에 동시에 +1을 N번 하면 잔액은 정확히 N이다 (Lost Update 없음)")
    void concurrentAddBalance() throws Exception {
        Long accountId = createUserCoin();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return tx.execute(s -> accountRepository.addBalance(accountId, 1));
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Integer> result : results) {
            succeeded += result.get();
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(threads);
        assertThat(accountRepository.findBalanceById(accountId)).isEqualTo((long) threads);
    }

    @Test
    @DisplayName("같은 (거래 type, ref_type, ref_id) 헤더는 두 번 저장할 수 없다")
    void rejectDuplicateLedgerTransaction() {
        Account issuance = Account.createSystemAccount(AccountType.COIN_ISSUANCE);
        Account user = Account.createUserAccount(AccountType.USER_COIN, USER_ID);
        List<LedgerLine> lines = List.of(new LedgerLine(issuance, -500), new LedgerLine(user, 500));

        tx.executeWithoutResult(s -> ledgerTransactionRepository.save(
                LedgerTransaction.create(TransactionType.CHARGE, RefTableType.CHARGE_ORDER, 123L, lines)));

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ledgerTransactionRepository.save(
                LedgerTransaction.create(TransactionType.CHARGE, RefTableType.CHARGE_ORDER, 123L, lines))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transaction", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 근거라도 거래 type이 다르면 저장된다 (매칭 HOLD 뒤 RELEASE)")
    void allowDifferentTypeForSameRef() {
        Account escrow = Account.createSystemAccount(AccountType.ESCROW);
        Account user = Account.createUserAccount(AccountType.USER_COIN, USER_ID);
        List<LedgerLine> lines = List.of(new LedgerLine(user, -500), new LedgerLine(escrow, 500));

        tx.executeWithoutResult(s -> ledgerTransactionRepository.save(
                LedgerTransaction.create(TransactionType.MATCH_HOLD, RefTableType.MATCH, 45L, lines)));
        tx.executeWithoutResult(s -> ledgerTransactionRepository.save(
                LedgerTransaction.create(TransactionType.MATCH_RELEASE, RefTableType.MATCH, 45L, lines)));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transaction", Integer.class)).isEqualTo(2);
    }

    private Long createUserCoin() {
        tx.executeWithoutResult(s -> accountRepository.insertIfAbsent(
                Account.createUserAccount(AccountType.USER_COIN, USER_ID)));
        return findUserCoin().getId();
    }

    private Account findUserCoin() {
        return accountRepository.findByAccountTypeAndOwnerUserId(AccountType.USER_COIN, USER_ID).orElseThrow();
    }

    private int countAccounts(AccountType type) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM account WHERE account_type = ?", Integer.class, type.name());
    }
}
