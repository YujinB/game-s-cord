package com.example.gamescord.wallet.infrastructure.persistence;

import com.example.gamescord.wallet.domain.model.LedgerEntry;
import com.example.gamescord.wallet.domain.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class LedgerEntryRepositoryImpl implements LedgerEntryRepository {
    private final LedgerEntryJpaRepository ledgerEntryJpaRepository;

    @Override
    public void saveAll(List<LedgerEntry> ledgerEntries) {
        ledgerEntryJpaRepository.saveAll(ledgerEntries);
    }
}
