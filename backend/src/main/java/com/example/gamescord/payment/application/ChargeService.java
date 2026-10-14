package com.example.gamescord.payment.application;

import com.example.gamescord.domain.User;
import com.example.gamescord.dto.coin.CoinResponseDTO;
import com.example.gamescord.dto.coin.TossPaymentConfirmRequestDTO;
import com.example.gamescord.dto.coin.TossPaymentOrderRequestDTO;
import com.example.gamescord.dto.coin.TossPaymentOrderResponseDTO;
import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.model.ChargeOrderStatus;
import com.example.gamescord.payment.domain.model.CoinPackage;
import com.example.gamescord.payment.domain.repository.ChargeOrderRepository;
import com.example.gamescord.payment.infrastructure.toss.TossPaymentRejectedException;
import com.example.gamescord.payment.infrastructure.toss.TossPaymentsClient;
import com.example.gamescord.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChargeService {
    private final ChargeOrderRepository chargeOrderRepository;
    private final TossPaymentsClient tossPaymentsClient;
    private final UserRepository userRepository;      // loginId → userId
    private final ChargeCompleter chargeCompleter;    // 확정 + 지급 (별도 빈이라 @Transactional 적용)
    private final TransactionTemplate transactionTemplate;

    @Transactional
    public TossPaymentOrderResponseDTO createOrder(TossPaymentOrderRequestDTO request, String loginId) {
        CoinPackage coinPackage = CoinPackage.of(request.getPackageId());
        int price = coinPackage.getPrice();
        int coinAmount = coinPackage.getCoinAmount();
        String orderName = NumberFormat.getNumberInstance(Locale.KOREA).format(coinAmount) + "코인 충전";

        Long userId = userRepository.findByLoginId(loginId).map(User::getId).orElseThrow(()->new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        String pgOrderId = "GAMESCORD-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        ChargeOrder chargeOrder = ChargeOrder.create(userId, request.getPackageId(), price, coinAmount, pgOrderId, LocalDateTime.now());
        chargeOrderRepository.save(chargeOrder);

        return TossPaymentOrderResponseDTO.builder().orderId(pgOrderId).orderName(orderName).amount(price).coinAmount(coinAmount).build();
    }

    /**
     * 결제 확정. 토스 호출을 DB 트랜잭션 밖에 두기 위해 메서드 전체에는 @Transactional을 붙이지 않고,
     * 필요한 구간만 TransactionTemplate으로 감싼다 (같은 클래스 안의 @Transactional 메서드 호출은 프록시를 거치지 않아 적용되지 않음).
     */
    public CoinResponseDTO confirm(TossPaymentConfirmRequestDTO request, String loginId) {
        // a. 검증 (클라이언트가 보낸 값을 주문 시점에 서버가 정한 값과 대조)
        ChargeOrder order = chargeOrderRepository.findByPgOrderId(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 주문입니다."));
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        order.validateOwnerAndAmount(user.getId(), request.getAmount());
        order.validateNotExpired(LocalDateTime.now());

        // b. 주문 선점: PENDING → IN_PROGRESS는 한 요청만 성공 (멱등성 1층). 실패하면 토스를 부르지 않음
        Integer claimed = transactionTemplate.execute(s ->
                chargeOrderRepository.changeStatus(order.getId(), ChargeOrderStatus.PENDING, ChargeOrderStatus.IN_PROGRESS));
        if (claimed == null || claimed != 1) {
            throw new IllegalStateException("이미 처리 중이거나 처리된 주문입니다.");
        }

        // c. 토스 승인 (외부 통신은 트랜잭션 밖, Idempotency-Key = pgOrderId로 멱등성 2층)
        try {
            tossPaymentsClient.confirm(request.getPaymentKey(), order.getPgOrderId(), order.getAmount());
        } catch (TossPaymentRejectedException e) {
            // 토스가 확실히 거절 → 결제가 일어나지 않았으므로 FAILED로 확정
            transactionTemplate.execute(s ->
                    chargeOrderRepository.changeStatus(order.getId(), ChargeOrderStatus.IN_PROGRESS, ChargeOrderStatus.FAILED));
            throw e;
        }
        // TossPaymentUnknownException은 잡지 않음 → 토스에서는 승인됐을 수 있으므로 IN_PROGRESS로 남겨 나중에 대사

        // d. 확정 + 지급
        return chargeCompleter.complete(order, request.getPaymentKey());
    }
}
