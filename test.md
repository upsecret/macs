# Gateway AuthValidation + Rate Limiter 테스트 가이드

http-bin 라우트에 `AuthValidation` 필터를 걸고 권한 체계가 정상 동작하는지 end-to-end 로 검증하는 curl 명령어 모음.

**대상 라우트**
- `id: http-bin`
- `uri: https://httpbin.org`
- `predicates: Path=/get/**`
- `filters: [AuthValidation(connector=http-bin), RequestRateLimiter]`

**테스트용 계정**
- `2078432` — `http-bin` connector 권한 부여 대상 (통과 기대)
- `2065162` — 권한 없음 (차단 기대, 대조군)

---

## 0. 공통 준비 — admin 토큰 발급

```bash
TOKEN_ADMIN=$(curl -s -X POST 'http://localhost:8080/api/auth/token' \
  -H 'Content-Type: application/json' \
  -H 'app_name: portal' -H 'employee_number: 2078432' \
  -d '{"employee_number":"2078432"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
```

---

## 1. 현재 권한 상태 조회

```bash
# 2078432 권한 목록
curl -s "http://localhost:8080/api/admin/permissions/users/portal/2078432" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'app_name: portal' -H 'employee_number: 2078432' | python3 -m json.tool

# 2065162 권한 목록 (대조군 — 비어있어야 통제된 테스트)
curl -s "http://localhost:8080/api/admin/permissions/users/portal/2065162" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'app_name: portal' -H 'employee_number: 2078432' | python3 -m json.tool
```

---

## 2. 2078432 에 `http-bin` connector 권한 부여

```bash
curl -s -X POST "http://localhost:8080/api/admin/permissions" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'app_name: portal' -H 'employee_number: 2078432' \
  -H 'Content-Type: application/json' \
  -d '{
    "appName":"portal",
    "employeeNumber":"2078432",
    "system":"common",
    "connector":"http-bin",
    "role":"admin"
  }'
```

---

## 3. http-bin 라우트에 AuthValidation 필터 적용 + refresh

```bash
# 라우트 설정: AuthValidation(connector=http-bin) + RequestRateLimiter(10/s, burst 20)
curl -s -X PUT "http://localhost:8080/api/config/routes/http-bin" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'app_name: portal' -H 'employee_number: 2078432' \
  -H 'Content-Type: application/json' \
  -d '{
    "id":"http-bin",
    "uri":"https://httpbin.org",
    "predicates":[{"name":"Path","args":{"_genkey_0":"/get/**"}}],
    "filters":[
      {"name":"AuthValidation","args":{"connector":"http-bin"}},
      {"name":"RequestRateLimiter","args":{
        "redis-rate-limiter.replenishRate":"10",
        "redis-rate-limiter.burstCapacity":"20",
        "redis-rate-limiter.requestedTokens":"1",
        "key-resolver":"#{@headerKeyResolver}",
        "deny-empty-key":"false"
      }}
    ],
    "order":0,
    "registerSwagger":false
  }'

# bus refresh 로 모든 gateway 인스턴스 반영
curl -s -X POST "http://localhost:8080/api/config/properties/refresh" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'app_name: portal' -H 'employee_number: 2078432'
```

### 필터가 실제로 gateway 에 로드됐는지 확인

```bash
curl -s http://localhost:8080/actuator/gateway/routes | \
  python3 -c "
import sys,json
for r in json.load(sys.stdin):
  if r.get('route_id')=='http-bin':
    print('filters:')
    for f in r.get('filters',[]): print(' ',f[:160])"
```

기대: `AuthValidationGatewayFilterFactory` 와 `RequestRateLimiterGatewayFilterFactory` 두 줄이 보여야 함.

---

## 4. 시나리오별 테스트

### 시나리오 1 — 권한 있는 사용자 `2078432` → **200 기대**

```bash
TOKEN_2078432=$(curl -s -X POST 'http://localhost:8080/api/auth/token' \
  -H 'Content-Type: application/json' \
  -H 'app_name: portal' -H 'employee_number: 2078432' \
  -d '{"employee_number":"2078432"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -sw '\nHTTP %{http_code}\n' 'http://localhost:8080/get' \
  -H "Authorization: Bearer $TOKEN_2078432" \
  -H 'app_name: portal' -H 'employee_number: 2078432'
```

**기대 응답**: httpbin 의 echo JSON + `HTTP 200`

### 시나리오 2 — 권한 없는 사용자 `2065162` → **403 기대**

```bash
TOKEN_2065162=$(curl -s -X POST 'http://localhost:8080/api/auth/token' \
  -H 'Content-Type: application/json' \
  -H 'app_name: portal' -H 'employee_number: 2065162' \
  -d '{"employee_number":"2065162"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -sw '\nHTTP %{http_code}\n' 'http://localhost:8080/get' \
  -H "Authorization: Bearer $TOKEN_2065162" \
  -H 'app_name: portal' -H 'employee_number: 2065162'
```

**기대 응답**: `{"error":"Forbidden","message":"Access denied to http-bin"}` + `HTTP 403`

### 시나리오 3 — Authorization 헤더 없음 → **401 기대**

```bash
curl -sw '\nHTTP %{http_code}\n' 'http://localhost:8080/get' \
  -H 'app_name: portal' -H 'employee_number: 2078432'
```

**기대 응답**: `{"error":"Unauthorized","message":"Missing or invalid Authorization header"}` + `HTTP 401`

### 시나리오 4 — 잘못된/위조 토큰 → **401 기대**

```bash
curl -sw '\nHTTP %{http_code}\n' 'http://localhost:8080/get' \
  -H 'Authorization: Bearer fake.garbage.token' \
  -H 'app_name: portal' -H 'employee_number: 2078432'
```

