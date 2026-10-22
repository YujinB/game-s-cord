# PG 지연 시 커넥션 풀 고갈 측정

토스 승인 API가 느려질 때, 결제와 상관없는 API(잔액 조회)까지 느려지는지 측정했다.

## 가설

`ChargeService.confirm()`은 토스 호출을 DB 트랜잭션 밖에 둔다. 그런데 `spring.jpa.open-in-view`가 기본값(true)이면 요청이 끝날 때까지 영속성 컨텍스트가 열려 있고, 처음 받은 DB 커넥션을 요청 끝까지 놓지 않는다. 그러면 토스를 기다리는 동안에도 커넥션이 묶이고, Hikari 풀(기본 10개)이 바닥나면 다른 API도 커넥션을 기다리게 된다.

## 환경

| 항목 | 값 |
|---|---|
| 앱 | 로컬 `java -jar` (Java 21 런타임), 포트 8081, `perf` 프로필 |
| DB | MySQL 8.0 (Docker), 3307 |
| 풀·스레드 | Hikari 최대 10 (기본값), Tomcat 최대 200 (기본값) |
| 가짜 토스 | WireMock 3.13.2 (Docker, 8089). 승인 API가 `PG_DELAY_MS` 뒤에 200 응답 |
| 부하 | k6 0.54.0 (Docker), 30초 |
| 머신 | Windows 11, Docker Desktop. 앱·DB·부하기가 같은 PC |

**부하 구성** (`k6/pg-latency.js`, 세 시나리오 동시 실행)
- `confirm`: VU 20개가 미리 만든 주문을 계속 승인. 주문마다 다른 사용자 (100명 순환)
- `balance`: 잔액 조회 초당 20건 (도착률 고정)
- `pool`: 0.5초마다 actuator에서 Hikari `active`·`pending`을 읽음

**비교 조건**
- A: OSIV 켜짐 (`--spring.jpa.open-in-view=true`, 변경 전 기본값)
- B: OSIV 꺼짐 (`spring.jpa.open-in-view=false`)
- 두 조건 모두 같은 jar를 쓴다. 조건마다 앱을 다시 띄우고 워밍업 1회는 버린 뒤, 지연 0ms·3000ms를 번갈아 3회씩 측정했다.

## 결과

3회 측정값 (괄호 안은 중앙값)

| 조건 | PG 지연 | 잔액 조회 p50 | 잔액 조회 p95 | 승인 p95 | 30초간 승인 수 | Hikari pending 최대 |
|---|---|---|---|---|---|---|
| A (OSIV 켬) | 0ms | 21 / 26 / 19ms | 441 / 435 / 328ms (**435ms**) | 831 / 822 / 617ms | 400\* | 25 / 20 / 18 |
| A (OSIV 켬) | 3000ms | 4.37 / 4.18 / 4.22s | 6.19 / 6.07 / 6.20s (**6.19s**) | 8.96 / 6.83 / 6.79s | 100 / 110 / 110 | 122 / 120 / 127 |
| B (OSIV 끔) | 0ms | 20 / 27 / 20ms | 177 / 229 / 161ms (**177ms**) | 574 / 835 / 500ms | 400\* | 12 / 14 / 12 |
| B (OSIV 끔) | 3000ms | 20 / 19 / 19ms | 49 / 153 / 71ms (**71ms**) | 3.59 / 4.07 / 3.49s | 195 / 178 / 194 | 0 / 14 / 2 |

\* 미리 만든 주문 400개를 30초 안에 모두 소진함 (지연 0ms에서는 승인 수가 부하 한계를 나타내지 않음)

- 실패: A-3000ms 3회차에서 잔액 조회 1건이 k6의 TCP 연결 단계 타임아웃(`dial: i/o timeout`)으로 실패. 앱 로그에는 커넥션 풀 오류가 없었다. 나머지 실행은 실패 0건.

**읽는 법**
- PG가 3초 느려지면, OSIV를 켠 상태(A)에서는 결제와 무관한 잔액 조회의 p95가 435ms에서 6.19s로 늘었다. pending은 120개를 넘었다.
- OSIV를 끄면(B) 같은 상황에서 잔액 조회 p95는 71ms이고, pending은 거의 0이다. 승인 p95(약 3.5s)는 PG 지연 3초에 수렴한다.
- 30초간 승인 수도 A가 100~110건, B가 178~195건이다. A는 커넥션 10개가 각각 3초 넘게 묶여 풀이 상한이었다. B는 VU 20개가 3초씩 기다리는 부하기 쪽이 상한이다.

