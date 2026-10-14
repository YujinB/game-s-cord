package com.example.gamescord.payment.infrastructure.toss;

import com.example.gamescord.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 토스 승인 호출을 WireMock(가짜 토스)으로 확인한다.
 */
class TossPaymentsClientTest extends IntegrationTestSupport {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";

    @Autowired TossPaymentsClient client;

    @Test
    @DisplayName("승인 요청에 Basic 인증·Idempotency-Key·주문 정보가 실리고, GET 조회는 하지 않는다")
    void sendsConfirmRequest() {
        TOSS.stubFor(post(urlEqualTo(CONFIRM_PATH)).willReturn(okJson("""
                {"paymentKey":"pk_1","orderId":"GAMESCORD-A","status":"DONE","totalAmount":4680}
                """)));

        assertThatNoException().isThrownBy(() -> client.confirm("pk_1", "GAMESCORD-A", 4680));

        String basic = "Basic " + Base64.getEncoder()
                .encodeToString("test_sk_dummy:".getBytes(StandardCharsets.UTF_8));
        TOSS.verify(1, postRequestedFor(urlEqualTo(CONFIRM_PATH))
                .withHeader("Authorization", equalTo(basic))
                .withHeader("Idempotency-Key", equalTo("GAMESCORD-A"))
                .withRequestBody(equalToJson("""
                        {"paymentKey":"pk_1","orderId":"GAMESCORD-A","amount":4680}
                        """)));
        TOSS.verify(0, getRequestedFor(urlMatching("/v1/payments/.*")));
    }

    @Test
    @DisplayName("토스가 4xx로 거절하면 Rejected 예외에 토스 메시지가 담긴다")
    void rejected() {
        TOSS.stubFor(post(urlEqualTo(CONFIRM_PATH)).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code":"INVALID_REQUEST","message":"결제 금액이 일치하지 않습니다."}
                        """)));

        assertThatThrownBy(() -> client.confirm("pk_1", "GAMESCORD-A", 4680))
                .isInstanceOf(TossPaymentRejectedException.class)
                .hasMessage("결제 금액이 일치하지 않습니다.");
    }

    @Test
    @DisplayName("토스가 5xx를 주면 결과를 모르는 것으로 본다")
    void serverError() {
        TOSS.stubFor(post(urlEqualTo(CONFIRM_PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.confirm("pk_1", "GAMESCORD-A", 4680))
                .isInstanceOf(TossPaymentUnknownException.class);
    }

    @Test
    @DisplayName("응답이 읽기 타임아웃보다 늦으면 결과를 모르는 것으로 본다")
    void timeout() {
        // 테스트 읽기 타임아웃은 2초 (IntegrationTestSupport)
        TOSS.stubFor(post(urlEqualTo(CONFIRM_PATH)).willReturn(okJson("{}").withFixedDelay(3_000)));

        long start = System.currentTimeMillis();
        assertThatThrownBy(() -> client.confirm("pk_1", "GAMESCORD-A", 4680))
                .isInstanceOf(TossPaymentUnknownException.class);
        assertThat(System.currentTimeMillis() - start).isLessThan(3_000);
    }

    @Test
    @DisplayName("주문번호로 결제를 조회하면 상태·paymentKey·금액을 돌려준다")
    void findByOrderId() {
        TOSS.stubFor(get(urlEqualTo("/v1/payments/orders/GAMESCORD-A")).willReturn(okJson("""
                {"paymentKey":"pk_1","orderId":"GAMESCORD-A","status":"DONE","totalAmount":4680}
                """)));

        TossPayment payment = client.findByOrderId("GAMESCORD-A").orElseThrow();

        assertThat(payment.isDone()).isTrue();
        assertThat(payment.paymentKey()).isEqualTo("pk_1");
        assertThat(payment.totalAmount()).isEqualTo(4680);
        String basic = "Basic " + Base64.getEncoder()
                .encodeToString("test_sk_dummy:".getBytes(StandardCharsets.UTF_8));
        TOSS.verify(1, getRequestedFor(urlEqualTo("/v1/payments/orders/GAMESCORD-A"))
                .withHeader("Authorization", equalTo(basic)));
    }

    @Test
    @DisplayName("토스에 결제가 없으면(404) empty")
    void findByOrderIdNotFound() {
        TOSS.stubFor(get(urlEqualTo("/v1/payments/orders/GAMESCORD-A")).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code":"NOT_FOUND_PAYMENT","message":"존재하지 않는 결제 정보 입니다."}
                        """)));

        assertThat(client.findByOrderId("GAMESCORD-A")).isEmpty();
    }

    @Test
    @DisplayName("조회가 5xx면 결과를 모르는 것으로 본다")
    void findByOrderIdServerError() {
        TOSS.stubFor(get(urlEqualTo("/v1/payments/orders/GAMESCORD-A")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.findByOrderId("GAMESCORD-A"))
                .isInstanceOf(TossPaymentUnknownException.class);
    }

    @Test
    @DisplayName("토스가 409(같은 키 처리 중)를 주면 거절이 아니라 결과를 모르는 것으로 본다")
    void conflictIsUnknown() {
        TOSS.stubFor(post(urlEqualTo(CONFIRM_PATH)).willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code":"IDEMPOTENT_REQUEST_PROCESSING","message":"이전 멱등 요청이 처리 중입니다."}
                        """)));

        assertThatThrownBy(() -> client.confirm("pk_1", "GAMESCORD-A", 4680))
                .isInstanceOf(TossPaymentUnknownException.class);
    }
}
