package com.example.gamescord.wallet.infrastructure.persistence;

import com.example.gamescord.wallet.domain.model.Account;
import com.example.gamescord.wallet.domain.model.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountJpaRepository extends JpaRepository<Account, Long> {

    // 평소에 쓰는 일반 조회 메서드 (발행 계좌 조회나 단순 읽기용으로 사용 -> 락 안 걸림)
    Optional<Account> findByAccountTypeAndOwnerUserId(AccountType accountType, Long ownerUserId);

    // 동시성 해결이 필요한 곳(유저 계좌 강제 최신 조회)에만 쓸 FOR UPDATE용 메서드
    // SELECT FOR UPDATE (MYSQL MVCC로 첫 SELECT 기준 스냅샷을 보던 문제 해결)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountType = :accountType AND a.ownerUserId = :ownerUserId")
    Optional<Account> findByAccountTypeAndOwnerUserIdWithLock(
            @Param("accountType") AccountType accountType,
            @Param("ownerUserId") Long ownerUserId
    );

    // 계좌(지갑) 생성(초기화)
    // 유저 계좌가 이미 존재할 때 아무것도 하지 않음(중복 에러X) | 미존재 시 생성
    @Modifying
    @Query(value = """
            INSERT INTO account (account_type, owner_user_id, balance, created_at, updated_at)
            VALUES (:accountType, :ownerUserId, :balance, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("accountType") String accountType,
                        @Param("ownerUserId") Long ownerUserId,
                        @Param("balance") Long balance);

    @Modifying
    @Query(value = """
            UPDATE account 
            SET balance = balance + :delta, updated_at = CURRENT_TIMESTAMP 
            WHERE id = :accountId and balance IS NOT NULL and balance + :delta >= 0
           """, nativeQuery = true)
    int addBalance(@Param("accountId") Long accountId, @Param("delta") long delta);

    @Query(value = """
            SELECT balance FROM account WHERE id = :accountId
    """, nativeQuery = true)
    Long findBalanceById(Long accountId);

}
