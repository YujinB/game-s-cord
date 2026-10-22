package com.example.gamescord.config;

import com.example.gamescord.security.CustomOAuth2UserService;
import com.example.gamescord.security.JwtAuthenticationFilter;
import com.example.gamescord.security.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(HttpMethod.GET, "/api/gamemates/*/*/reviews").permitAll()
                        .requestMatchers(
                                // User & Auth
                                "/api/users/signup",
                                "/api/users/login",
                                "/api/auth/refresh",
                                "/api/auth/request-verification",
                                "/api/auth/request-password-reset",
                                "/api/auth/reset-password",
                                "/api/users/check-id",

                                // OAuth2
                                "/login/oauth2/**",
                                "/oauth2/**",

                                // Gamemate Public
                                "/api/gamemates/search",
                                "/api/gamemates/profile/**",
                                "/api/gamemates/popular/**",
                                "/api/gamemates/filter",

                                // Games Public
                                "/api/games/**",

                                // Swagger
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",

                                // Actuator (노출 범위는 프로필 설정이 결정, 기본은 health만)
                                "/actuator/**",

                                // Error
                                "/error"

                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}