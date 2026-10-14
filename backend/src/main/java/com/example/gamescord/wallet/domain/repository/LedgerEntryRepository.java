package com.example.gamescord.wallet.domain.repository;

import com.example.gamescord.wallet.domain.model.LedgerEntry;

import java.util.List;

public interface LedgerEntryRepository {
    void saveAll(List<LedgerEntry> ledgerEntries);
}
