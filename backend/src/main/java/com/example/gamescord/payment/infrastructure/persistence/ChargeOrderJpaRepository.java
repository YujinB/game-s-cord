package com.example.gamescord.payment.infrastructure.persistence;

import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.model.ChargeOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChargeOrderJpaRepository extends JpaRepository<ChargeOrder, Long> {
    Optional<ChargeOrder> findByPgOrderId(String pgOrderId);

    @Modifying
    @Query("update ChargeOrder o set o.status = :to, o.updatedAt = CURRENT_TIMESTAMP where o.id = :id and o.status = :from")
    int changeStatus(@Param("id") Long id, @Param("from") ChargeOrderStatus from, @Param("to") ChargeOrderStatus to);

    @Modifying
    // enum 이름이 바뀌면 서버 시작 시 쿼리 검사에서 에러 (문자열 리터럴은 조용히 0행이 됨)
    @Query("""
            update ChargeOrder o
            set o.status = com.example.gamescord.payment.domain.model.ChargeOrderStatus.DONE,
                o.paymentKey = :paymentKey,
                o.updatedAt = CURRENT_TIMESTAMP
            where o.id = :id
              and o.status = com.example.gamescord.payment.domain.model.ChargeOrderStatus.IN_PROGRESS
            """)
    int markDone(@Param("id") Long id, @Param("paymentKey") String paymentKey);
}
