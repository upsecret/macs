# MACS 운영 매뉴얼 — 커넥터/MCP 등록 & 권한 관리

> 권한 모델: **토큰(employee_number + client_app 바인딩) → 헤더 일치 검증 → PERMISSION(client_app, employee_number, connector) 매칭**.
>
> 본 매뉴얼은 두 부분으로 구성된다:
> - **Part A — UI(포털)로 설정** : 운영자용 화면 기준 가이드
> - **Part B — API(curl) 레퍼런스** : 자동화·디버깅용 상세 호출

---

# Part A. UI(포털)로 설정하기

포털 접속: `http://<게이트웨이>/` → 로그인. 사이드바 메뉴(역할에 따라 노출):

| 메뉴 | 경로 | 용도 | 필요 역할 |
|---|---|---|---|
| **커넥터연동** | `/connector` | API 커넥터 · MCP 서버 등록/관리 | user 이상 |
| **권한관리** | `/auth-manage` | 권한 부여·조회·해제, 토큰 디버그 | admin |
| **경로설정** | `/route-config` | 게이트웨이 라우트(경로·필터) 관리 | admin |
| **설정정보** | `/settings` | config 프로퍼티 관리 | admin |

> 관리 메뉴(권한관리/경로설정/설정정보)는 **role=admin** 권한 보유자에게만 노출되고, 백엔드도 admin 을 강제한다.

## A-0. 로그인

1. `/login` 에서 **사번** 입력 (예: `2078432`) → **로그인**.
2. 토큰은 `client_app=portal` 로 자동 바인딩된다.
3. `portal-route` 커넥터 권한이 있어야 진입, `role=admin` 이어야 관리 메뉴가 보인다.

## A-1. API 서버 등록 (경로설정 → 커넥터연동 → 권한관리)

### ① 경로설정 — 라우트 + 필터 생성
**경로설정** → **+ 라우트 추가** 모달:
- **Route ID**: `orders-route`
- **URI**: `http://orders-api:8080`
- **Order**: catch-all(`/**`) 라우트가 있으면 더 낮은 값(예 `-100`)
- **Predicates**: `Path` 추가 → Pattern `/api/orders/**`
- **Filters** (+ 추가로 여러 개):
  - `StripPrefix` → Parts `2`
  - `AuthValidation` → **권한 게이트** (UI 에선 인자 없음 → connector = **Route ID** 로 자동)
  - `RequestRateLimiter` → Replenish Rate / Burst Capacity (유량제어)
- **Gateway Swagger에 등록**: backing 서비스가 OpenAPI 를 제공할 때만 체크 → **생성**

### ② 커넥터연동 — 메타데이터 등록 (선택, UI 노출용)
**커넥터연동** → **+ 등록** 모달:
- **Type**: `api`
- **Gateway Route ID**: 위에서 만든 라우트 선택(드롭다운 = 아직 커넥터 없는 라우트)
- **Title**, **System** 입력 → **저장**

### ③ 권한관리 — 권한 부여
**권한관리** → 우측 **권한 부여** 폼:
- **Employee Number**: 대상 사번
- **Client App**: `portal`
- **System**: `common`
- **Connector**: 드롭다운에서 **Route ID**(`orders-route`) 선택
- **Role**: `admin`/`operator`/`user` → **권한 부여**

→ 이제 해당 사번이 그 토큰/헤더로 `/api/orders/**` 호출 가능. 권한 없으면 403, 토큰/claim 불일치 401.

## A-2. MCP 서버 등록 (경로설정 → 필터(선택) → 커넥터연동)

MCP 서버도 API 와 **동일하게 게이트웨이 라우트를 통해** 연동한다 (업스트림 endpoint 직결 아님).
라우트 규약: `Path=/mcp/{id}`, 업스트림은 라우트의 `uri`(단일 소스). 게이트웨이가 자기 라우트를
loopback 으로 통과하며 MCP Streamable HTTP 세션(initialize→`Mcp-Session-Id` 캡처→
notifications/initialized→호출)을 **자동 처리**한다.

