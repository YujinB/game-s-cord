package com.example.gamescord.payment.infrastructure.persistence;

import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.model.ChargeOrderStatus;
import com.example.gamescord.payment.domain.repository.ChargeOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
        return chargeOrderJpaRepository.changeStatus(id, from, to);
    }

    @Override
    public int markDone(Long id, String paymentKey) {
        return chargeOrderJpaRepository.markDone(id, paymentKey);
    }
}
