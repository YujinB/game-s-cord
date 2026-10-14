package com.example.gamescord.payment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ChargeOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 9, 0);

    private ChargeOrder order() {
        return ChargeOrder.create(7L, 1, 4680, 500, "GAMESCORD-ABC", NOW);
    }

    @Test
    @DisplayName("주문은 PENDING으로 시작하고 서버가 정한 금액·코인을 담는다")
    void create() {
        ChargeOrder order = order();

        assertThat(order.getId()).isNull();
        assertThat(order.getStatus()).isEqualTo(ChargeOrderStatus.PENDING);
        assertThat(order.getUserId()).isEqualTo(7L);
        assertThat(order.getPackageId()).isEqualTo(1);
        assertThat(order.getAmount()).isEqualTo(4680);
        assertThat(order.getCoinAmount()).isEqualTo(500);
        assertThat(order.getPgOrderId()).isEqualTo("GAMESCORD-ABC");
        assertThat(order.getPaymentKey()).isNull();
        assertThat(order.getExpiresAt()).isEqualTo(NOW.plus(ChargeOrder.EXPIRES_IN));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("금액이나 코인이 0 이하면 주문을 만들 수 없다")
    void rejectNonPositive(int value) {
        assertThatThrownBy(() -> ChargeOrder.create(7L, 1, value, 500, "GAMESCORD-ABC", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeOrder.create(7L, 1, 4680, value, "GAMESCORD-ABC", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("필수 값이 없으면 주문을 만들 수 없다")
    void rejectMissingFields() {
        assertThatThrownBy(() -> ChargeOrder.create(null, 1, 4680, 500, "GAMESCORD-ABC", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeOrder.create(7L, null, 4680, 500, "GAMESCORD-ABC", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeOrder.create(7L, 1, null, 500, "GAMESCORD-ABC", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeOrder.create(7L, 1, 4680, 500, " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeOrder.create(7L, 1, 4680, 500, "GAMESCORD-ABC", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("소유자와 금액이 주문과 같으면 통과한다")
    void validateOk() {
        assertThatNoException().isThrownBy(() -> order().validateOwnerAndAmount(7L, 4680));
    }

    @Test
    @DisplayName("다른 유저의 주문이면 거절한다")
    void rejectOtherOwner() {
        assertThatThrownBy(() -> order().validateOwnerAndAmount(8L, 4680))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("클라이언트가 보낸 금액이 주문 금액과 다르면 거절한다 (금액 위변조)")
    void rejectTamperedAmount() {
        assertThatThrownBy(() -> order().validateOwnerAndAmount(7L, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기한 안이면 통과하고, 기한 시각 정각까지도 통과한다")
    void notExpired() {
        ChargeOrder order = order();

        assertThatNoException().isThrownBy(() -> order.validateNotExpired(NOW.plusMinutes(10)));
        assertThatNoException().isThrownBy(() -> order.validateNotExpired(order.getExpiresAt()));
    }

    @Test
    @DisplayName("기한이 지나면 confirm을 거절한다")
    void rejectExpired() {
        ChargeOrder order = order();

        assertThatThrownBy(() -> order.validateNotExpired(order.getExpiresAt().plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
