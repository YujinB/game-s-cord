package com.example.gamescord.payment.infrastructure.persistence;

import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.repository.ChargeOrderRepository;
import com.example.gamescord.support.IntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.example.gamescord.payment.domain.model.ChargeOrderStatus.DONE;
import static com.example.gamescord.payment.domain.model.ChargeOrderStatus.IN_PROGRESS;
import static com.example.gamescord.payment.domain.model.ChargeOrderStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주문 상태 변경(조건부 UPDATE)을 MySQL 컨테이너에서 확인한다.
 * 동시성 확인을 위해 실제로 커밋해야 하므로 TransactionTemplate을 쓴다.
 */
class ChargeOrderRepositoryTest extends IntegrationTestSupport {

    private static final String PG_ORDER_ID = "GAMESCORD-TEST0001";

    @Autowired ChargeOrderRepository chargeOrderRepository;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM charge_order");
    }

    @Test
    @DisplayName("저장한 주문을 pgOrderId로 찾을 수 있다")
    void saveAndFind() {
        Long id = createOrder();

        ChargeOrder found = chargeOrderRepository.findByPgOrderId(PG_ORDER_ID).orElseThrow();
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getStatus()).isEqualTo(PENDING);
        assertThat(found.getExpiresAt()).isNotNull();
        assertThat(chargeOrderRepository.findByPgOrderId("GAMESCORD-NONE")).isEmpty();
    }

    @Test
    @DisplayName("같은 pgOrderId로 주문을 두 번 저장할 수 없다")
    void rejectDuplicatePgOrderId() {
        createOrder();

        assertThatThrownBy(this::createOrder)
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("현재 상태가 from과 같을 때만 바뀐다")
    void changeStatus() {
        Long id = createOrder();

        assertThat(toInProgress(id)).isEqualTo(1);
        assertThat(status(id)).isEqualTo("IN_PROGRESS");

        assertThat(toInProgress(id)).isZero();
        assertThat(status(id)).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("허용되지 않은 전이는 DB에 가기 전에 거절되고 상태는 그대로다")
    void rejectDisallowedTransition() {
        Long id = createOrder();

        // @Repository 빈이 던진 IllegalArgumentException은 스프링이 InvalidDataAccessApiUsageException으로 감싼다
        assertThatThrownBy(() -> tx.execute(s -> chargeOrderRepository.changeStatus(id, DONE, PENDING)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tx.execute(s -> chargeOrderRepository.changeStatus(id, PENDING, DONE)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThat(status(id)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("IN_PROGRESS 주문만 DONE이 되고 paymentKey가 저장된다")
    void markDone() {
        Long id = createOrder();

        assertThat(markDone(id, "pk_1")).isZero();
        assertThat(status(id)).isEqualTo("PENDING");

        toInProgress(id);
        assertThat(markDone(id, "pk_1")).isEqualTo(1);
        assertThat(status(id)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT payment_key FROM charge_order WHERE id = ?", String.class, id))
                .isEqualTo("pk_1");

        assertThat(markDone(id, "pk_2")).isZero();
    }

    @Test
    @DisplayName("같은 주문에 PENDING → IN_PROGRESS가 동시에 N번 와도 1번만 성공한다")
    void concurrentChangeStatus() throws Exception {
        Long id = createOrder();
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return toInProgress(id);
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Integer> result : results) {
            succeeded += result.get();
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1);
        assertThat(status(id)).isEqualTo("IN_PROGRESS");
    }

    private Long createOrder() {
        tx.executeWithoutResult(s -> chargeOrderRepository.save(
                ChargeOrder.create(7L, 1, 4_680, 500, PG_ORDER_ID, LocalDateTime.now())));
        return jdbc.queryForObject("SELECT id FROM charge_order WHERE pg_order_id = ?", Long.class, PG_ORDER_ID);
    }

    private Integer toInProgress(Long id) {
        return tx.execute(s -> chargeOrderRepository.changeStatus(id, PENDING, IN_PROGRESS));
    }

    private Integer markDone(Long id, String paymentKey) {
        return tx.execute(s -> chargeOrderRepository.markDone(id, paymentKey));
    }

    private String status(Long id) {
        return jdbc.queryForObject("SELECT status FROM charge_order WHERE id = ?", String.class, id);
    }
}
