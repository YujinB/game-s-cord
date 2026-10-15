package com.example.gamescord.payment.domain.repository;

import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.model.ChargeOrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChargeOrderRepository {
    void save(ChargeOrder order);
    Optional<ChargeOrder> findByPgOrderId(String pgOrderId);
    int changeStatus(Long id, ChargeOrderStatus from, ChargeOrderStatus to);
    int claim(Long id);
    int markDone(Long id, String paymentKey);
    List<ChargeOrder> findStaleInProgress(LocalDateTime before);
    int expirePending(LocalDateTime now);
}
