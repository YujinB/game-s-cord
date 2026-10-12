package com.example.gamescord.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * 통합 테스트 공통 설정.
 * - MySQL: 테스트 전용 컨테이너 (로컬·운영 DB와 무관, docker-compose와 같은 8.0)
 * - 토스: WireMock 가짜 서버 (실제 토스 키·주소를 쓰지 않음)
 * 컨테이너와 WireMock은 JVM당 한 번만 띄우고 모든 테스트 클래스가 공유한다.
 * 동시성 테스트에서 커밋이 보여야 하므로 @Transactional은 붙이지 않는다.
 */
@SpringBootTest
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    protected static final WireMockServer TOSS = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        MYSQL.start();
        TOSS.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("toss.payments.api-url", TOSS::baseUrl);
        registry.add("toss.payments.secret-key", () -> "test_sk_dummy");
    }

    @AfterEach
    void resetToss() {
        TOSS.resetAll();
    }
}
