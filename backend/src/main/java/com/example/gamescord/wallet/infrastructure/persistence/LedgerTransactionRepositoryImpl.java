package com.example.gamescord.wallet.infrastructure.persistence;

import com.example.gamescord.wallet.domain.model.LedgerTransaction;
import com.example.gamescord.wallet.domain.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LedgerTransactionRepositoryImpl implements LedgerTransactionRepository {
    private final LedgerTransactionJpaRepository ledgerTransactionJpaRepository;

    @Override
    public void save(LedgerTransaction ledgerTransaction) {
        ledgerTransactionJpaRepository.save(ledgerTransaction);
    }
}
