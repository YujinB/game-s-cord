package com.example.gamescord.payment.infrastructure.toss;

/**
 * 토스 결제 조회 결과 중 대사에 필요한 값만.
 * status: READY, IN_PROGRESS, WAITING_FOR_DEPOSIT, DONE, CANCELED, PARTIAL_CANCELED, ABORTED, EXPIRED
 */
public record TossPayment(String status, String paymentKey, int totalAmount) {

    public boolean isDone() {
        return "DONE".equals(status);
    }

    // 돈이 빠져나가지 않았거나 되돌려진 상태 → 우리 주문을 FAILED로 확정해도 됨
    public boolean isNotPaid() {
        return "ABORTED".equals(status) || "EXPIRED".equals(status) || "CANCELED".equals(status);
    }
}
