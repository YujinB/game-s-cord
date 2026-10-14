package com.example.gamescord.payment.application;

import com.example.gamescord.domain.User;
import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.model.ChargeOrderStatus;
import com.example.gamescord.payment.domain.repository.ChargeOrderRepository;
import com.example.gamescord.repository.user.UserRepository;
import com.example.gamescord.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결과 불명 주문 대사를 MySQL 컨테이너 + WireMock(가짜 토스)으로 확인한다.
 * 스케줄러는 테스트에서 꺼져 있고 reconcile(now)를 직접 호출한다.
 */
class ChargeReconcilerTest extends IntegrationTestSupport {

    private static final String PG_ORDER_ID = "GAMESCORD-RECON0001";
    private static final String LOOKUP_PATH = "/v1/payments/orders/" + PG_ORDER_ID;
    private static final int PRICE = 4_680;
    private static final int COIN = 500;

    @Autowired ChargeReconciler reconciler;
    @Autowired ChargeOrderRepository chargeOrderRepository;
    @Autowired UserRepository userRepository;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private Long userId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        String loginId = "recon-" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setLoginId(loginId);
        user.setEmail(loginId + "@test.com");
        user.setUsersName("recon");
        user.setPoint(0L);
        user.setLoginFailCount(0);
        user.setEnabled(true);
        userId = userRepository.saveUser(user).getId();

        // confirm이 토스 응답을 받지 못해 IN_PROGRESS로 남은 주문
        tx.executeWithoutResult(s -> chargeOrderRepository.save(
                ChargeOrder.create(userId, 1, PRICE, COIN, PG_ORDER_ID, LocalDateTime.now())));
        orderId = jdbc.queryForObject("SELECT id FROM charge_order WHERE pg_order_id = ?", Long.class, PG_ORDER_ID);
        tx.execute(s -> chargeOrderRepository.changeStatus(orderId, ChargeOrderStatus.PENDING, ChargeOrderStatus.IN_PROGRESS));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM ledger_entry");
        jdbc.update("DELETE FROM ledger_transaction");
        jdbc.update("DELETE FROM charge_order");
        jdbc.update("DELETE FROM account WHERE owner_user_id <> 0");
        jdbc.update("DELETE FROM coin WHERE users_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE users_id = ?", userId);
    }

    @Test
    @DisplayName("토스가 DONE이고 금액이 맞으면 주문을 확정하고 코인을 한 번 지급한다")
    void tossDone() {
        stubLookup("DONE", PRICE);

        reconciler.reconcile(staleNow());

        assertThat(status()).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT payment_key FROM charge_order WHERE id = ?", String.class, orderId))
                .isEqualTo("pk_recon");
        assertThat(chargeLedgerCount()).isEqualTo(1);
        assertThat(walletBalance()).isEqualTo(COIN);
        assertThat(point()).isEqualTo(COIN);
    }

    @Test
    @DisplayName("대사를 두 번 돌려도 지급은 한 번이다")
    void reconcileTwice() {
        stubLookup("DONE", PRICE);

        reconciler.reconcile(staleNow());
        reconciler.reconcile(staleNow());

        assertThat(chargeLedgerCount()).isEqualTo(1);
        assertThat(walletBalance()).isEqualTo(COIN);
        assertThat(point()).isEqualTo(COIN);
    }

    @Test
    @DisplayName("토스에 결제가 없으면(404) FAILED로 바꾸고 지급하지 않는다")
    void tossNotFound() {
        TOSS.stubFor(get(urlEqualTo(LOOKUP_PATH)).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code":"NOT_FOUND_PAYMENT","message":"존재하지 않는 결제 정보 입니다."}
                        """)));

        reconciler.reconcile(staleNow());

        assertThat(status()).isEqualTo("FAILED");
        assertThat(chargeLedgerCount()).isZero();
        assertThat(point()).isZero();
    }

    @Test
    @DisplayName("토스가 ABORTED면 FAILED로 바꾼다")
    void tossAborted() {
        stubLookup("ABORTED", PRICE);

        reconciler.reconcile(staleNow());

        assertThat(status()).isEqualTo("FAILED");
        assertThat(chargeLedgerCount()).isZero();
    }

    @Test
    @DisplayName("토스 조회가 5xx면 IN_PROGRESS로 남겨 다음 회차에 다시 시도한다")
    void tossLookupFails() {
        TOSS.stubFor(get(urlEqualTo(LOOKUP_PATH)).willReturn(aResponse().withStatus(500)));

        reconciler.reconcile(staleNow());

        assertThat(status()).isEqualTo("IN_PROGRESS");
        assertThat(chargeLedgerCount()).isZero();
    }

    @Test
    @DisplayName("토스는 DONE인데 금액이 다르면 자동 처리하지 않는다")
    void amountMismatch() {
        stubLookup("DONE", 100);

        reconciler.reconcile(staleNow());

        assertThat(status()).isEqualTo("IN_PROGRESS");
        assertThat(chargeLedgerCount()).isZero();
    }

    @Test
    @DisplayName("방금 IN_PROGRESS가 된 주문은 confirm이 처리 중일 수 있으므로 토스에 묻지도 않는다")
    void freshOrderUntouched() {
        stubLookup("DONE", PRICE);

        reconciler.reconcile(LocalDateTime.now());

        assertThat(status()).isEqualTo("IN_PROGRESS");
        TOSS.verify(0, getRequestedFor(urlEqualTo(LOOKUP_PATH)));
    }

    // ── helpers ──

    // 대사 기준(5분)이 지난 시각
    private LocalDateTime staleNow() {
        return LocalDateTime.now().plus(ChargeReconciler.STALE_AFTER).plusMinutes(1);
    }

    private void stubLookup(String status, int totalAmount) {
        TOSS.stubFor(get(urlEqualTo(LOOKUP_PATH)).willReturn(okJson("""
                {"paymentKey":"pk_recon","orderId":"%s","status":"%s","totalAmount":%d}
                """.formatted(PG_ORDER_ID, status, totalAmount))));
    }

    private String status() {
        return jdbc.queryForObject("SELECT status FROM charge_order WHERE id = ?", String.class, orderId);
    }

    private int chargeLedgerCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction WHERE transaction_type = 'CHARGE' AND ref_id = ?",
                Integer.class, orderId);
    }

    private long walletBalance() {
        Long balance = jdbc.queryForObject("""
                SELECT COALESCE(MAX(balance), 0) FROM account
                WHERE account_type = 'USER_COIN' AND owner_user_id = ?
                """, Long.class, userId);
        return balance;
    }

    private long point() {
        return jdbc.queryForObject("SELECT point FROM users WHERE users_id = ?", Long.class, userId);
    }
}
