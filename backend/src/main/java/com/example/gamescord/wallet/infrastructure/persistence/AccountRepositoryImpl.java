package com.example.gamescord.wallet.infrastructure.persistence;

import com.example.gamescord.wallet.domain.model.Account;
import com.example.gamescord.wallet.domain.model.AccountType;
import com.example.gamescord.wallet.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {
    private final AccountJpaRepository accountJpaRepository;


    @Override
    public Optional<Account> findByAccountTypeAndOwnerUserId(AccountType accountType, Long ownerUserId) {
        return accountJpaRepository.findByAccountTypeAndOwnerUserId(accountType, ownerUserId);
    }

    @Override
    public Optional<Account> findByAccountTypeAndOwnerUserIdWithLock(AccountType accountType, Long ownerUserId) {
        return accountJpaRepository.findByAccountTypeAndOwnerUserIdWithLock(accountType, ownerUserId);
    }

    @Override
    public void insertIfAbsent(Account account) {
        accountJpaRepository.insertIfAbsent(account.getAccountType().name(), account.getOwnerUserId(), account.getBalance());
    }

    @Override
    public int addBalance(Long accountId, long delta) {
        return accountJpaRepository.addBalance(accountId, delta);
    }

    @Override
    public Long findBalanceById(Long accountId) {
        return accountJpaRepository.findBalanceById(accountId);
    }
}
