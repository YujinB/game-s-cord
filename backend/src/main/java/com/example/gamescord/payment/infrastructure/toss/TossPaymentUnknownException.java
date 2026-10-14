package com.example.gamescord.payment.infrastructure.toss;

// 토스 승인 결과를 모름 (5xx, 타임아웃, 연결 끊김). 토스에서는 승인됐을 수 있으므로 FAILED로 확정하면 안 된다.
public class TossPaymentUnknownException extends RuntimeException {
    public TossPaymentUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
