package com.example.gamescord.payment.infrastructure.toss;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * 토스페이먼츠 승인 API 호출만 담당한다.
 * - GET으로 상태만 보고 통과시키는 지름길은 두지 않는다 (다른 주문의 결제키 재사용 차단)
 * - Idempotency-Key로 같은 주문의 재요청이 결제를 두 번 일으키지 않게 한다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String ORDER_LOOKUP_PATH = "/v1/payments/orders/";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${toss.payments.secret-key}")
    private String secretKey;

    @Value("${toss.payments.api-url}")
    private String apiUrl;

    public void confirm(String paymentKey, String pgOrderId, int amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", pgOrderId); // 같은 주문이면 토스가 처음 결과를 그대로 돌려줌

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", pgOrderId,
                "amount", amount);

        try {
            // 2xx가 아니면 RestTemplate이 예외를 던지므로 상태 코드를 따로 검사하지 않음
            restTemplate.exchange(apiUrl + CONFIRM_PATH, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        } catch (HttpClientErrorException.Conflict e) {
            // 409: 같은 Idempotency-Key의 원래 요청을 토스가 아직 처리 중 → 실패가 아니라 결과 모름
            log.warn("토스 승인 처리 중(409) | orderId={}", pgOrderId);
            throw new TossPaymentUnknownException("토스가 같은 주문의 승인을 처리 중입니다.", e);
        } catch (HttpClientErrorException e) {
            // 그 외 4xx: 토스가 승인을 거절함 (금액 불일치, 한도 초과 등) → 확실한 실패
            String message = parseErrorMessage(e.getResponseBodyAsString());
            log.warn("토스 승인 거절 | orderId={} | status={} | message={}", pgOrderId, e.getStatusCode(), message);
            throw new TossPaymentRejectedException(message);
        } catch (HttpServerErrorException | ResourceAccessException e) {
            // 5xx·타임아웃·연결 끊김: 토스에서는 승인됐을 수도 있음 → 실패로 확정하면 안 됨
            log.error("토스 승인 결과 불명 | orderId={} | cause={}", pgOrderId, e.getMessage());
            throw new TossPaymentUnknownException("토스 승인 결과를 확인할 수 없습니다.", e);
        }
    }

    /**
     * 주문번호로 토스 결제 상태를 조회한다 (결과 불명 주문 대사용).
     * 결제 기록이 없으면(404) empty, 조회 자체가 실패하면(5xx·타임아웃) Unknown 예외.
     */
    public Optional<TossPayment> findByOrderId(String pgOrderId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey());

        try {
            String body = restTemplate.exchange(apiUrl + ORDER_LOOKUP_PATH + pgOrderId, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class).getBody();
            JsonNode json = objectMapper.readTree(body);
            return Optional.of(new TossPayment(
                    json.path("status").asText(),
                    json.path("paymentKey").asText(null),
                    json.path("totalAmount").asInt()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty(); // 토스에 이 주문의 결제가 없음 → 돈이 나가지 않음
        } catch (HttpServerErrorException | ResourceAccessException e) {
            throw new TossPaymentUnknownException("토스 결제 조회에 실패했습니다.", e);
        } catch (HttpClientErrorException e) {
            throw new TossPaymentUnknownException("토스 결제 조회 요청이 거절되었습니다: " + e.getStatusCode(), e);
        } catch (JsonProcessingException e) {
            throw new TossPaymentUnknownException("토스 결제 조회 응답을 읽을 수 없습니다.", e);
        }
    }

    // 토스 인증 방식: "시크릿키:"를 Base64로 인코딩 (비밀번호 자리는 비움)
    private String encodedSecretKey() {
        return Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    // 토스 에러 응답 형식: {"code": "...", "message": "..."}
    private String parseErrorMessage(String errorBody) {
        try {
            // JSON 문자열을 트리구조 노드로 변환 후 "message" node 찾기
            JsonNode message = objectMapper.readTree(errorBody).get("message");
            return message != null ? message.asText() : "알 수 없는 오류";
        } catch (Exception e) {
            // 만약 토스가 JSON이 아니라 엉뚱한 HTML 에러 페이지 등을 보냈을 때 방어
            return "알 수 없는 오류";
        }
    }
}
