package com.example.gamescord.wallet.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransactionTest {

    private final Account from = Account.createSystemAccount(AccountType.ESCROW);
    private final Account to = Account.createUserAccount(AccountType.USER_EARNING, 1L);
    private final Account fee = Account.createSystemAccount(AccountType.PLATFORM_FEE);

    @Test
    @DisplayName("줄이 null이면 거래를 만들 수 없다")
    void rejectNullLines() {
        assertThatThrownBy(() -> create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("줄이 0개면 거래를 만들 수 없다")
    void rejectEmptyLines() {
        assertThatThrownBy(() -> create(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("줄이 1개면 합이 맞을 수 없으므로 거래를 만들 수 없다")
    void rejectSingleLine() {
        assertThatThrownBy(() -> create(List.of(new LedgerLine(to, 500))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("줄의 합이 0이 아니면 거래를 만들 수 없다")
    void rejectNonZeroSum() {
        List<LedgerLine> lines = List.of(
                new LedgerLine(from, -500),
                new LedgerLine(to, 499));

        assertThatThrownBy(() -> create(lines))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("금액이 0인 줄은 만들 수 없다")
    void rejectZeroAmountLine() {
        assertThatThrownBy(() -> new LedgerLine(to, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("계좌가 없는 줄은 만들 수 없다")
    void rejectNullAccountLine() {
        assertThatThrownBy(() -> new LedgerLine(null, 500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("합이 0인 2줄이면 거래가 만들어지고 근거 정보가 그대로 담긴다")
    void createWithTwoLines() {
        List<LedgerLine> lines = List.of(
                new LedgerLine(from, -500),
                new LedgerLine(to, 500));

        LedgerTransaction transaction = LedgerTransaction.create(
                TransactionType.CHARGE, RefTableType.CHARGE_ORDER, 123L, lines);

        assertThat(transaction.getId()).isNull();
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.CHARGE);
        assertThat(transaction.getRefTableType()).isEqualTo(RefTableType.CHARGE_ORDER);
        assertThat(transaction.getRefId()).isEqualTo(123L);
    }

    @Test
    @DisplayName("합이 0인 3줄(매칭 정산 모양)이면 거래가 만들어진다")
    void createWithThreeLines() {
        List<LedgerLine> lines = List.of(
                new LedgerLine(from, -1000),
                new LedgerLine(to, 900),
                new LedgerLine(fee, 100));

        LedgerTransaction transaction = LedgerTransaction.create(
                TransactionType.MATCH_RELEASE, RefTableType.MATCH, 45L, lines);

        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.MATCH_RELEASE);
        assertThat(transaction.getRefId()).isEqualTo(45L);
    }

    private LedgerTransaction create(List<LedgerLine> lines) {
        return LedgerTransaction.create(TransactionType.CHARGE, RefTableType.CHARGE_ORDER, 1L, lines);
    }
}
