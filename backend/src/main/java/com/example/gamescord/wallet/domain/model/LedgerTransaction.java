package com.example.gamescord.wallet.domain.model;

import com.example.gamescord.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Getter
@Table(name = "ledger_transaction",
        // ** 코인 원장 거래 헤더에서 중복을 막음. (멱등성 최종 방어선) **
        // 코인이 움직인 원인, 근거 테이블 및 해당 행이 중복 X
        // ex) 충전 1번과 관련된 주문, 매칭 2번과 관련된 주문 => 즉 해당 요청은 중복이 될 수 없음.
        uniqueConstraints = @UniqueConstraint(columnNames = {"transaction_type", "ref_type", "ref_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerTransaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType; // 코인이 움직인 원인

    @Column(name = "ref_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private RefTableType refTableType; // 근거 기록 테이블

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    public static LedgerTransaction create(TransactionType transactionType, RefTableType refTableType, Long refId, List<LedgerLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("lines size must be more than 1");
        }
        if (lines.stream().mapToLong(LedgerLine::amount).sum() != 0) {
            throw new IllegalArgumentException("lines amount must be 0");
        }
        return new LedgerTransaction(null, transactionType, refTableType, refId);
    }
}
