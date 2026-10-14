package com.example.gamescord.wallet.infrastructure.persistence;

import com.example.gamescord.wallet.domain.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntry, Long> {
}