### ① 경로설정 — MCP 라우트 생성
**경로설정** → 라우트 추가:
- **ID**: `dummy-mcp`
- **URI**: 업스트림 MCP base (예 `http://dummy-mcp-server:8765`)
- **Predicate**: `Path = /mcp/dummy-mcp`
- **Filter**: `RewritePath` (regexp `/mcp/dummy-mcp` → replacement `/mcp`) — 게이트웨이 경로를 업스트림 `/mcp` 로 매핑
- **(선택) Filter** `AuthValidation` (인자 없음 = connector 가 route id) — **게이트웨이가 사용자 권한으로 게이팅**할 때만. MCP 서버가 자체 인증(bearer)하면 걸지 않는다.

### ② 커넥터연동 — MCP 등록
**커넥터연동** → **등록하기**:
- **Type**: `mcp`
- **Gateway Route ID**: ① 의 라우트 선택 (`Path=/mcp/*` 라우트만 목록에 노출)
- **Display Name** / **System**
- **Transport**: `streamable-http`
- **Authentication**: `none` | `bearer` (`bearer` 면 Bearer Token 입력 → 게이트웨이가 업스트림에 주입할 서버 토큰)

### ③ 권한관리 — 권한 부여 (게이트웨이 게이팅인 경우만)
**권한관리** → **권한 부여**:
- **Connector**: route id (예 `dummy-mcp`) — API 와 동일 (`mcp:` 접두어 없음)
- 나머지(사번/Client App/System/Role) 동일 → **권한 부여**

> AuthValidation 을 걸지 않은 **자체-bearer 서버는 권한 부여 불필요**(MCP 서버가 직접 검증).

### ④ 커넥터연동 상세 — 도구 스펙 조회
**커넥터연동** 목록에서 MCP 카드 클릭 → 상세에서 도구 스펙(이름/파라미터/Input Schema)을
**조회 전용**으로 표시(실행 기능 없음). "에이전트 연동" 가이드(LangGraph·Claude MCP SDK 스니펫)도 함께 제공.

> **인증 방식 2가지**
> - **게이트웨이 게이팅**: 라우트에 `AuthValidation` + 등록 `Authentication=none` + 권한관리에서 `connector=route id` 부여 → 사용자 macs 토큰을 에이전트에 공유해 연동 (사용자별 권한 통제 가능)
> - **자체 bearer**: 라우트에 AuthValidation 없음 + 등록 `Authentication=bearer` + 토큰 → 게이트웨이가 저장 토큰을 주입, MCP 서버가 검증
> - **stateful(세션) 서버**: 위 두 방식과 무관하게 게이트웨이가 세션을 자동 처리(포털 설정 불필요)

## A-3. 권한관리 화면 요약
- **사용자 권한 조회**: Employee Number(+선택 App) → **조회**. 각 행의 휴지통 아이콘으로 **해제**.
- **권한 부여**: 위 폼.
- **토큰 발급(디버그)**: 임의 사번으로 JWT 발급 후 payload(`employee_number`,`client_app`) 확인. 현재 로그인 세션엔 영향 없음.

## A-4. 설정정보 화면
**설정정보** 에서 config 프로퍼티(application/profile/label/key/value) 를 조회·추가·수정·삭제. 게이트웨이 라우트도 내부적으로 이 프로퍼티로 저장된다.

---

# Part B. API(curl) 레퍼런스

> 게이트웨이(8080) 기준. 관리 호출은 `app_name`/`employee_number` 헤더 + admin 토큰 필요.

## 0. 공통 — 토큰 발급 & 필수 헤더

토큰은 **사번 + client_app** 에 바인딩된다. 발급 시 둘 다 필수:

