package com.example.gamescord.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// BaseEntity의 @CreatedDate, @LastModifiedDate 동작용
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
