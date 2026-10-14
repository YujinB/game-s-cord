package com.example.gamescord.payment.application;

import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.model.ChargeOrderStatus;
import com.example.gamescord.payment.domain.repository.ChargeOrderRepository;
import com.example.gamescord.payment.infrastructure.toss.TossPayment;
import com.example.gamescord.payment.infrastructure.toss.TossPaymentsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결과 불명 주문 대사.
 * confirm에서 토스 응답이 5xx·타임아웃이면 주문이 IN_PROGRESS로 남는다 (토스에서는 승인됐을 수 있음).
 * 오래된 IN_PROGRESS 주문을 토스에 조회해 우리 상태를 맞춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeReconciler {

    // confirm이 아직 토스 응답을 기다리는 중일 수 있으므로, 이 시간이 지난 주문만 대사
    static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final ChargeOrderRepository chargeOrderRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final ChargeCompleter chargeCompleter;
    private final TransactionTemplate transactionTemplate;

    // now를 밖에서 받아야 테스트에서 시각을 직접 정할 수 있음
    public void reconcile(LocalDateTime now) {
        List<ChargeOrder> staleOrders = chargeOrderRepository.findStaleInProgress(now.minus(STALE_AFTER));

        for (ChargeOrder order : staleOrders) {
            try {
                reconcileOne(order);
            } catch (Exception e) {
                // 한 주문이 실패해도 나머지는 계속. 이 주문은 IN_PROGRESS로 남아 다음 회차에 다시 시도
                log.error("대사 실패 | orderId={}", order.getPgOrderId(), e);
            }
        }
    }

    private void reconcileOne(ChargeOrder order) {
        Optional<TossPayment> found = tossPaymentsClient.findByOrderId(order.getPgOrderId());

        // 1) 토스에 결제가 없거나 중단·만료·취소 → 돈이 나가지 않았으므로 FAILED
        //    (같은 클래스 안 호출이라 @Transactional 대신 TransactionTemplate)
        if (found.isEmpty() || found.get().isNotPaid()) {
            transactionTemplate.execute(s -> chargeOrderRepository.changeStatus(
                    order.getId(), ChargeOrderStatus.IN_PROGRESS, ChargeOrderStatus.FAILED));
            log.info("대사: 결제 없음 → FAILED | orderId={}", order.getPgOrderId());
            return;
        }

        TossPayment payment = found.get();

        // 2) 토스는 DONE이고 금액도 맞음 → confirm과 같은 확정·지급 (다른 빈이라 @Transactional 적용)
        if (payment.isDone() && payment.totalAmount() == order.getAmount()) {
            chargeCompleter.complete(order, payment.paymentKey());
            log.info("대사: 토스 승인 확인 → DONE | orderId={}", order.getPgOrderId());
            return;
        }

        // 3) 토스는 DONE인데 금액이 다름 → 자동 처리하면 안 됨, 사람이 확인
        if (payment.isDone()) {
            log.error("대사: 금액 불일치 | orderId={} | 주문={} | 토스={}",
                    order.getPgOrderId(), order.getAmount(), payment.totalAmount());
            return;
        }

        // 4) 그 외 (토스에서도 아직 진행 중 등) → 아무것도 안 함, 다음 회차에 다시
        log.info("대사: 토스 상태 {} → 보류 | orderId={}", payment.status(), order.getPgOrderId());
    }
}