```bash
curl -s -X POST http://localhost:8080/api/auth/token \
  -H "app_name: portal" -H "employee_number: 2078432" \
  -H "Content-Type: application/json" \
  -d '{"employee_number":"2078432","client_app":"portal"}'
# → { "token":"<JWT>", "employee_number":"2078432", "client_app":"portal" }
```

- `client_app` 또는 `employee_number` 누락 → **400**
- 보호된 요청은 헤더 `app_name`·`employee_number` + `Authorization: Bearer <JWT>` 필요
- 토큰 claim(`client_app`/`employee_number`) ↔ 요청 헤더가 **다르면 401** (스푸핑 차단)

권한 부여/조회/회수:

```bash
# 부여
curl -s -X POST http://localhost:8080/api/admin/permissions \
  -H "Content-Type: application/json" \
  -d '{"appName":"portal","employeeNumber":"2078432","system":"common","connector":"<CONNECTOR>","role":"admin"}'

# 사용자 권한 조회
curl -s http://localhost:8080/api/admin/permissions/users/portal/2078432

# 회수
curl -s -X DELETE "http://localhost:8080/api/admin/permissions?appName=portal&employeeNumber=2078432&system=common&connector=<CONNECTOR>"
```

---

## 1. API 서버 등록 (경로 설정 → 필터 → 커넥터 → 권한)

API 백엔드를 게이트웨이 뒤에 붙이고 권한으로 보호하는 전체 절차.

### 1-1. 게이트웨이 라우트 생성 (경로 설정 + 필터)

`POST /api/config/routes` — 생성 즉시 `RefreshRoutesEvent` 가 발행되어 반영된다.

```bash
curl -s -X POST http://localhost:8080/api/config/routes \
  -H "app_name: portal" -H "employee_number: 2078432" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "orders-route",
    "uri": "http://orders-api:8080",
    "predicates": [
      { "name": "Path", "args": { "_genkey_0": "/api/orders/**" } }
    ],
    "filters": [
      { "name": "StripPrefix", "args": { "_genkey_0": "2" } },
      { "name": "AuthValidation", "args": { "connector": "orders-route" } },
      { "name": "RequestRateLimiter", "args": {
          "redis-rate-limiter.replenishRate": "50",
          "redis-rate-limiter.burstCapacity": "100",
          "key-resolver": "#{@headerKeyResolver}",
          "deny-empty-key": "false"
      } }
    ],
    "order": 0,
    "registerSwagger": false
  }'
```

필터 설명:

| 필터 | 역할 |
|---|---|
| `StripPrefix=2` | `/api/orders/x` → 업스트림엔 `/x` 로 전달 (앞 2 세그먼트 제거) |
| `AuthValidation=<connector>` | **권한 게이트**. 토큰 검증 + claim↔헤더 일치 + PERMISSION(connector) 확인. 생략 시 `connector`=route id |
| `RequestRateLimiter` | 유량 제어. 키 = `app_name:employee_number` (`headerKeyResolver`). burst 초과 시 429 |

> ⚠️ **라우트 우선순위 주의**: `/**` 같은 catch-all 라우트가 있으면 `order` 가 같을 때(둘 다 0) 먼저 등록된 라우트가 가로챈다. 더 구체적인 라우트는 `order` 를 낮게(예: `-100`) 주거나 catch-all 을 제거할 것.

### 1-2. 커넥터 등록 (메타데이터 연동)

커넥터는 **이미 존재하는 게이트웨이 라우트 위의 메타데이터**다 (id = route id 여야 함, 없으면 400).

```bash
curl -s -X POST http://localhost:8080/api/admin/connectors \
  -H "Content-Type: application/json" \
  -d '{
    "id": "orders-route",
    "title": "주문 API",
    "description": "주문 조회/생성",
    "type": "api",
    "system": "common"
  }'
```

- `type`: `api` | `agent` | `mcp`
- 커넥터는 UI/문서(api-docs)용 메타. **권한 enforcement 자체는 라우트의 AuthValidation 필터가 담당**한다.

