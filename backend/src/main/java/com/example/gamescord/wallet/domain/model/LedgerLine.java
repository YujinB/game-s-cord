package com.example.gamescord.wallet.domain.model;

public record LedgerLine(Account account, long amount) {
    public LedgerLine {
        if (account == null) {
            throw new IllegalArgumentException("Account must not be null");
        }

        if (amount == 0) {
            throw new IllegalArgumentException("Amount must not be 0");
        }
    }
}
