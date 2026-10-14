package com.example.gamescord.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 결과 불명 주문 대사를 주기적으로 실행한다.
 * 대사 로직(ChargeReconciler)과 나눈 이유: 테스트에서는 스케줄러를 끄고 reconcile(now)를 직접 호출해 결과를 고정하기 위함.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class ChargeReconcileScheduler {

    private final ChargeReconciler chargeReconciler;

    // fixedDelay: 이전 실행이 끝난 뒤 1분. 토스 조회가 오래 걸려도 다음 회차가 겹쳐 시작되지 않음
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void run() {
        chargeReconciler.reconcile(LocalDateTime.now());
    }
}