### 1-3. 권한 부여

`AuthValidation` 의 connector 이름과 동일하게 PERMISSION 부여:

```bash
curl -s -X POST http://localhost:8080/api/admin/permissions \
  -H "Content-Type: application/json" \
  -d '{"appName":"portal","employeeNumber":"2078432","system":"common","connector":"orders-route","role":"admin"}'
```

### 1-4. 호출 & 검증

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/token \
  -H "app_name: portal" -H "employee_number: 2078432" -H "Content-Type: application/json" \
  -d '{"employee_number":"2078432","client_app":"portal"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

curl -s http://localhost:8080/api/orders/list \
  -H "app_name: portal" -H "employee_number: 2078432" \
  -H "Authorization: Bearer $TOKEN"
```

- 권한 있음 → **200** (업스트림 응답)
- 권한 없음 → **403**
- 토큰 없음/claim 불일치 → **401**
- 헤더 누락 → **400**

---

## 2. MCP 서버 등록 (경로설정 → 필터 → 커넥터 → (게이팅 시)권한)

MCP 서버는 API 와 동일하게 **게이트웨이 라우트(`Path=/mcp/{id}`)** 를 통해 연동한다. 게이트웨이의 MCP
클라이언트는 업스트림 endpoint 가 아니라 **자기 라우트를 loopback** 으로 통과하며, MCP Streamable HTTP
**세션**(initialize→`Mcp-Session-Id` 캡처→`notifications/initialized`→method)을 자동 처리한다.

### 2-1. 라우트 생성 (공통)

```bash
curl -s -X POST http://localhost:8080/api/config/routes "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp","uri":"http://dummy-mcp-server:8765",
  "predicates":[{"name":"Path","args":{"_genkey_0":"/mcp/dummy-mcp"}}],
  "filters":[{"name":"RewritePath","args":{"regexp":"/mcp/dummy-mcp","replacement":"/mcp"}}],
  "order":0,"registerSwagger":false}'
# 게이트웨이 게이팅을 쓰려면 filters 에 {"name":"AuthValidation","args":{}} 추가 (connector=route id).
# 자체 bearer 검증 서버면 AuthValidation 을 넣지 않는다.
```

### 2-2. MCP 서버 등록 (라우트와 동일 id 필요)

```bash
curl -s -X POST http://localhost:8080/api/admin/mcp/servers "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp","name":"Dummy MCP Server","transport":"streamable-http",
  "authType":"none","system":"common"}'
# 자체 bearer 서버면: "authType":"bearer","authToken":"<서버 토큰>"
# endpointUrl 은 보내지 않는다 — 업스트림은 라우트 uri 가 단일 소스.
```

- `authType=none`: 게이트웨이 게이팅(라우트 AuthValidation)으로 보호.
- `authType=bearer`: 게이트웨이가 저장된 `authToken` 을 업스트림 `Authorization` 으로 주입 → MCP 서버가 자체 검증.

### 2-3. 권한 부여 (라우트에 AuthValidation 을 건 경우만; connector = route id)

```bash
curl -s -X POST http://localhost:8080/api/admin/permissions "${H_ADMIN[@]}" \
  -d '{"appName":"portal","employeeNumber":"2078432","system":"common","connector":"dummy-mcp","role":"admin"}'
```

### 2-4. 호출 & 검증 (포털 도구 조회와 동일 경로)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/token \
  -H "app_name: portal" -H "employee_number: 2078432" -H "Content-Type: application/json" \
  -d '{"employee_number":"2078432","client_app":"portal"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
H=(-H "app_name: portal" -H "employee_number: 2078432" -H "Authorization: Bearer $TOKEN")

curl -s http://localhost:8080/api/admin/mcp/servers/dummy-mcp/tools "${H[@]}"          # 도구 목록
curl -s -X POST http://localhost:8080/api/admin/mcp/servers/dummy-mcp/tools/call "${H[@]}" \
  -H "Content-Type: application/json" -d '{"name":"add","arguments":{"a":2,"b":3}}'    # → Sum = 5
```

