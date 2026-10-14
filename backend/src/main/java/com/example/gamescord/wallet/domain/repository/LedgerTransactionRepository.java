package com.example.gamescord.wallet.domain.repository;

import com.example.gamescord.wallet.domain.model.LedgerTransaction;

public interface LedgerTransactionRepository {
    void save(LedgerTransaction ledgerTransaction);
}
