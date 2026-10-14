package com.example.gamescord.wallet.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerEntryTest {

    private final Account issuance = Account.createSystemAccount(AccountType.COIN_ISSUANCE);
    private final Account user = Account.createUserAccount(AccountType.USER_COIN, 1L);

    private final LedgerLine issuanceLine = new LedgerLine(issuance, -500);
    private final LedgerLine userLine = new LedgerLine(user, 500);

    private final LedgerTransaction transaction = LedgerTransaction.create(
            TransactionType.CHARGE, RefTableType.CHARGE_ORDER, 1L, List.of(issuanceLine, userLine));

    @Test
    @DisplayName("유저 계좌 entry는 줄의 계좌·금액과 넘긴 반영 후 잔액을 그대로 담는다")
    void createUserEntry() {
        LedgerEntry entry = LedgerEntry.create(transaction, userLine, 1500L);

        assertThat(entry.getId()).isNull();
        assertThat(entry.getTransaction()).isSameAs(transaction);
        assertThat(entry.getAccount()).isSameAs(user);
        assertThat(entry.getAmount()).isEqualTo(500L);
        assertThat(entry.getBalanceAfter()).isEqualTo(1500L);
    }

    @Test
    @DisplayName("시스템 계좌 entry는 반영 후 잔액 없이 만들어진다")
    void createSystemEntry() {
        LedgerEntry entry = LedgerEntry.create(transaction, issuanceLine, null);

        assertThat(entry.getAccount()).isSameAs(issuance);
        assertThat(entry.getAmount()).isEqualTo(-500L);
        assertThat(entry.getBalanceAfter()).isNull();
    }

    @Test
    @DisplayName("거래 헤더 없이는 entry를 만들 수 없다")
    void rejectNullTransaction() {
        assertThatThrownBy(() -> LedgerEntry.create(null, userLine, 1500L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
