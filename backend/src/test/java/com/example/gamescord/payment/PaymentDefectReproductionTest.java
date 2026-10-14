package com.example.gamescord.payment;

import com.example.gamescord.domain.User;
import com.example.gamescord.repository.user.UserRepository;
import com.example.gamescord.security.JwtUtil;
import com.example.gamescord.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 0단계: 기존 결제 결함 1~3 재현 (Notion 「재현 테스트 계획」).
 * 버그 동작이 아니라 지켜져야 할 불변식으로 단언한다 → 지금은 실패(수정 전 증거), 고친 뒤 통과(회귀 테스트).
 * 대상은 기존 API(/api/coins/charge, /api/toss/*). 새 API로 바뀌면 호출부만 옮기고 불변식은 유지한다.
 */
class PaymentDefectReproductionTest extends IntegrationTestSupport {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository userRepository;
    @Autowired JwtUtil jwtUtil;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    private String loginId;
    private String token;

    @BeforeEach
    void createUser() {
        loginId = "repro-" + UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setLoginId(loginId);
        user.setEmail(loginId + "@test.com");
        user.setUsersName("repro");
        user.setPoint(0L);
        user.setLoginFailCount(0);
        user.setEnabled(true);
        userRepository.saveUser(user);

        token = jwtUtil.generateAccessToken(loginId);
    }

    @AfterEach
    void cleanUp() {
        // 결제가 성공하면 원장·주문·지갑 계좌도 생기므로 함께 지운다 (시스템 계좌는 남김)
        jdbc.update("DELETE FROM ledger_entry");
        jdbc.update("DELETE FROM ledger_transaction");
        jdbc.update("DELETE FROM charge_order");
        jdbc.update("DELETE FROM account WHERE owner_user_id <> 0");
        jdbc.update("DELETE c FROM coin c JOIN users u ON c.users_id = u.users_id WHERE u.login_id = ?", loginId);
        jdbc.update("DELETE FROM users WHERE login_id = ?", loginId);
    }

    @Test
    @DisplayName("결함 1: PG 승인 없이는 코인이 늘면 안 된다 (가짜 결제 API)")
    void defect1_chargeWithoutPayment() {
        ResponseEntity<String> response = callApi("/api/coins/charge",
                Map.of("packageId", 10, "paymentMethod", "카드결제"));

        System.out.printf("[결함1 관찰] status=%s, point=%d, PG 요청=%d건%n",
                response.getStatusCode(), point(), TOSS.getAllServeEvents().size());

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("PG 승인 없이 코인을 주는 API가 열려 있음")
                .isFalse();
        assertThat(point())
                .as("PG 승인 없이 지급된 코인")
                .isZero();
    }

    @Test
    @DisplayName("결함 2: 다른 주문·금액의 결제키로 충전되면 안 된다 (결제키 재사용)")
    void defect2_reuseOldPaymentKey() throws Exception {
        // 예전에 4,680원으로 끝난 다른 주문의 결제
        TOSS.stubFor(get(urlEqualTo("/v1/payments/pk_old"))
                .willReturn(okJson("""
                        {"paymentKey":"pk_old","orderId":"OLD-ORDER","status":"DONE","totalAmount":4680}
                        """)));
        TOSS.stubFor(post(urlEqualTo(CONFIRM_PATH))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제 입니다."}
                                """)));

        // 새 주문: 패키지 10 (500,000코인 / 4,320,000원)
        JsonNode order = createOrder(10);
        ResponseEntity<String> response = callApi("/api/toss/confirm", Map.of(
                "paymentKey", "pk_old",
                "orderId", order.get("orderId").asText(),
                "amount", order.get("amount").asInt()));

        System.out.printf("[결함2 관찰] status=%s, point=%d, PG 승인 요청=%d건%n",
                response.getStatusCode(), point(), confirmCalls());

        assertThat(point())
                .as("이 주문(%s, %d원)에 대한 PG 결제가 없는데 지급된 코인",
                        order.get("orderId").asText(), order.get("amount").asInt())
                .isZero();
    }

    @Test
    @DisplayName("결함 3: 같은 승인이 동시에 오면 코인은 PG 승인 횟수만큼만 지급돼야 한다 (이중 지급)")
    void defect3_concurrentConfirm() throws Exception {
        String scenario = "confirm";
        // 승인 전: 조회하면 아직 승인 대기
        TOSS.stubFor(get(urlEqualTo("/v1/payments/pk_new")).inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson("""
                        {"paymentKey":"pk_new","status":"IN_PROGRESS"}
                        """)));
        // 승인 요청: 받는 순간 토스 쪽은 승인됨(APPROVED), 응답은 500ms 뒤에 돌아감
        TOSS.stubFor(post(urlEqualTo(CONFIRM_PATH)).inScenario(scenario)
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("APPROVED")
                .willReturn(okJson("""
                        {"paymentKey":"pk_new","status":"DONE"}
                        """).withFixedDelay(500)));
        // 승인 후: 조회하면 DONE, 다시 승인 요청하면 거절
        TOSS.stubFor(get(urlEqualTo("/v1/payments/pk_new")).inScenario(scenario)
                .whenScenarioStateIs("APPROVED")
                .willReturn(okJson("""
                        {"paymentKey":"pk_new","status":"DONE"}
                        """)));
        TOSS.stubFor(post(urlEqualTo(CONFIRM_PATH)).inScenario(scenario)
                .whenScenarioStateIs("APPROVED")
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제 입니다."}
                                """)));

        // 패키지 1 (500코인 / 4,680원)
        JsonNode order = createOrder(1);
        Map<String, Object> confirmBody = Map.of(
                "paymentKey", "pk_new",
                "orderId", order.get("orderId").asText(),
                "amount", order.get("amount").asInt());

        // A: 승인 요청 → 토스가 승인했지만 응답이 아직 안 온 구간을 만든다
        CompletableFuture<ResponseEntity<String>> a =
                CompletableFuture.supplyAsync(() -> callApi("/api/toss/confirm", confirmBody));
        awaitConfirmCallReceived();

        // B: 그 구간에 같은 승인 요청
        ResponseEntity<String> b = callApi("/api/toss/confirm", confirmBody);
        ResponseEntity<String> aResult = a.get(10, TimeUnit.SECONDS);

        int approvals = confirmCalls();
        int chargeRows = tossChargeRows();
        System.out.printf("[결함3 관찰] A=%s, B=%s, PG 승인 요청=%d건, 충전 coin 행=%d건, point=%d%n",
                aResult.getStatusCode(), b.getStatusCode(), approvals, chargeRows, point());

        assertThat(chargeRows)
                .as("충전 행 수 = PG 승인 요청 수 (%d)", approvals)
                .isEqualTo(approvals);
    }

    // ---------- helpers ----------

    private JsonNode createOrder(int packageId) throws Exception {
        ResponseEntity<String> response = callApi("/api/toss/create-order", Map.of("packageId", packageId));
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("주문 생성 응답: %s", response.getBody())
                .isTrue();
        return objectMapper.readTree(response.getBody());
    }

    private ResponseEntity<String> callApi(String path, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private long point() {
        return jdbc.queryForObject("SELECT point FROM users WHERE login_id = ?", Long.class, loginId);
    }

    private int confirmCalls() {
        return TOSS.findAll(postRequestedFor(urlEqualTo(CONFIRM_PATH))).size();
    }

    private int tossChargeRows() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM coin c JOIN users u ON c.users_id = u.users_id
                WHERE u.login_id = ? AND c.payment_method = '토스페이먼츠'
                """, Integer.class, loginId);
    }

    private void awaitConfirmCallReceived() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (confirmCalls() == 0) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("A의 승인 요청이 WireMock에 도착하지 않음");
            }
            Thread.sleep(10);
        }
    }
}
