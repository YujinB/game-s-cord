package com.example.gamescord.payment.infrastructure.persistence;

import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.model.ChargeOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChargeOrderJpaRepository extends JpaRepository<ChargeOrder, Long> {
    Optional<ChargeOrder> findByPgOrderId(String pgOrderId);

    // updatedAt은 DB 시계(CURRENT_TIMESTAMP, UTC) 대신 앱 시계로 넣는다.
    // created_at·expires_at도 앱 시계라 섞이면 대사의 "5분 지난 주문" 판단이 시간대 차이만큼 틀어진다.
    @Modifying
    @Query("update ChargeOrder o set o.status = :to, o.updatedAt = :now where o.id = :id and o.status = :from")
    int changeStatus(@Param("id") Long id, @Param("from") ChargeOrderStatus from, @Param("to") ChargeOrderStatus to,
                     @Param("now") LocalDateTime now);

    @Modifying
    // enum 이름이 바뀌면 서버 시작 시 쿼리 검사에서 에러 (문자열 리터럴은 조용히 0행이 됨)
    @Query("""
            update ChargeOrder o
            set o.status = com.example.gamescord.payment.domain.model.ChargeOrderStatus.DONE,
                o.paymentKey = :paymentKey,
                o.updatedAt = :now
            where o.id = :id
              and o.status = com.example.gamescord.payment.domain.model.ChargeOrderStatus.IN_PROGRESS
            """)
    int markDone(@Param("id") Long id, @Param("paymentKey") String paymentKey, @Param("now") LocalDateTime now);

    @Query("select o from ChargeOrder o where o.status = com.example.gamescord.payment.domain.model.ChargeOrderStatus.IN_PROGRESS and o.updatedAt < :before")
    List<ChargeOrder> findStaleInProgress(@Param("before") LocalDateTime before);
}
