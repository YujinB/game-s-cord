package com.example.gamescord.service.coin;

import com.example.gamescord.domain.Coin;
import com.example.gamescord.domain.User;
import com.example.gamescord.dto.coin.*;
import com.example.gamescord.payment.domain.model.CoinPackage;
import com.example.gamescord.repository.coin.CoinRepository;
import com.example.gamescord.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CoinService {

    private final UserRepository userRepository;
    private final CoinRepository coinRepository;


    @Transactional(readOnly = true)
    public PrepareChargeResponseDTO prepareCharge(PrepareChargeRequestDTO requestDto) {
        // 패키지 정의는 결제와 같은 CoinPackage 한 곳 (없는 id면 "존재하지 않는 패키지입니다.")
        CoinPackage coinPackage = CoinPackage.of(requestDto.getPackageId());

        List<String> paymentMethods = List.of("카드결제", "계좌이체", "간편 결제");

        return PrepareChargeResponseDTO.builder()
                .coinAmount(coinPackage.getCoinAmount())
                .paymentAmount(coinPackage.getPrice())
                .paymentMethods(paymentMethods)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CoinHistoryResponseDTO> getCoinHistory(String loginId) {
        List<Coin> coinHistory = coinRepository.findByUserLoginId(loginId);
        return coinHistory.stream()
                .map(CoinHistoryResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public CoinResponseDTO refundCoin(String loginId, CoinRefundRequestDTO requestDto) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Coin coinToRefund = coinRepository.findById(requestDto.getCoinId())
                .orElseThrow(() -> new IllegalArgumentException("환불할 거래 내역을 찾을 수 없습니다."));

        if (!coinToRefund.getUsers().getId().equals(user.getId())) {
            throw new IllegalArgumentException("본인의 거래 내역만 환불할 수 있습니다.");
        }

        // 양수 금액인 '충전' 내역만 환불 가능
        if (coinToRefund.getCoinAmount() < 0) {
            throw new IllegalArgumentException("충전 내역만 환불할 수 있습니다.");
        }

        user.setPoint(user.getPoint() - coinToRefund.getCoinAmount());
        userRepository.saveUser(user);

        coinRepository.delete(coinToRefund);

        return CoinResponseDTO.success("환불 처리가 완료되었습니다.", user.getPoint());
    }


    @Transactional
    public Coin useCoinForMatch(User user, Long amount) {
        user.setPoint(user.getPoint() - amount.intValue());
        userRepository.saveUser(user);

        Coin useCoin = new Coin();
        useCoin.setUsers(user);
        useCoin.setCoinAmount(-amount.intValue()); // 음수
        useCoin.setPaymentAmount(0);
        useCoin.setPaymentMethod("MATCH_PENDING");
        return coinRepository.save(useCoin);
    }

    @Transactional
    public Coin payoutToGamemate(User gamemateUser, Long amount) {
        gamemateUser.setPoint(gamemateUser.getPoint() + amount.intValue());
        userRepository.saveUser(gamemateUser);

        Coin payoutCoin = new Coin();
        payoutCoin.setUsers(gamemateUser);
        payoutCoin.setCoinAmount(amount.intValue()); // 양수
        payoutCoin.setPaymentAmount(0);
        payoutCoin.setPaymentMethod("GAMEMATE_PAYOUT");
        return coinRepository.save(payoutCoin);
    }

    @Transactional
    public Coin cancelMatchRefund(User requester, Long amount) {
        requester.setPoint(requester.getPoint() + amount.intValue());
        userRepository.saveUser(requester);

        Coin refundCoin = new Coin();
        refundCoin.setUsers(requester);
        refundCoin.setCoinAmount(amount.intValue()); // 양수
        refundCoin.setPaymentAmount(0);
        refundCoin.setPaymentMethod("MATCH_CANCELLED");
        return coinRepository.save(refundCoin);
    }

    @Transactional(readOnly = true)
    public CoinBalanceResponseDTO getBalance(String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return CoinBalanceResponseDTO.builder()
                .userId(user.getId())
                .balance(user.getPoint())
                .build();
    }
}
