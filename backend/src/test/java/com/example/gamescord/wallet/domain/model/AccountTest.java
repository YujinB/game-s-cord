package com.example.gamescord.wallet.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"USER_COIN", "USER_EARNING"})
    @DisplayName("유저 계좌는 주인이 지정되고 잔액 0에서 시작한다")
    void createUserAccount(AccountType type) {
        Account account = Account.createUserAccount(type, 7L);

        assertThat(account.getAccountType()).isEqualTo(type);
        assertThat(account.getOwnerUserId()).isEqualTo(7L);
        assertThat(account.getBalance()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"COIN_ISSUANCE", "ESCROW", "PLATFORM_FEE"})
    @DisplayName("시스템 타입으로는 유저 계좌를 만들 수 없다")
    void rejectSystemTypeAsUserAccount(AccountType type) {
        assertThatThrownBy(() -> Account.createUserAccount(type, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("유저 계좌의 주인 id는 시스템 계좌용 값(0)이나 음수일 수 없다")
    void rejectInvalidUserId(long userId) {
        assertThatThrownBy(() -> Account.createUserAccount(AccountType.USER_COIN, userId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"USER_COIN"})
    @DisplayName("유저 계좌의 주인 id가 없으면 만들 수 없다")
    void rejectNullUserId(AccountType type) {
        assertThatThrownBy(() -> Account.createUserAccount(type, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"COIN_ISSUANCE", "ESCROW", "PLATFORM_FEE"})
    @DisplayName("시스템 계좌는 주인이 0이고 잔액을 관리하지 않는다")
    void createSystemAccount(AccountType type) {
        Account account = Account.createSystemAccount(type);

        assertThat(account.getAccountType()).isEqualTo(type);
        assertThat(account.getOwnerUserId()).isEqualTo(Account.SYSTEM_OWNER_ID);
        assertThat(account.getBalance()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AccountType.class, names = {"USER_COIN", "USER_EARNING"})
    @DisplayName("유저 타입으로는 시스템 계좌를 만들 수 없다")
    void rejectUserTypeAsSystemAccount(AccountType type) {
        assertThatThrownBy(() -> Account.createSystemAccount(type))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
