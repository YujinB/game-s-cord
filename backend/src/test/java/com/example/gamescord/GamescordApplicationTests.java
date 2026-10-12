package com.example.gamescord;

import com.example.gamescord.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;

class GamescordApplicationTests extends IntegrationTestSupport {

    @Value("${toss.payments.api-url}")
    String tossApiUrl;

    @Test
    void contextLoads() {
        assertThat(tossApiUrl).isEqualTo(TOSS.baseUrl());
    }

}
