package com.example.gamescord.controller;

import com.example.gamescord.dto.coin.CoinResponseDTO;
import com.example.gamescord.dto.coin.TossPaymentConfirmRequestDTO;
import com.example.gamescord.dto.coin.TossPaymentOrderRequestDTO;
import com.example.gamescord.dto.coin.TossPaymentOrderResponseDTO;
import com.example.gamescord.security.CustomUserDetails;
import com.example.gamescord.payment.application.ChargeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Toss Payments", description = "토스페이먼츠 실결제 API")
@RestController
@RequestMapping("/api/toss")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TossPaymentController {

    private final ChargeService chargeService;

    @Operation(summary = "토스 결제 주문 생성")
    @PostMapping("/create-order")
    public ResponseEntity<TossPaymentOrderResponseDTO> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TossPaymentOrderRequestDTO requestDto) {

        TossPaymentOrderResponseDTO response = chargeService.createOrder(requestDto, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "토스 결제 최종 승인")
    @PostMapping("/confirm")
    public ResponseEntity<CoinResponseDTO> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody TossPaymentConfirmRequestDTO requestDto) {

        CoinResponseDTO response = chargeService.confirm(requestDto, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}