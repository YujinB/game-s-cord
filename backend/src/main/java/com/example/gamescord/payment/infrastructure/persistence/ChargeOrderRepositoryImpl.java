package com.example.gamescord.payment.infrastructure.persistence;

import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.model.ChargeOrderStatus;
import com.example.gamescord.payment.domain.repository.ChargeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChargeOrderRepositoryImpl implements ChargeOrderRepository {
    private final ChargeOrderJpaRepository chargeOrderJpaRepository;

    @Override
    public void save(ChargeOrder order) {
        chargeOrderJpaRepository.save(order);
    }

    @Override
    public Optional<ChargeOrder> findByPgOrderId(String pgOrderId) {
        return chargeOrderJpaRepository.findByPgOrderId(pgOrderId);
    }

    @Override
    public int changeStatus(Long id, ChargeOrderStatus from, ChargeOrderStatus to) {
        // 허용되지 않은 전이는 DB에 가기 전에 거절 (현재 상태 확인은 쿼리의 WHERE status = :from이 원자적으로 함)
        if (!from.canTransitionTo(to)) {
            throw new IllegalArgumentException("허용되지 않은 주문 상태 전이: " + from + " → " + to);
        }
        return chargeOrderJpaRepository.changeStatus(id, from, to, LocalDateTime.now());
    }

    @Override
    public int claim(Long id) {
        // 기한은 UPDATE를 실행하는 시각 기준으로 판정
        return chargeOrderJpaRepository.claim(id, LocalDateTime.now());
    }

    @Override
    public int markDone(Long id, String paymentKey) {
        return chargeOrderJpaRepository.markDone(id, paymentKey, LocalDateTime.now());
    }

    @Override
    public List<ChargeOrder> findStaleInProgress(LocalDateTime before) {
        return chargeOrderJpaRepository.findStaleInProgress(before);
    }

    @Override
    public int expirePending(LocalDateTime now) {
        return chargeOrderJpaRepository.expirePending(now);
    }
}
