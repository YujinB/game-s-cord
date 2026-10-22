// PG(토스) 승인이 느려질 때 결제와 무관한 API까지 영향을 받는지 측정한다.
// 승인 부하(confirm)와 잔액 조회(balance)를 동시에 보내고, Hikari 풀 상태를 함께 기록한다.
// 실행 방법과 조건은 backend/perf/README.md
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Gauge, Trend } from 'k6/metrics';
import exec from 'k6/execution';

const APP = __ENV.APP_URL || 'http://host.docker.internal:8081';
const PG_ADMIN = __ENV.PG_ADMIN_URL || 'http://host.docker.internal:8089';
const PG_DELAY_MS = Number(__ENV.PG_DELAY_MS || 3000);
const CONFIRM_VUS = Number(__ENV.CONFIRM_VUS || 20);
const BALANCE_RPS = Number(__ENV.BALANCE_RPS || 20);
const DURATION = __ENV.DURATION || '30s';
const ORDER_COUNT = Number(__ENV.ORDER_COUNT || 400);
// 같은 사용자의 충전은 계좌 행 락으로 직렬화되므로(의도된 동작) 주문마다 다른 사용자를 쓴다
const USER_COUNT = Number(__ENV.USER_COUNT || 100);
const LOGIN_PREFIX = __ENV.LOGIN_PREFIX || 'perf'; // perf001 ~ perf100 (README의 준비 SQL)
const LOGIN_PWD = __ENV.LOGIN_PWD || 'paytest1234';
const PACKAGE_ID = 1; // P1: 4,680원

const hikariActive = new Gauge('hikari_active');
const hikariPending = new Gauge('hikari_pending');
const hikariPendingTrend = new Trend('hikari_pending_samples');

export const options = {
  setupTimeout: '120s',
  scenarios: {
    confirm: {
      executor: 'constant-vus', vus: CONFIRM_VUS, duration: DURATION,
      exec: 'confirm', startTime: '0s',
    },
    balance: {
      executor: 'constant-arrival-rate', rate: BALANCE_RPS, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 50, maxVUs: 400, exec: 'balance', startTime: '0s',
    },
    pool: {
      executor: 'constant-vus', vus: 1, duration: DURATION, exec: 'pool', startTime: '0s',
    },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  // 시나리오별로 나눠 보기 위해 태그 기준 임계값을 둔다 (값 자체는 판정용이 아니라 요약 출력용)
  thresholds: {
    'http_req_duration{scenario:balance}': ['max>=0'],
    'http_req_failed{scenario:balance}': ['rate>=0'],
    'http_req_duration{scenario:confirm}': ['max>=0'],
    'http_req_failed{scenario:confirm}': ['rate>=0'],
    'iterations{scenario:confirm}': ['count>=0'],
    'hikari_pending_samples': ['max>=0'],
  },
};

const json = { headers: { 'Content-Type': 'application/json' } };
const auth = (token) => ({ headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } });

export function setup() {
  // 가짜 토스: 승인 API가 PG_DELAY_MS 뒤에 200을 돌려준다
  http.del(`${PG_ADMIN}/__admin/mappings`);
  const mapping = http.post(`${PG_ADMIN}/__admin/mappings`, JSON.stringify({
    request: { method: 'POST', urlPath: '/v1/payments/confirm' },
    response: { status: 200, fixedDelayMilliseconds: PG_DELAY_MS,
      headers: { 'Content-Type': 'application/json' }, body: '{"status":"DONE"}' },
  }), json);
  if (mapping.status !== 201) throw new Error(`WireMock 설정 실패: ${mapping.status}`);

  const tokens = [];
  for (let u = 1; u <= USER_COUNT; u++) {
    const loginId = LOGIN_PREFIX + String(u).padStart(3, '0');
    const login = http.post(`${APP}/api/users/login`, JSON.stringify({ loginId, loginPwd: LOGIN_PWD }), json);
    if (login.status !== 200) throw new Error(`로그인 실패(${loginId}): ${login.status}`);
    tokens.push(login.json('accessToken'));
  }

  // 승인할 주문을 미리 만든다 (부하 구간에 주문 생성 트래픽이 섞이지 않게). 연속한 주문은 서로 다른 사용자
  const orders = [];
  for (let i = 0; i < ORDER_COUNT; i++) {
    const token = tokens[i % USER_COUNT];
    const res = http.post(`${APP}/api/toss/create-order`, JSON.stringify({ packageId: PACKAGE_ID }), auth(token));
    if (res.status !== 200) throw new Error(`주문 생성 실패: ${res.status}`);
    orders.push({ token, orderId: res.json('orderId'), amount: res.json('amount') });
  }
  return { tokens, orders };
}

export function confirm(data) {
  const i = exec.scenario.iterationInTest;
  if (i >= data.orders.length) { sleep(1); return; } // 주문 소진 시 대기 (ORDER_COUNT를 늘려야 함)
  const order = data.orders[i];
  const res = http.post(`${APP}/api/toss/confirm`, JSON.stringify({
    paymentKey: `perf-${order.orderId}`, orderId: order.orderId, amount: order.amount,
  }), Object.assign(auth(order.token), { tags: { name: 'confirm' } }));
  check(res, { 'confirm 200': (r) => r.status === 200 });
}

export function balance(data) {
  const token = data.tokens[exec.scenario.iterationInTest % data.tokens.length];
  const res = http.get(`${APP}/api/coins/balance`, Object.assign(auth(token), { tags: { name: 'balance' } }));
  check(res, { 'balance 200': (r) => r.status === 200 });
}

export function pool() {
  const active = http.get(`${APP}/actuator/metrics/hikaricp.connections.active`, { tags: { name: 'metrics' } });
  const pending = http.get(`${APP}/actuator/metrics/hikaricp.connections.pending`, { tags: { name: 'metrics' } });
  if (active.status === 200) hikariActive.add(active.json('measurements.0.value'));
  if (pending.status === 200) {
    const v = pending.json('measurements.0.value');
    hikariPending.add(v);
    hikariPendingTrend.add(v);
  }
  sleep(0.5);
}
