package com.example.gamescord.wallet.domain.repository;

import com.example.gamescord.wallet.domain.model.Account;
import com.example.gamescord.wallet.domain.model.AccountType;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findByAccountTypeAndOwnerUserId(AccountType accountType, Long ownerUserId);
    Optional<Account> findByAccountTypeAndOwnerUserIdWithLock(AccountType accountType, Long ownerUserId);
    void insertIfAbsent(Account account);
    int addBalance(Long accountId, long delta);
    Long findBalanceById(Long accountId);

}
