package com.example.gamescord.payment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeOrderStatusTest {

    @ParameterizedTest(name = "{0} → {1} = {2}")
    @CsvSource({
            "PENDING, IN_PROGRESS, true",
            "IN_PROGRESS, DONE, true",
            "IN_PROGRESS, FAILED, true",
            "PENDING, DONE, false",
            "PENDING, FAILED, false",
            "PENDING, PENDING, false",
            "IN_PROGRESS, PENDING, false",
            "IN_PROGRESS, IN_PROGRESS, false",
            "DONE, PENDING, false",
            "DONE, IN_PROGRESS, false",
            "DONE, FAILED, false",
            "FAILED, PENDING, false",
            "FAILED, IN_PROGRESS, false",
            "FAILED, DONE, false",
            "PENDING, EXPIRED, true",
            "IN_PROGRESS, EXPIRED, false",
            "DONE, EXPIRED, false",
            "FAILED, EXPIRED, false",
            "EXPIRED, PENDING, false",
            "EXPIRED, IN_PROGRESS, false",
            "EXPIRED, DONE, false",
            "EXPIRED, FAILED, false"
    })
    @DisplayName("허용된 전이만 true (DONE·FAILED·EXPIRED는 종료 상태)")
    void canTransitionTo(ChargeOrderStatus from, ChargeOrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
