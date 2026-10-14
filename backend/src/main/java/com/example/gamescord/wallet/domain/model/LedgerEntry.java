package com.example.gamescord.wallet.domain.model;

import com.example.gamescord.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "ledger_entry",
        // unique는 필요 X 중복은 헤더(transaction)에서 막음.
        // 복식 원장으로 거래당 최소 2건의 entry 발생. 또한, 동일 account에 대한 entry도 발생.
        indexes = {@Index(name = "idx_ledger_entry_account", columnList = "account_id, id"),
                @Index(name = "idx_ledger_entry_transaction", columnList = "transaction_id, id")}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private LedgerTransaction transaction; //관련 거래(코인)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account; //관련 계좌

    @Column(nullable = false)
    private Long amount; //코인 변화량 (부호 있음, 거래 당 합 0)

    private Long balanceAfter; // 유저 계좌만 반영 후 잔액

    public static LedgerEntry create(LedgerTransaction transaction, LedgerLine line, Long balanceAfter) {
        if(transaction == null){ throw new IllegalArgumentException("transaction must not be null");}
        return new LedgerEntry(null, transaction, line.account(), line.amount(), balanceAfter);
    }
}
