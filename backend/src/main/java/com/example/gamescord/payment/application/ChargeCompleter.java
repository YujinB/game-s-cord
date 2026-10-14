package com.example.gamescord.payment.application;

import com.example.gamescord.domain.Coin;
import com.example.gamescord.domain.User;
import com.example.gamescord.dto.coin.CoinResponseDTO;
import com.example.gamescord.payment.domain.model.ChargeOrder;
import com.example.gamescord.payment.domain.repository.ChargeOrderRepository;
import com.example.gamescord.repository.coin.CoinRepository;
import com.example.gamescord.repository.user.UserRepository;
import com.example.gamescord.wallet.application.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 토스 승인이 확인된 주문을 확정하고 코인을 지급한다 (confirm과 결과 불명 주문 대사가 함께 사용).
 * 별도 빈으로 둔 이유: ChargeService 안에서 같은 클래스의 @Transactional 메서드를 부르면
 * 프록시를 거치지 않아 트랜잭션이 적용되지 않는다 (self-invocation).
 */
@Service
@RequiredArgsConstructor
public class ChargeCompleter {

    private final ChargeOrderRepository chargeOrderRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;      // 호환용 point
    private final CoinRepository coinRepository;      // 호환용 충전 내역

    /**
     * markDone은 IN_PROGRESS일 때만 성공하고 원장은 UNIQUE로 막히므로, confirm과 대사가 겹쳐도 지급은 한 번이다.
     * 확정과 지급을 한 트랜잭션으로 묶어 하나라도 실패하면 함께 롤백한다.
     */
    @Transactional
    public CoinResponseDTO complete(ChargeOrder order, String paymentKey) {
        if (chargeOrderRepository.markDone(order.getId(), paymentKey) != 1) {
            throw new IllegalStateException("주문 상태가 승인 중이 아닙니다.");
        }
        walletService.creditCharge(order.getId(), order.getUserId(), order.getCoinAmount()); // 원장 UNIQUE = 멱등성 3층

        // 매칭 이관 전까지 기존 기능(MatchService 등)이 User.point와 coin 내역을 읽으므로 같은 트랜잭션에서 함께 반영
        User currentUser = userRepository.findById(order.getUserId());
        currentUser.setPoint(currentUser.getPoint() + order.getCoinAmount());
        userRepository.saveUser(currentUser);

        Coin coin = new Coin();
        coin.setUsers(currentUser);
        coin.setCoinAmount(order.getCoinAmount());
        coin.setPaymentAmount(order.getAmount());
        coin.setPaymentMethod("토스페이먼츠");
        Coin savedCoin = coinRepository.save(coin);

        return CoinResponseDTO.success("코인이 성공적으로 충전되었습니다.", savedCoin, currentUser.getPoint());
    }
}
