package com.example.gamescord.payment.domain.model;

import com.example.gamescord.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "charge_order",
        // 토스에 보내는 주문번호이자 Idempotency-Key이므로 중복 불가
        uniqueConstraints = @UniqueConstraint(columnNames = "pg_order_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChargeOrder extends BaseEntity {

    // 토스 결제창에서 결제를 마치고 돌아오는 시간을 넉넉히 덮는 기한
    public static final Duration EXPIRES_IN = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 원장 ref_id로 사용

    @Column(name = "pg_order_id", nullable = false, length = 64)
    private String pgOrderId;

    @Column(name = "user_id", nullable = false)
    private Long userId; // users_id (다른 경계는 ID로만 참조)

    @Column(name = "package_id", nullable = false)
    private Integer packageId;

    @Column(nullable = false)
    private Integer amount; // 결제 금액(원), 주문 시점에 서버가 패키지로 정함

    @Column(name = "coin_amount", nullable = false)
    private Integer coinAmount; // 지급할 코인, 주문 시점에 서버가 패키지로 정함

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeOrderStatus status;

    @Column(name = "payment_key")
    private String paymentKey; // 승인 후 토스가 준 값 (조회·취소용)

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // 이 시각이 지나면 confirm 거절

    // 상태 변경은 조건부 UPDATE(@Modifying)로 하므로 쿼리에서 직접 갱신해야 함
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // now를 밖에서 받아야 테스트에서 시각을 직접 정할 수 있음
    public static ChargeOrder create(Long userId, Integer packageId, Integer amount, Integer coinAmount,
                                     String pgOrderId, LocalDateTime now) {
        if (userId == null || packageId == null || pgOrderId == null || pgOrderId.isBlank() || now == null) {
            throw new IllegalArgumentException("order fields must not be null");
        }
        if (amount == null || amount <= 0 || coinAmount == null || coinAmount <= 0) {
            throw new IllegalArgumentException("amount and coinAmount must be positive");
        }
        return new ChargeOrder(null, pgOrderId, userId, packageId, amount, coinAmount,
                ChargeOrderStatus.PENDING, null, now.plus(EXPIRES_IN), null);
    }

    // 클라이언트가 보낸 값이 주문 시점에 서버가 정한 값과 같은지 확인
    public void validateOwnerAndAmount(Long requestUserId, Integer requestAmount) {
        if (!userId.equals(requestUserId)) {
            throw new IllegalArgumentException("주문 소유자가 일치하지 않습니다.");
        }
        if (!amount.equals(requestAmount)) {
            throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }
    }

    // 기한이 지난 주문은 토스에 승인 요청을 보내기 전에 거절 (승인 전이라 돈은 빠져나가지 않음)
    public void validateNotExpired(LocalDateTime now) {
        if (now.isAfter(expiresAt)) {
            throw new IllegalArgumentException("만료된 주문입니다. 다시 결제해 주세요.");
        }
    }
}