**기대 응답**: `{"error":"Unauthorized","message":"Token validation failed"}` + `HTTP 401`

---

## 5. 서비스 로그에서 판정 추적

```bash
# auth-server: ALLOW / DENY 결정 로그
docker logs --since 3m macs-auth-server 2>&1 | \
  grep -E 'Validate (ALLOW|DENY)|Token validation failed|Permissions received' | tail -10

# gateway: AuthValidationGatewayFilterFactory 의 Auth denied 로그
docker logs --since 3m macs-gateway-service 2>&1 | \
  grep -E 'Auth denied|Auth validation error' | tail -10
```

### Elasticsearch 에서 한 요청의 전체 request/response body 조회

```bash
# 1) 가장 최근 http-bin 요청의 trace_id 가져오기
TRACE=$(curl -s -u elastic:elastic_password \
  'http://localhost:9200/.ds-logs-generic.otel-default-*/_search' \
  -H 'Content-Type: application/json' \
  -d '{
    "query":{"bool":{"must":[
      {"term":{"scope.name":"com.macs.gateway.filter.RequestResponseLoggingFilter"}},
      {"prefix":{"body.text":">>> GET /get"}}
    ]}},
    "sort":[{"@timestamp":"desc"}],
    "size":1,
    "_source":["trace_id"]
  }' | python3 -c "import sys,json; print(json.load(sys.stdin)['hits']['hits'][0]['_source']['trace_id'])")

echo "trace_id=$TRACE"

# 2) 해당 trace 의 모든 로그 라인 (요약 + 헤더 + body) timestamp 순
curl -s -u elastic:elastic_password \
  'http://localhost:9200/.ds-logs-generic.otel-default-*/_search' \
  -H 'Content-Type: application/json' \
  -d "{
    \"query\":{\"term\":{\"trace_id\":\"$TRACE\"}},
    \"sort\":[{\"@timestamp\":\"asc\"}],
    \"size\":50,
    \"_source\":[\"body.text\"]
  }" | python3 -c "
import sys,json
for h in json.load(sys.stdin)['hits']['hits']:
    print(h['_source']['body']['text'][:300])"
```

---

## 6. (필요 시) 원상복구

```bash
# 2078432 의 http-bin 권한 삭제
curl -s -X DELETE "http://localhost:8080/api/admin/permissions?appName=portal&employeeNumber=2078432&system=common&connector=http-bin" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'app_name: portal' -H 'employee_number: 2078432'

# http-bin 라우트에서 AuthValidation 제거, 원래의 타이트한 rate limiter 로 복구
curl -s -X PUT "http://localhost:8080/api/config/routes/http-bin" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'app_name: portal' -H 'employee_number: 2078432' \
  -H 'Content-Type: application/json' \
  -d '{
    "id":"http-bin",
    "uri":"https://httpbin.org",
    "predicates":[{"name":"Path","args":{"_genkey_0":"/get/**"}}],
    "filters":[
      {"name":"RequestRateLimiter","args":{
        "redis-rate-limiter.replenishRate":"1",
        "redis-rate-limiter.burstCapacity":"10",
        "redis-rate-limiter.requestedTokens":"5",
        "key-resolver":"#{@headerKeyResolver}",
        "deny-empty-key":"false"
      }}
    ],
    "order":0,
    "registerSwagger":false
  }'

curl -s -X POST "http://localhost:8080/api/config/properties/refresh" \
  -H "Authorization: Bearer $TOKEN_ADMIN" \
  -H 'app_name: portal' -H 'employee_number: 2078432'
```

---

## 요약 매트릭스 — 반드시 통과해야 할 결과

| # | 시나리오 | 필요 헤더 | 기대 HTTP | 기대 본문 |
|---|---|---|---|---|
| 1 | 권한 있는 사용자 (2078432) | `Bearer <token>` + `app_name` + `employee_number` | **200** | httpbin echo JSON |
| 2 | 권한 없는 사용자 (2065162) | 동일 (토큰만 다름) | **403** | `Access denied to http-bin` |
| 3 | Authorization 없음 | `app_name` + `employee_number` 만 | **401** | `Missing or invalid Authorization header` |
| 4 | 위조 토큰 | `Bearer fake.garbage.token` | **401** | `Token validation failed` |

---

## 판정 체인 참고 (시나리오 2 기준)

```
curl /get + Bearer <2065162 token>
  └─ gateway: HeaderValidationFilter (app_name/employee_number 검사) → 통과
     └─ gateway: AuthValidationGatewayFilterFactory
        └─ POST http://auth-server:9000/api/auth/validate
             headers: Authorization: Bearer <token>
             body:    {"app_name": "portal", "connector": "http-bin"}
           └─ auth-server: JWT 서명/만료 검증 OK → employee_number=2065162 추출
              └─ admin-server: GET /api/admin/permissions/users/portal/2065162 → []
                 └─ AuthValidationService: grants=0, connector="http-bin" 매칭 없음
                    └─ response {valid: true, allowed: false, employee_number: "2065162"}
                    └─ WARN "Validate DENY app=portal emp=2065162 connector=http-bin (grants=0)"
        └─ gateway: allowed=false → HTTP 403 + "Access denied to http-bin"
           └─ WARN "Auth denied: app_name=portal connector=http-bin route=http-bin"
```

**핵심**: `app_name` 은 요청 헤더에서, `connector` 는 필터 args (또는 route id fallback) 에서, `employee_number` 는 JWT claim 에서 나옴. 세 값의 조합으로 PERMISSION 테이블을 검색해 `connector` 가 일치하는 row 가 있으면 통과.
