package com.example.gamescord.wallet.domain.model;

import com.example.gamescord.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "account",
        // 같은 type, user가 동일 계좌를 갖지 않도록 보장.
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_type", "owner_user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    // MySQL UNIQUE는 NULL끼리 중복으로 보지 않으므로 시스템 계좌는 NULL 대신 0을 사용
    public static final long SYSTEM_OWNER_ID = 0L;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId; // users_id (다른 경계는 ID로만 참조), 시스템 계좌는 SYSTEM_OWNER_ID

    private Long balance; // 현재 잔액 = entry 합계의 캐시 (시스템 계좌는 NULL)

    // 잔액 원자 UPDATE(@Modifying)는 엔티티를 거치지 않으므로 쿼리에서 직접 갱신해야 함
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 잔액은 entry 합계의 캐시이므로 0에서 시작
    public static Account createUserAccount(AccountType accountType, Long userId) {
        if (!accountType.isUserOwned()) {
            throw new IllegalArgumentException("Not a user account type: " + accountType);
        }
        if (userId == null || userId <= SYSTEM_OWNER_ID) {
            throw new IllegalArgumentException("Invalid user id: " + userId);
        }
        return new Account(null, accountType, userId, 0L, null);
    }

    public static Account createSystemAccount(AccountType accountType) {
        if (accountType.isUserOwned()) {
            throw new IllegalArgumentException("Not a system account type: " + accountType);
        }
        return new Account(null, accountType, SYSTEM_OWNER_ID, null, null);
    }
}
