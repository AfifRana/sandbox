import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  vus: __ENV.K6_VUS ? parseInt(__ENV.K6_VUS) : 10,
  duration: __ENV.K6_DURATION || '30s',
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.01']
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max']
};

const GATEWAY = __ENV.GATEWAY_URL || 'http://localhost:30000';
const USER = __ENV.K6_USERNAME || 'alice';
const PASS = __ENV.K6_PASSWORD || 'password';
const CUSTOMER_ID = __ENV.CUSTOMER_ID || '11111111-1111-1111-1111-111111111111';
const CREATE_ORDER = __ENV.K6_CREATE_ORDER === 'true';

function login() {
  const payload = JSON.stringify({ username: USER, password: PASS });
  const res = http.post(`${GATEWAY}/auth/login`, payload, { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'login: status 200': (r) => r.status === 200 });
  if (res.status !== 200) return null;
  const body = res.json();
  return body.accessToken || '';
}

export function setup() {
  const token = login();
  if (!token) {
    throw new Error('Unable to obtain an access token for the load test');
  }
  return { token };
}

export default function (data) {
  const token = data.token;

  const authHeaders = { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` } };

  if (CREATE_ORDER) {
    const order = {
      customerId: CUSTOMER_ID,
      lines: [{ productId: __ENV.PRODUCT_ID || '22222222-2222-2222-2222-222222222222', quantity: 1, unitPrice: 89.99 }]
    };
    const resCreate = http.post(`${GATEWAY}/api/v1/orders`, JSON.stringify(order), authHeaders);
    check(resCreate, { 'create order: 200|201': (r) => r.status === 200 || r.status === 201 });
  }

  // Fetch orders by customer
  const resGet = http.get(`${GATEWAY}/api/v1/orders?customerId=${CUSTOMER_ID}`, authHeaders);
  check(resGet, { 'get orders: 200': (r) => r.status === 200 });

  sleep(1);
}
