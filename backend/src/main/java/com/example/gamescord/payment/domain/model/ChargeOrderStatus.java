package com.example.gamescord.payment.domain.model;

public enum ChargeOrderStatus {
    PENDING,      // 주문 생성, 결제 대기
    IN_PROGRESS,  // 토스 승인 요청 중 (같은 주문의 다른 confirm 요청은 여기서 걸러짐)
    DONE,         // 승인 완료 + 코인 지급 완료
    FAILED;       // 승인 실패

    // 허용 전이: PENDING → IN_PROGRESS → DONE | FAILED. DONE·FAILED는 종료 상태
    public boolean canTransitionTo(ChargeOrderStatus next) {
        return switch (this) {
            case PENDING -> next == IN_PROGRESS;
            case IN_PROGRESS -> next == DONE || next == FAILED;
            case DONE, FAILED -> false;
        };
    }
}