## 근거: 스레드 덤프

A, PG 지연 3000ms 부하 중 `jstack`으로 요청 스레드(`http-nio-8081-exec-*`)를 대기 위치별로 셌다.

| 대기 위치 | 스레드 수 |
|---|---|
| `TossPaymentsClient.confirm` (토스 응답 대기) | 9 |
| `HikariPool.getConnection` (커넥션 대기) | 83 |
| 그 외 (유휴 등) | 39 |

이때 Hikari `active`는 10이었다. 토스 응답을 기다리는 동안(DB를 쓰지 않는 구간) 커넥션을 쥐고 있었다는 뜻이고, 나머지 요청은 잔액 조회까지 커넥션을 기다렸다.

## 함께 발견한 것: 같은 사용자의 충전은 줄을 선다

처음에는 사용자 한 명(paytest01)으로 모든 승인을 보냈다. 이 경우 지연 0ms에서도 잔액 조회 p95가 10.39s, 승인이 초당 3.2건이었다. 덤프를 보면 커넥션을 쥔 스레드 대부분이 `AccountRepositoryImpl.insertIfAbsent`(`INSERT … ON DUPLICATE KEY UPDATE`)에서 같은 계좌 행의 락을 기다렸다.

같은 계좌의 잔액 갱신을 직렬화하는 것은 의도한 동작이다. 한 사용자가 동시에 20건을 충전하는 상황은 현실적이지 않아서, 이후 측정은 주문마다 다른 사용자를 쓰도록 바꿨다. 다만 락을 기다리는 동안에도 커넥션을 쥐고 있으므로, 같은 계좌로 요청이 몰리면 풀 전체가 영향을 받는다는 점은 남는다.

## 변경과 영향

- `application.properties`에 `spring.jpa.open-in-view=false`
- OSIV를 끄면 트랜잭션 밖(컨트롤러·응답 변환)에서 지연 로딩하던 코드가 `LazyInitializationException`으로 깨질 수 있다. 확인한 범위는 다음과 같다.
  - 서비스 public 메서드는 대부분 `@Transactional` 안에서 DTO로 변환한다 (코드 리딩)
  - 컨트롤러가 엔티티를 직접 응답하는 곳은 없다. `AuthController`의 refresh 흐름은 `RefreshToken.user`가 `@OneToOne`(기본 EAGER)이라 트랜잭션 밖 접근이 안전하다
  - 전체 테스트 108개 통과. 다만 테스트는 결제·지갑 위주라, 매칭·게임메이트 화면 API는 코드 리딩으로만 확인했다

## 측정하지 않은 것

- Tomcat 스레드 고갈: 이번 부하(승인 동시 20개)에서 B는 스레드가 남았다. 느린 승인이 200개 넘게 동시에 들어오는 상황은 측정하지 않았다. 필요해지면 PG 호출 동시 수 제한(bulkhead)과 read-timeout(현재 30s)을 함께 검토한다.
- 운영 환경(EC2·RDS) 수치. 모든 수치는 로컬 한 대에서 잰 상대 비교다.

## 재현 방법

```sh
# 1. 측정용 사용자 100명 (paytest01 행을 복사, 비밀번호 동일)
#    컬럼 목록은 users 테이블에서 users_id·login_id를 뺀 나머지
INSERT INTO users (login_id, <나머지 컬럼>)
SELECT CONCAT('perf', LPAD(n, 3, '0')), <나머지 컬럼> FROM users
JOIN (WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 100) SELECT n FROM s) t
WHERE login_id = 'paytest01';

# 2. 가짜 토스
docker run -d --name gamescord-pg-mock -p 8089:8080 wiremock/wiremock:3.13.2

# 3. 앱 (DB 접속 정보는 환경변수, TOSS_SECRET_KEY는 아무 값)
java -jar build/libs/gamescord-0.0.1-SNAPSHOT.jar --server.port=8081 --spring.profiles.active=perf
#    A 조건은 끝에 --spring.jpa.open-in-view=true 추가

# 4. 부하 (Git Bash에서는 MSYS_NO_PATHCONV=1 필요)
docker run --rm -v "$(pwd)/perf/k6:/scripts" -e PG_DELAY_MS=3000 grafana/k6:0.54.0 run /scripts/pg-latency.js
```

환경변수: `PG_DELAY_MS`(기본 3000), `CONFIRM_VUS`(20), `BALANCE_RPS`(20), `DURATION`(30s), `ORDER_COUNT`(400), `USER_COUNT`(100)
