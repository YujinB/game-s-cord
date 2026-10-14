package com.example.gamescord.wallet.application;

import com.example.gamescord.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 충전 지급(creditCharge)을 실제 MySQL에서 확인한다.
 * 시스템 계좌는 서버 시작 시 SystemAccountInitializer가 만든 것을 그대로 쓴다.
 */
class WalletServiceTest extends IntegrationTestSupport {

    private static final long USER_ID = 7L;

    @Autowired WalletService walletService;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM ledger_entry");
        jdbc.update("DELETE FROM ledger_transaction");
        jdbc.update("DELETE FROM account WHERE owner_user_id <> 0");
    }

    @Test
    @DisplayName("서버가 뜨면 시스템 계좌 3개가 owner 0, 잔액 없음으로 만들어져 있다")
    void systemAccountsInitialized() {
        List<String> types = jdbc.queryForList(
                "SELECT account_type FROM account WHERE owner_user_id = 0 AND balance IS NULL ORDER BY account_type",
                String.class);

        assertThat(types).containsExactly("COIN_ISSUANCE", "ESCROW", "PLATFORM_FEE");
    }

    @Test
    @DisplayName("충전하면 유저 잔액이 늘고, entry 2줄의 합이 0이며 유저 줄에만 반영 후 잔액이 남는다")
    void creditOnce() {
        walletService.creditCharge(1L, USER_ID, 500);

        assertThat(balance(USER_ID)).isEqualTo(500L);
        assertThat(transactionCount()).isEqualTo(1);

        List<Map<String, Object>> entries = entries();
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().mapToLong(e -> ((Number) e.get("amount")).longValue()).sum()).isZero();

        Map<String, Object> issuanceEntry = entryOf(entries, "COIN_ISSUANCE");
        Map<String, Object> userEntry = entryOf(entries, "USER_COIN");
        assertThat(((Number) issuanceEntry.get("amount")).longValue()).isEqualTo(-500L);
        assertThat(issuanceEntry.get("balance_after")).isNull();
        assertThat(((Number) userEntry.get("amount")).longValue()).isEqualTo(500L);
        assertThat(((Number) userEntry.get("balance_after")).longValue()).isEqualTo(500L);
    }

    @Test
    @DisplayName("다른 주문으로 두 번 충전하면 잔액이 누적되고 두 번째 반영 후 잔액은 누적값이다")
    void creditTwoOrders() {
        walletService.creditCharge(1L, USER_ID, 500);
        walletService.creditCharge(2L, USER_ID, 300);

        assertThat(balance(USER_ID)).isEqualTo(800L);
        assertThat(transactionCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT e.balance_after FROM ledger_entry e
                JOIN ledger_transaction t ON t.id = e.transaction_id
                JOIN account a ON a.id = e.account_id
                WHERE t.ref_id = 2 AND a.account_type = 'USER_COIN'
                """, Long.class)).isEqualTo(800L);
    }

    @Test
    @DisplayName("같은 주문으로 다시 충전하면 거절되고 잔액·원장은 그대로다 (멱등성 ③층)")
    void rejectSameOrderTwice() {
        walletService.creditCharge(1L, USER_ID, 500);

        assertThatThrownBy(() -> walletService.creditCharge(1L, USER_ID, 500))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(balance(USER_ID)).isEqualTo(500L);
        assertThat(transactionCount()).isEqualTo(1);
        assertThat(entries()).hasSize(2);
    }

    @Test
    @DisplayName("금액이 0 이하면 거절되고 DB에 아무것도 남지 않는다")
    void rejectNonPositiveAmount() {
        assertThatThrownBy(() -> walletService.creditCharge(1L, USER_ID, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> walletService.creditCharge(1L, USER_ID, -500))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(transactionCount()).isZero();
        assertThat(userAccountCount(USER_ID)).isZero();
    }

    @Test
    @DisplayName("같은 주문이 동시에 N번 들어와도 지급은 1번이다")
    void concurrentSameOrder() throws Exception {
        walletService.creditCharge(99L, USER_ID, 1); // 계좌를 미리 만들어 같은 주문 경합만 본다
        int threads = 10;

        Map<String, Integer> outcomes = runConcurrently(threads, i -> () -> {
            walletService.creditCharge(1L, USER_ID, 500);
            return null;
        });

        assertThat(outcomes).containsEntry("success", 1);
        assertThat(balance(USER_ID)).isEqualTo(501L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction WHERE ref_id = 1", Integer.class)).isEqualTo(1);
        System.out.println("[concurrentSameOrder] " + outcomes);
    }

    @Test
    @DisplayName("한 유저의 서로 다른 주문 N개가 동시에 들어오면 모두 지급되고 잔액은 합계와 같다")
    void concurrentDifferentOrders() throws Exception {
        walletService.creditCharge(99L, USER_ID, 1);
        int threads = 10;

        Map<String, Integer> outcomes = runConcurrently(threads, i -> () -> {
            walletService.creditCharge(100L + i, USER_ID, 500);
            return null;
        });

        assertThat(outcomes).containsEntry("success", threads);
        assertThat(balance(USER_ID)).isEqualTo(1 + 500L * threads);
        assertThat(sumOfUserEntries(USER_ID)).isEqualTo(balance(USER_ID));
    }

    @Test
    @DisplayName("처음 충전하는 유저의 주문 N개가 동시에 들어와도 유저 계좌는 1개다")
    void concurrentFirstCharge() throws Exception {
        int threads = 10;

        Map<String, Integer> outcomes = runConcurrently(threads, i -> () -> {
            walletService.creditCharge(200L + i, USER_ID, 500);
            return null;
        });
        System.out.println("[concurrentFirstCharge] " + outcomes);

        assertThat(userAccountCount(USER_ID)).isEqualTo(1);
        int succeeded = outcomes.getOrDefault("success", 0);
        assertThat(balance(USER_ID)).isEqualTo(500L * succeeded);
        assertThat(sumOfUserEntries(USER_ID)).isEqualTo(balance(USER_ID));
        assertThat(succeeded).isEqualTo(threads);
    }

    // ── helpers ──

    interface TaskFactory {
        Callable<Void> create(int index);
    }

    /** 동시에 출발시키고 결과를 "success" 또는 예외 클래스 이름별로 센다. */
    private Map<String, Integer> runConcurrently(int threads, TaskFactory factory) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Void> task = factory.create(i);
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();

        Map<String, Integer> outcomes = new TreeMap<>();
        for (Future<Void> future : futures) {
            String key;
            try {
                future.get();
                key = "success";
            } catch (ExecutionException e) {
                key = e.getCause().getClass().getSimpleName();
            }
            outcomes.merge(key, 1, Integer::sum);
        }
        pool.shutdown();
        return outcomes;
    }

    private Long balance(long userId) {
        return jdbc.queryForObject(
                "SELECT balance FROM account WHERE account_type = 'USER_COIN' AND owner_user_id = ?",
                Long.class, userId);
    }

    private int userAccountCount(long userId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM account WHERE account_type = 'USER_COIN' AND owner_user_id = ?",
                Integer.class, userId);
    }

    private int transactionCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transaction", Integer.class);
    }

    private long sumOfUserEntries(long userId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(e.amount), 0) FROM ledger_entry e
                JOIN account a ON a.id = e.account_id
                WHERE a.account_type = 'USER_COIN' AND a.owner_user_id = ?
                """, Long.class, userId);
    }

    private List<Map<String, Object>> entries() {
        return jdbc.queryForList("""
                SELECT a.account_type, e.amount, e.balance_after FROM ledger_entry e
                JOIN account a ON a.id = e.account_id
                """);
    }

    private Map<String, Object> entryOf(List<Map<String, Object>> entries, String accountType) {
        return entries.stream()
                .filter(e -> accountType.equals(e.get("account_type")))
                .findFirst()
                .orElseThrow();
    }
}
