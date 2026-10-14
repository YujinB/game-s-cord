package com.example.gamescord.wallet.domain.model;

public enum AccountType {
    USER_COIN(true),        // 유저 보유 코인
    COIN_ISSUANCE(false),   // 코인 발행 (충전 시 출금 측)
    ESCROW(false),          // 매칭 대금 보관
    USER_EARNING(true),     // 유저 정산 수익
    PLATFORM_FEE(false);    // 플랫폼 수수료

    private final boolean userOwned;

    AccountType(boolean userOwned) {
        this.userOwned = userOwned;
    }

    public boolean isUserOwned() {
        return userOwned;
    }
}