- 게이트웨이 게이팅: 권한 있음 **200** / 없음 **403** / 헤더 누락 **400** / 토큰 무효 **401**
- 자체 bearer: 토큰 일치 **200** / 불일치(MCP 서버에서) **401**

> **직접 연동(에이전트/MCP 클라이언트)**: 라우트 `/mcp/{id}` 로 직접 붙어도 된다. 헤더는 `app_name`/`employee_number`
> 필수 + `Authorization`(게이팅이면 사용자 macs JWT, 자체-bearer면 서버 토큰). 세션(`Mcp-Session-Id`)은 MCP 클라이언트가 처리.

---

## 3. 빠른 비교

| | API 서버 | MCP 서버 |
|---|---|---|
| 저장 | 라우트(`PROPERTIES`) + `CONNECTOR` 메타 | 라우트(`PROPERTIES`) + `MCP_SERVER` 메타 (id 동일) |
| 호출 경로 | `/api/<path>/**` (게이트웨이 라우팅) | `/mcp/{id}` (게이트웨이 라우트). 포털은 `/api/admin/mcp/servers/{id}/tools(/call)` 프록시가 loopback 으로 그 라우트를 통과 |
| 권한 connector | route id (= AuthValidation connector) | route id (라우트 AuthValidation 적용 시) — `mcp:` 접두어 없음 |
| 권한 적용 지점 | 라우트의 `AuthValidation` 필터 | **동일**(라우트 AuthValidation) 또는 **MCP 서버 자체 bearer** |
| 세션 | — | MCP Streamable HTTP 세션을 게이트웨이가 자동 처리(stateful 지원) |
| 보안 모델 | 토큰 + claim↔헤더 일치 + PERMISSION | 동일(게이팅) 또는 MCP 서버 자체 검증(bearer) |

---

## 4. 관리(admin/config) 엔드포인트 보호

`/api/admin/**`, `/api/config/**` 는 `AdminAccessFilter` 가 보호한다. **변경은 admin, 조회는 포털 접근자**로
구분한다. (포털 axios 인터셉터가 토큰·헤더를 자동 주입.)

| 경로 | 정책 |
|---|---|
| `GET /api/admin/connectors*`, `GET /api/admin/mcp/servers*` (레지스트리 **조회**) | 토큰 + **`portal-route` 권한자**면 admin 아니어도 허용(현황 열람) |
| 그 외 `/api/admin/**`, `/api/config/**` **변경**(POST/PUT/DELETE 등) | 토큰 + **admin role** 필수. 없으면 401, admin 아니면 403 |
| `GET /api/admin/permissions/users/{app}/{emp}` | 본인 토큰(헤더=경로 일치)이면 admin 불필요(로그인용). 또는 내부 시크릿(S2S) |
| `GET /api/admin/mcp/servers/*/tools`, `/health` | 포털 접근자 조회 + 해당 라우트에 AuthValidation 이 있으면 그 connector 권한도 적용 |
| `POST /api/admin/mcp/servers/*/tools/call` | admin gate 제외 → loopback 라우트(`/mcp/{id}`)의 `AuthValidation`(또는 자체 bearer)로 게이팅 |

> 포털 인터셉터는 **401(인증 실패)에서만 로그아웃**한다. 403(인가 거부)은 세션을 유지하고 화면에 에러만 표시
> (예: 권한 없는 MCP 도구 조회 → 카드에 "권한 없음", 로그아웃 안 됨).

> admin role = 해당 사용자가 PERMISSION 에 `role=admin` 행을 하나라도 보유. (부트스트랩 2078432 가 admin)

### 내부 시크릿 (S2S 역호출 보호)

