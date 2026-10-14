package com.example.gamescord.payment.infrastructure.toss;

// 토스가 승인을 거절함 (4xx). 결제는 일어나지 않았으므로 주문을 FAILED로 확정해도 된다.
public class TossPaymentRejectedException extends RuntimeException {
    public TossPaymentRejectedException(String message) {
        super(message);
    }
}
