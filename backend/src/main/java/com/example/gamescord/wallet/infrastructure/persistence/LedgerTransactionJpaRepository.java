package com.example.gamescord.wallet.infrastructure.persistence;

import com.example.gamescord.wallet.domain.model.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionJpaRepository extends JpaRepository<LedgerTransaction, Long> {
}
