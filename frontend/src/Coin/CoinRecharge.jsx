import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api/axios";
import "../css/CoinRecharge.css";
import coinImage from "../assets/coin.jpg";
import HeaderTabs from "../Coin/HeaderTabs";
import Sidebar from "../page/MyPage/Sidebar";
import styles from ".././page/MyPage/MyPage.module.css";

// 코인 상품 데이터
const coinProducts = [
  { id: 1, points: "500", price: "4,680" },
  { id: 2, points: "1,000", price: "9,360" },
  { id: 3, points: "2,000", price: "18,720" },
  { id: 4, points: "5,000", price: "45,600" },
  { id: 5, points: "10,000", price: "91,200" },
  { id: 6, points: "30,000", price: "266,400" },
  { id: 7, points: "50,000", price: "444,000" },
  { id: 8, points: "100,000", price: "864,000" },
  { id: 9, points: "300,000", price: "2,592,000" },
  { id: 10, points: "500,000", price: "4,320,000" },
];

const CoinItem = ({ id, points, price }) => {
  const [isProcessing, setIsProcessing] = useState(false);
  // 토스 클라이언트 키는 frontend/.env.local의 VITE_TOSS_CLIENT_KEY (git에 올리지 않음)
  const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;

  const handleRecharge = async () => {
    // 1. 사용자 확인 팝업
    const message = `${points} 코인을 ₩${price} 에 충전하시겠습니까?`;
    if (!window.confirm(message)) return;

    if (isProcessing) return;
    setIsProcessing(true);

    try {
      // 2. 로그인 토큰 체크
      const token = localStorage.getItem("accessToken");
      if (!token) {
        alert("로그인 정보가 없습니다. 다시 로그인해 주세요.");
        window.location.href = "/login";
        return;
      }

      // 3. 백엔드 주문 생성 API 호출
      const response = await api.post(`/toss/create-order`, { packageId: id });
      const { orderId, orderName, amount } = response.data;

      // 4. 토스 SDK 로드 확인 (index.html에 스크립트가 있어야 함)
      if (typeof window.TossPayments !== "function") {
        alert("결제 모듈을 불러오는 중입니다. 잠시 후 다시 시도해주세요.");
        setIsProcessing(false);
        return;
      }

      // 5. 토스 결제창 실행 (기본 수단: 카드)
      const tossPayments = window.TossPayments(clientKey);

      await tossPayments.requestPayment("카드", {
        amount: amount,
        orderId: orderId,
        orderName: orderName,
        successUrl: `${window.location.origin}/toss/success`,
        failUrl: `${window.location.origin}/toss/fail`,
      });
    } catch (error) {
      console.error("결제 준비 중 오류:", error);
      const errorMsg =
        error.response?.data?.message || "결제 진행 중 오류가 발생했습니다.";
      alert(errorMsg);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <button
      className={`coin-item ${isProcessing ? "processing" : ""}`}
      onClick={handleRecharge}
      disabled={isProcessing}
    >
      <div className="point-info">
        <img src={coinImage} alt="포인트 코인" className="coin-icon" />
        <p className="points-amount">{points}</p>
      </div>
      <hr />
      <p className="price-amount">
        {isProcessing ? "처리 중..." : `￦ ${price}`}
      </p>
    </button>
  );
};

const CoinRecharge = () => {
  return (
    <div className={styles.wrapper}>
      <Sidebar />
      <div style={{ flex: 1, marginRight: "80px", minHeight: "500px" }}>
        <HeaderTabs />
        <div className="coin-list-container">
          {coinProducts.map((product) => (
            <CoinItem
              key={product.id}
              id={product.id}
              points={product.points}
              price={product.price}
            />
          ))}
        </div>
      </div>
    </div>
  );
};

export default CoinRecharge;
