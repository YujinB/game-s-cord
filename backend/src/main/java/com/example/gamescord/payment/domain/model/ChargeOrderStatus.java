package com.example.gamescord.payment.domain.model;

public enum ChargeOrderStatus {
    PENDING,      // 주문 생성, 결제 대기
    IN_PROGRESS,  // 토스 승인 요청 중 (같은 주문의 다른 confirm 요청은 여기서 걸러짐)
    DONE,         // 승인 완료 + 코인 지급 완료
    FAILED,       // 승인 실패
    EXPIRED;      // 기한 안에 결제 확정 요청이 오지 않음 (승인 전이라 돈은 나가지 않음)

    // 허용 전이: PENDING → IN_PROGRESS | EXPIRED, IN_PROGRESS → DONE | FAILED. DONE·FAILED·EXPIRED는 종료 상태
    public boolean canTransitionTo(ChargeOrderStatus next) {
        return switch (this) {
            case PENDING -> next == IN_PROGRESS || next == EXPIRED;
            case IN_PROGRESS -> next == DONE || next == FAILED;
            case DONE, FAILED, EXPIRED -> false;
        };
    }
}
