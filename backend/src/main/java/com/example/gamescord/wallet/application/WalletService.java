package com.example.gamescord.wallet.application;

import com.example.gamescord.wallet.domain.model.*;
import com.example.gamescord.wallet.domain.repository.AccountRepository;
import com.example.gamescord.wallet.domain.repository.LedgerEntryRepository;
import com.example.gamescord.wallet.domain.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    // 코인 충전 메서드
    @Transactional
    public void creditCharge(Long chargeOrderId, Long userId, long coinAmount) {
        if (coinAmount <= 0) throw new IllegalArgumentException("coinAmount must be positive");

        // 계좌 생성 (시스템 코인 발행 계좌, 유저 게좌)
        Account issuance = accountRepository.findByAccountTypeAndOwnerUserId(AccountType.COIN_ISSUANCE, Account.SYSTEM_OWNER_ID).orElseThrow();

        accountRepository.insertIfAbsent(Account.createUserAccount(AccountType.USER_COIN, userId));
        // SELECT FOR UPDATE
        Account userCoinAccount = accountRepository.findByAccountTypeAndOwnerUserIdWithLock(AccountType.USER_COIN, userId).orElseThrow(() -> new NoSuchElementException("Account doesn't exist"));

        LedgerLine issuanceLine = new LedgerLine(issuance, -coinAmount);
        LedgerLine userCoinLine = new LedgerLine(userCoinAccount, +coinAmount);

        // 거래 원장 생성
        LedgerTransaction tx = LedgerTransaction.create(TransactionType.CHARGE, RefTableType.CHARGE_ORDER, chargeOrderId, List.of(issuanceLine, userCoinLine));
        ledgerTransactionRepository.save(tx);// UNIQUE(type, ref_type, ref_id) 중복이면 여기서 예외 (IDENTITY라 save 즉시 INSERT)

        // 유저 계좌 잔액 반영 (시스템 계좌는 잔액 캐시 없음, entry로만 기록)
        if (accountRepository.addBalance(userCoinAccount.getId(), coinAmount)<1){
            // 조건에 안 맞아서 갱신이 안 된 경우
            throw new IllegalStateException("accountRepository.addBalance failed");
        }

        // 원장 기록 반영
        Long balanceAfter = accountRepository.findBalanceById(userCoinAccount.getId());

        ledgerEntryRepository.saveAll(
                List.of(
                        LedgerEntry.create(tx, issuanceLine, null),
                        LedgerEntry.create(tx, userCoinLine, balanceAfter)

                )
        );
    }
}