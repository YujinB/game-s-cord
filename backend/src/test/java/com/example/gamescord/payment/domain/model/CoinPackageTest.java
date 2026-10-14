package com.example.gamescord.payment.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoinPackageTest {

    @Test
    @DisplayName("패키지 id로 코인과 금액을 찾는다 (기존 표와 같은 값)")
    void findById() {
        assertThat(CoinPackage.of(1).getCoinAmount()).isEqualTo(500);
        assertThat(CoinPackage.of(1).getPrice()).isEqualTo(4_680);
        assertThat(CoinPackage.of(10).getCoinAmount()).isEqualTo(500_000);
        assertThat(CoinPackage.of(10).getPrice()).isEqualTo(4_320_000);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11, -1})
    @DisplayName("없는 패키지 id면 거절한다")
    void rejectUnknownId(int id) {
        assertThatThrownBy(() -> CoinPackage.of(id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("id가 null이면 거절한다")
    void rejectNullId() {
        assertThatThrownBy(() -> CoinPackage.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("패키지 id는 서로 겹치지 않는다")
    void idsAreUnique() {
        assertThat(Arrays.stream(CoinPackage.values()).map(CoinPackage::getId).distinct().count())
                .isEqualTo(CoinPackage.values().length);
    }
}
