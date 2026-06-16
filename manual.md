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

## A-2. MCP 서버 등록 (커넥터연동 → 권한관리 → 도구 테스트)

### ① 커넥터연동 — MCP 서버 등록
**커넥터연동** → **+ 등록** 모달:
- **Type**: `mcp` (선택 시 MCP 전용 필드로 전환)
- **ID**: `dummy-mcp`
- **Display Name**: `Dummy MCP Server`
- **Endpoint URL**: `http://dummy-mcp-server:8765/mcp`
- **Transport**: `streamable-http`
- **Authentication**: `none` (또는 `bearer` → Bearer Token 입력)
- **System**: `common` → **저장**

### ② 권한관리 — MCP 권한 부여
**권한관리** → **권한 부여**:
- **Connector**: `mcp:dummy-mcp` 직접 입력 (route 가 아니므로 드롭다운 대신 타이핑)
- 나머지(사번/Client App/System/Role) 동일 → **권한 부여**

> MCP 권한 connector 규칙은 `mcp:{서버ID}`.

### ③ 커넥터연동 상세 — 도구 테스트
**커넥터연동** 목록에서 해당 MCP 클릭 → 상세 패널에서 `tools/list` 조회 및 `tools/call` 실행(예: `add` 도구). 권한이 없으면 403.

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

## 2. MCP 서버 등록 (레지스트리 → 권한)

MCP 서버는 게이트웨이 라우트가 아니라 **별도 레지스트리(`MCP_SERVER`)** 이며 JSON-RPC 프록시로 호출된다.
호출 시 `connector = mcp:{서버id}` 권한을 확인한다 (API 커넥터와 동일한 토큰/헤더/권한 모델).

### 2-1. MCP 서버 등록

```bash
curl -s -X POST http://localhost:8080/api/admin/mcp/servers \
  -H "Content-Type: application/json" \
  -d '{
    "id": "dummy-mcp",
    "name": "Dummy MCP Server",
    "description": "echo, add",
    "endpointUrl": "http://dummy-mcp-server:8765/mcp",
    "transport": "streamable-http",
    "authType": "none",
    "system": "common"
  }'
```

- `authType`: `none` | `bearer` (`bearer` 면 `authToken` 필수 — MCP 서버로의 인증 토큰)
- `transport`: `streamable-http`

### 2-2. 권한 부여 (connector = `mcp:{id}`)

```bash
curl -s -X POST http://localhost:8080/api/admin/permissions \
  -H "Content-Type: application/json" \
  -d '{"appName":"portal","employeeNumber":"2078432","system":"common","connector":"mcp:dummy-mcp","role":"admin"}'
```

### 2-3. (선택) 커넥터 메타 노출

UI 목록에 MCP 를 노출하려면 type=mcp 커넥터 메타를 추가할 수 있다. 단 현재 `ConnectorService` 는
동일 id 의 게이트웨이 라우트를 요구하므로, 메타 노출이 필요하면 별도 처리/라우트가 필요하다.
**권한 게이트는 메타와 무관하게 `mcp:{id}` PERMISSION 으로 동작**한다.

### 2-4. 호출 & 검증

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/token \
  -H "app_name: portal" -H "employee_number: 2078432" -H "Content-Type: application/json" \
  -d '{"employee_number":"2078432","client_app":"portal"}' | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# 도구 목록
curl -s http://localhost:8080/api/admin/mcp/servers/dummy-mcp/tools \
  -H "app_name: portal" -H "employee_number: 2078432" -H "Authorization: Bearer $TOKEN"

# 도구 호출 (add 2+3)
curl -s -X POST http://localhost:8080/api/admin/mcp/servers/dummy-mcp/tools/call \
  -H "app_name: portal" -H "employee_number: 2078432" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"add","arguments":{"a":2,"b":3}}'
# → { "content":[{"type":"text","text":"Sum = 5"}], "error":false, ... }
```

- 권한 있음 → **200**
- 권한 없음 → **403**
- 토큰 없음/claim 불일치 → **401**

> 게이트 대상: `tools/list`, `tools/call`. 서버 등록/수정/삭제(CRUD)·health probe 는 관리(admin) 동작으로 현재 게이트 대상이 아니다.

---

## 3. 빠른 비교

| | API 서버 | MCP 서버 |
|---|---|---|
| 저장 | 게이트웨이 라우트(`PROPERTIES`) + `CONNECTOR` 메타 | `MCP_SERVER` 레지스트리 |
| 호출 경로 | `/api/<path>/**` (게이트웨이 라우팅) | `/api/admin/mcp/servers/{id}/tools/*` (JSON-RPC 프록시) |
| 권한 connector | route id (= AuthValidation connector) | `mcp:{id}` |
| 권한 적용 지점 | 라우트의 `AuthValidation` 필터 | `McpController` → auth-server `/validate` 호출 |
| 보안 모델 | 토큰 + claim↔헤더 일치 + PERMISSION | **동일** |

---

## 4. 관리(admin/config) 엔드포인트 보호

`/api/admin/**`, `/api/config/**` 는 권한·라우트를 **변경**할 수 있으므로 `AdminAccessFilter` 가
**유효 토큰 + admin role** 을 요구한다. (포털 axios 인터셉터가 토큰·헤더를 자동 주입하므로 admin 사용자는 그대로 동작.)

| 경로 | 정책 |
|---|---|
| `/api/admin/**`, `/api/config/**` (관리) | 토큰 + **admin role** 필수. 없으면 401, admin 아니면 403 |
| `GET /api/admin/permissions/users/{app}/{emp}` | 본인 토큰(헤더=경로 일치)이면 admin 불필요(로그인용). 또는 내부 시크릿(S2S) |
| `/api/admin/mcp/servers/*/tools`, `/tools/call` | admin gate 제외 → `mcp:{id}` 커넥터 권한으로 게이팅 |

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

`scripts/e2e-permission-test.sh` 가 위 전 과정을 자동 검증한다:
헤더 필수 · client_app 발급/검증 · 권한 allow/deny · claim 불일치 차단 · 유량제어 ·
MCP 권한 게이트 · admin/config 보호 (총 25 케이스).

```bash
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build auth-server gateway-service dummy-api-server
bash scripts/e2e-permission-test.sh
```
