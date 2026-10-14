package com.example.gamescord.payment.domain.model;

import lombok.Getter;

import java.util.Arrays;

// 판매 중인 코인 패키지. 결제 금액의 기준은 이 값 (프론트 목록은 표시용)
@Getter
public enum CoinPackage {
    P1(1, 500, 4_680),
    P2(2, 1_000, 9_360),
    P3(3, 2_000, 18_720),
    P4(4, 5_000, 45_600),
    P5(5, 10_000, 91_200),
    P6(6, 30_000, 266_400),
    P7(7, 50_000, 444_000),
    P8(8, 100_000, 864_000),
    P9(9, 300_000, 2_592_000),
    P10(10, 500_000, 4_320_000);

    private final int id;
    private final int coinAmount;
    private final int price; // 원

    CoinPackage(int id, int coinAmount, int price) {
        this.id = id;
        this.coinAmount = coinAmount;
        this.price = price;
    }

    public static CoinPackage of(Integer id) {
        return Arrays.stream(values())
                .filter(p -> id != null && p.id == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 패키지입니다."));
    }
}