auth-server 는 토큰 검증 중 gateway `GET /api/admin/permissions/users/...` 를 역호출한다.
이 내부 호출은 `X-Internal-Secret` 헤더로 식별하며, **양 서비스가 같은 값**이어야 한다.

```yaml
# 환경변수 (gateway + auth-server 동일 값, 미설정 시 dev 기본값)
MACS_INTERNAL_SECRET=<양 서비스 동일한 무작위 값>
```

> 멀티호스트(HA) 배포 시 `JWT_SECRET` 과 함께 **두 서버 모두 동일 값**으로 주입할 것. (`docs/ha-deployment-plan.md` 참고)

---

## 5. 참고 — E2E 검증

`scripts/e2e-permission-test.sh` 가 전 과정을 자동 검증한다 (총 36 케이스):
헤더 필수 · client_app 발급/검증 · 권한 allow/deny · claim 불일치 차단 · 유량제어 ·
MCP 권한 게이트(게이트웨이 게이팅) · admin/config 보호 · **API 문서연동(swagger)** ·
**MCP 자체 bearer** · **MCP stateful 세션** · **MCP stateful+bearer**.

라우트·커넥터·권한은 init.sql 에 시드하지 않고 스크립트가 런타임에 admin API 로 주입한다(재실행 안전).

```bash
# 운영 스택 + 테스트 컨테이너(아래 §6)를 함께 기동한 뒤 실행
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build
bash scripts/e2e-permission-test.sh
# → PASS=36 FAIL=0
```

> 테스트 컨테이너는 **운영 스택(`docker-compose.yml`)에는 없다**. 평상시 `docker compose up -d` 는
> 인프라 + 앱(auth/gateway/portal)만 띄우고, 테스트할 때만 `-f docker-compose.e2e.yml` 오버레이를 추가한다.

---

## 6. 테스트용 컨테이너 (fixtures)

연동 검증용 더미 서버. MCP 더미 이미지(`infra/dummy-mcp-server`)는 두 env 플래그를 독립 지원한다:
`MCP_BEARER_TOKEN`(설정 시 자체 bearer 검증), `MCP_STATEFUL=true`(설정 시 Streamable HTTP 세션 강제 —
initialize 응답으로 `Mcp-Session-Id` 발급, 이후 요청에 없으면 400 / 모르면 404).

| 컨테이너 | 포트 | 특성 | 검증 시나리오 |
|---|---|---|---|
| `dummy-mcp-server` | 8765 | stateless, 인증 없음 | 게이트웨이 게이팅(AuthValidation + connector 권한) |
| `dummy-mcp-auth` | 8766 | `MCP_BEARER_TOKEN` | 자체 bearer 검증 (AuthValidation 없이 authType=bearer + 토큰) |
| `dummy-mcp-stateful` | 8767 | `MCP_STATEFUL` | 세션 강제 — 게이트웨이가 세션 캡처·재전송 |
| `dummy-mcp-both` | 8768 | bearer + stateful | 자체 bearer + 세션 동시 |
| `swagger-api-server` | 8090 | OpenAPI(`/v3/api-docs`) + Swagger UI(`/swagger-ui`) + 샘플 `/api/products` | API 커넥터 문서연동 (커넥터 docsUrl 비우면 `{route uri}/v3/api-docs` 자동 취득) |
| `dummy-api-server` | 8088 | echo | 헤더/StripPrefix/권한 라우팅 |

> **모든 테스트 컨테이너는 `docker-compose.e2e.yml`(오버레이)에만 정의**되며 운영 `docker-compose.yml` 에는 없다.
> 이들에 대한 라우트·커넥터·권한도 init.sql 에 시드하지 않는다 — `scripts/e2e-permission-test.sh` 가 런타임에 주입하거나,
> 수동 검증 시 포털/`/api/config`·`/api/admin` API 로 직접 등록한다(본 매뉴얼 Part A/B 절차).
