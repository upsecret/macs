# MACS System

**Micro Application Configuration System** — Spring Cloud Gateway를 중심으로 인증·라우팅·권한·커넥터/MCP 레지스트리·관측(observability)을 한 번에 다루는 내부 플랫폼. 모든 엔드 사용자 트래픽은 portal(Next.js) → gateway를 거치며, gateway 라우팅·권한·커넥터·MCP 서버·OpenAPI 문서는 런타임에 portal UI에서 관리한다.

> **아키텍처 변경 이력 (PR 5)**: 기존의 별도 `admin-server`(Spring Cloud Config Server + 관리 API)는 **gateway-service로 병합**되었다. 이에 따라 **Kafka / Spring Cloud Bus / Config Server**는 더 이상 사용하지 않으며, 동적 라우트는 gateway가 **인프로세스에서 R2DBC로 PROPERTIES 테이블을 직접 로드**한다. portal은 **nginx + React Router에서 Next.js standalone**으로 교체되었고, **MCP(Model Context Protocol) 서버 레지스트리·호출 프록시**가 신규 추가되었다.

---

## 1. 아키텍처 개요

```
          ┌──────────────┐
  Browser │  portal      │  Next.js 15 (standalone, :3000)
   │      │   (App SPA)  │  - React 19 / Tailwind 4 / zustand
   │      └──────┬───────┘  - next.config rewrites: /api, /v3, /swagger-ui,
   ▼             │ same-origin   /webjars, /actuator → gateway (nginx 대체)
┌──────────────────────────────────────────────────────────────────┐
│  gateway-service  (:8080)   Spring Cloud Gateway (WebFlux) + R2DBC │
│                                                                    │
│  [엣지 라우팅]                                                     │
│   - DbRouteDefinitionRepository: PROPERTIES 테이블 → 동적 라우트   │
│   - HeaderValidationFilter (global): app_name / employee_number    │
│   - AuthValidationGatewayFilterFactory (per-route): JWT 검증       │
│   - RequestResponseLoggingFilter / Redis RateLimiter / CORS        │
│                                                                    │
│  [병합된 관리 API]  com.macs.gateway.admin.*                       │
│   - /api/admin/permissions   권한 CRUD                             │
│   - /api/admin/connectors    논리 커넥터 메타데이터 CRUD           │
│   - /api/admin/mcp           MCP 서버 레지스트리 + JSON-RPC 프록시  │
│   - /api/admin/audit         운영 로그                             │
│   - /api/config/routes       동적 라우트 CRUD (+ 로컬 refresh)     │
│   - /api/config/properties   일반 프로퍼티 CRUD                    │
│   - Swagger-UI 집계 (/swagger-ui.html)                             │
└──┬───────────────────────┬──────────────────────────┬─────────────┘
   │ JWT validate (REST)    │ R2DBC                     │ JSON-RPC / HTTP
   ▼                        ▼                           ▼
┌──────────────┐   ┌─────────────────────┐   ┌────────────────────┐
│ auth-server  │   │ Oracle (:1521)      │   │ MCP servers        │
│   (:9000)    │   │ FREE / schema MACS  │   │  dummy-mcp-server   │
│  WebFlux     │   │  - PROPERTIES       │   │   (:8765, test)     │
│              │   │  - PERMISSION       │   │  + 등록된 외부 서버 │
│ /api/auth/   │   │  - CONNECTOR        │   └────────────────────┘
│   token      │   │  - MCP_SERVER       │
│ /api/auth/   │   │  - AUDIT_LOG        │
│   validate   │   └─────────────────────┘
└──────┬───────┘            ▲
       │ permission lookup  │ R2DBC
       │ (REST → gateway)   │
       └───────► gateway ───┘   ← auth-server 는 권한 조회를
              /api/admin/          gateway 의 로컬 컨트롤러로 호출
              permissions/...      (admin-server merge 이후)

Infra: Redis(rate-limit)
Observability: 각 앱(OTel Java agent) → OTel Collector → APM Server → Elasticsearch → Kibana
```

### 트래픽 흐름

1. **로그인**: 브라우저가 portal(Next.js)에 `/api/auth/token` POST → Next rewrite가 gateway로 프록시 → gateway가 `auth-route`로 라우팅 → auth-server가 권한을 조회(`GET /api/admin/permissions/users/{app}/{emp}` — 이제 **gateway 자신의** 로컬 컨트롤러가 R2DBC로 응답)하고 JWT를 발급. 응답 body·JWT claim에 `permissions: [{system,connector,role}]` 포함.
2. **일반 API**: 브라우저가 `Authorization: Bearer <token>`과 함께 호출 → gateway의 해당 라우트 필터 체인 실행. 라우트에 `AuthValidation` 필터가 있으면 auth-server `/api/auth/validate` 호출 → JWT claim의 `permissions` 중 `connector == {filter.arg.connector}` 있는지 확인 → 통과/403.
3. **MCP 호출**: portal "커넥터 연동" 화면에서 MCP 서버 카드 선택 → gateway `/api/admin/mcp/servers/{id}/tools`(tools/list)·`/tools/call`(tools/call) → gateway가 해당 MCP endpoint로 **JSON-RPC 2.0 over HTTP(Streamable HTTP)** 프록시.
4. **Swagger**: `/swagger-ui.html`에 접속하면 gateway가 집계한 spec 드롭다운을 보여줌. `gateway-service`(병합된 admin/config 컨트롤러 포함)는 `/v3/api-docs`, `auth-server`는 `/v3/api-docs/auth-server`로 프록시 집계된다. OpenAPI `servers` 필드가 `[{url:"/"}]`로 고정되어 "Try it out"이 same-origin으로 발사됨 → CORS 없음.

---

## 2. 서비스별 역할

| 서비스 | 포트 | 스택 | 책임 |
|---|---|---|---|
| **portal** | 3000 | Next.js 15 (standalone) · React 19 · Tailwind 4 · zustand · axios | SPA UI 서빙 및 동일 origin 리버스 프록시 (`next.config.ts` rewrites가 `/api`, `/v3`, `/swagger-ui`, `/webjars`, `/actuator`를 gateway로 전달 — 기존 nginx 대체). 로그인, 권한/라우트/커넥터·MCP 관리 화면 제공. |
| **gateway-service** | 8080 | Spring Cloud Gateway (WebFlux) + Spring Data R2DBC | 엣지 라우터 **겸** 관리 백엔드. 동적 라우트는 `DbRouteDefinitionRepository`가 `PROPERTIES` 테이블에서 인프로세스 로드. 전역 필터로 헤더 검증·요청 로깅, 네임드 필터로 `AuthValidation`. Redis rate limiter. 병합된 `com.macs.gateway.admin.*` 패키지에서 permissions / connectors / mcp / audit / config(routes·properties) REST API 제공. Swagger-UI 집계. |
| **auth-server** | 9000 | Spring WebFlux (no repository) | JWT 발급·검증만 담당. 토큰 발급 시 gateway의 권한 조회 API를 REST로 호출해 claim에 embed. 검증 시 JWT만 파싱하고 요구되는 `connector`가 claim에 있는지 확인. |
| **dummy-mcp-server** | 8765 | Node.js (의존성 0, 단일 파일) | MCP 통합 검증용 테스트 픽스처. JSON-RPC 2.0 over HTTP로 `echo`·`add` 도구를 노출. gateway가 `http://dummy-mcp-server:8765/mcp`로 호출. |
| **oracle** | 1521 | Oracle 23 Free (`gvenzl/oracle-free:23-slim`) | 단일 데이터 저장소. PDB `FREE`, 스키마 `MACS`. gateway·auth가 R2DBC로 접속. |
| **redis** | 6379 | Redis 7 | gateway의 rate limiter 상태 저장. |
| **elasticsearch / kibana / apm-server / otel-collector / elastic-agent** | 9200 / 5601 / 8200 / 4317·4318 / - | Elastic 8.17 + OTel Contrib | 관측 스택. 각 Spring Boot 앱은 OTel Java agent로 트레이스·메트릭·로그를 OTel Collector로 보내고, Collector가 APM Server를 거쳐 Elasticsearch에 저장. Kibana에서 조회. `elastic-agent`는 Docker 컨테이너 로그/메트릭을 수집. |

> **참고**: 별도 `admin-server`·`config-server`·`kafka` 컨테이너는 더 이상 존재하지 않는다. 동적 라우트 갱신은 Kafka/Spring Cloud Bus 없이 gateway **인프로세스**에서 `RefreshRoutesEvent`로 처리된다(§5.6).

### 데이터 모델 (핵심)

스키마 `MACS`, 정의는 [`infra/oracle/init.sql`](infra/oracle/init.sql).

- `PROPERTIES(APPLICATION, PROFILE, LABEL, PROP_KEY, PROP_VALUE)` — 동적 라우트 저장소. `gateway-service`/`default`/`main` 키 아래 `spring.cloud.gateway.server.webflux.routes[*].*` 및 `springdoc.swagger-ui.urls[*]` 프로퍼티가 들어간다. `DbRouteDefinitionRepository`가 기동·refresh 시 이 행들을 읽어 `RouteDefinition`으로 변환.
- `PERMISSION(APP_NAME, EMPLOYEE_NUMBER, SYSTEM, CONNECTOR, ROLE)` — (사용자 × 논리 connector) → role. PK = 앞 4개 컬럼. 토큰 발급 시 이 행들이 JWT claim에 embed.
- `CONNECTOR(ID, TITLE, DESCRIPTION, TYPE, SYSTEM, DOCS_URL, CREATED_AT)` — gateway 라우트 위에 얹히는 메타데이터. `ID = gateway route id`. `TYPE ∈ {agent, api, mcp}`. `DOCS_URL`이 있으면 외부 OpenAPI를, 없으면 gateway `/v3/api-docs/{id}`를 사용. 활성 상태는 해당 route가 현재 라우트 목록에 있는지로 runtime 파생.
- `MCP_SERVER(ID, NAME, DESCRIPTION, ENDPOINT_URL, TRANSPORT, AUTH_TYPE, AUTH_TOKEN, SYSTEM, CREATED_AT)` — 등록된 MCP 서버 endpoint. gateway route와 무관하게 JSON-RPC over HTTP로 직접 호출. `TRANSPORT = streamable-http`, `AUTH_TYPE ∈ {none, bearer}`.
- `AUDIT_LOG(AUDIT_ID, OCCURRED_AT, EMPLOYEE_NUMBER, ACTION, TARGET_TYPE, TARGET_ID, RESULT, DETAIL)` — 운영 로그.
- 그 외 그룹/리소스 기반 RBAC 확장용 테이블(`SYSTEM_CONNECTOR`, `GROUP_INFO`, `GROUP_MEMBER`, `GROUP_RESOURCE`, `USER_RESOURCE`)이 스키마에 예약되어 있다(현재 API에는 미연결, 권한 모델 v2 후속용 — §9 TODO).

---

## 3. 빌드 · 실행

### 요구사항

- Docker 20.10+ / Docker Compose v2
- (선택) JDK 21 + Gradle 8 / Node 20+ — 로컬에서 개별 서비스 빌드·dev 시. 컨테이너 빌드는 내부에서 gradle/next build를 실행하므로 로컬 toolchain 없이도 가능.

### 사전 준비 — `libs/` (오프라인 빌드 자산)

Spring 서비스 Dockerfile(gateway-service / auth-server)은 빌드 단계에서 다음 두 파일을 참조한다:

- `libs/gradle-8.14-bin.zip` (≈132 MB)
- `libs/opentelemetry-javaagent.jar` (≈24 MB)

저장소 크기를 줄이기 위해 두 파일은 **git 추적 대상이 아니다**. `docker compose build` 전에 사내 미러(또는 외부 다운로드)에서 두 파일을 받아 `libs/`에 두어야 한다.

```bash
# 사내 배포 예시
mkdir -p libs
cp /sas/mirror/macs/libs/gradle-8.14-bin.zip libs/
cp /sas/mirror/macs/libs/opentelemetry-javaagent.jar libs/
```

> 과거 커밋에는 LFS 포인터가 남아있지만, repo 루트 `.lfsconfig`의 `fetchexclude = libs/**` 설정으로 clone/fetch 시 binary가 다운로드되지 않는다.

### 전체 기동

```bash
# 빌드 + 기동 (첫 실행)
docker compose up -d --build

# 다시 실행 (이미지 재사용)
docker compose up -d

# 인프라만
docker compose up -d oracle redis elasticsearch es-setup kibana apm-server otel-collector elastic-agent

# 앱만 재빌드·배포
docker compose build auth-server gateway-service dummy-mcp-server portal
docker compose up -d auth-server gateway-service dummy-mcp-server portal

# 전체 중지
docker compose down

# DB 볼륨까지 완전 삭제 (seed 재초기화가 필요할 때)
docker compose down -v
```

### 헬스 체크

```bash
docker ps --format "{{.Names}}\t{{.Status}}"
```

모든 컨테이너가 `healthy`가 되면 portal에 접속 가능.

### 접속 엔드포인트

| 용도 | URL |
|---|---|
| Portal | http://localhost:3000 |
| Gateway Swagger UI | http://localhost:3000/swagger-ui.html |
| Gateway actuator/routes | http://localhost:8080/actuator/gateway/routes |
| Kibana | http://localhost:5601 (elastic / elastic_password) |
| Oracle | localhost:1521/FREE (macs / macs_password) |
| Dummy MCP | http://localhost:8765/health |

초기 admin 사번(`2078432`)은 gateway 기동 시 [`PermissionBootstrapRunner`](gateway-service/src/main/java/com/macs/gateway/admin/permission/bootstrap/PermissionBootstrapRunner.java)가 자동 UPSERT 한다(connector=`portal-route`, role=`admin`). 다른 사번/비활성화는 gateway 환경변수 `MACS_BOOTSTRAP_ADMIN_EMPLOYEE_NUMBER`, `MACS_BOOTSTRAP_ADMIN_ENABLED`로 변경.

---

## 4. Portal 사용 방법

Next.js App Router 기반. 인증이 필요한 화면은 `app/(protected)/` 그룹 아래에 있고 `ProtectedRoute`가 가드한다.

### 4.1 로그인

1. `http://localhost:3000` 접속 → 자동으로 `/login`으로 이동
2. **사번** 입력 (초기 seed: `2078432` = admin, `2065162` = user)
3. "로그인" 클릭 → 성공 시 `/connector`로 이동

로그인이 되지 않고 다시 `/login`으로 튕기는 경우는 `PERMISSION` 테이블에 해당 사번이 등록되지 않은 경우다.

- 기본 admin(`2078432`)은 gateway 기동 시 `PermissionBootstrapRunner`가 자동으로 UPSERT하므로 볼륨을 재사용해도 복구된다. 로그에 `Permission bootstrap: ...` 메시지가 없다면 `macs.bootstrap.admin.*` 설정을 확인.
- 다른 사번을 초기 admin으로 쓰려면 `.env`에 `MACS_BOOTSTRAP_ADMIN_EMPLOYEE_NUMBER=사번` 설정 후 gateway 재시작.
- 그 외 사번은 `/auth-manage` 페이지나 직접 DB INSERT로 추가.

### 4.2 페이지 및 권한

사이드바 메뉴는 사용자의 최고 권한 role을 기준으로 필터링된다.

| 페이지 | 경로 | admin | user |
|---|---|:---:|:---:|
| 커넥터 연동 | `/connector` | ✓ | ✓ |
| 권한관리 | `/auth-manage` | ✓ | ✗ |
| 경로설정 | `/route-config` | ✓ | ✗ |
| 설정정보 | `/settings` | ✓ | ✗ |

`user` role이 `/auth-manage` 등의 URL로 직접 진입해도 `ProtectedRoute`가 `/connector`(또는 `/unauthorized`)로 리다이렉트한다.

### 4.3 "커넥터 연동" (`/connector`)

REST 커넥터와 MCP 서버를 **하나의 레지스트리**로 묶어 카드형으로 보여준다.

- **REST 커넥터 카드**: 활성/비활성은 해당 route가 현재 gateway에 등록되어 있는지로 판정. 카드 클릭 → 상세 뷰에서 system / connector / type / URI / description 표시 + Gateway Swagger(`ApiDocsViewer` / `SwaggerEmbed`)로 해당 route의 OpenAPI 문서 렌더.
- **MCP 서버 카드**: 항상 활성으로 표시. 카드 클릭 → `McpDetailPanel`이 `tools/list`로 도구 목록을 불러오고, 각 도구를 `tools/call`로 즉석 호출·결과 확인 가능.
- **등록하기** (admin만): REST는 커넥터 메타데이터가 없는 gateway route를 선택해 title/description/type(agent·api·mcp) 입력. MCP는 endpoint URL·transport·auth 정보를 입력해 `MCP_SERVER`에 등록.
- **편집 / 삭제** (admin만): 메타데이터/등록정보만 수정·삭제. gateway route 자체는 건드리지 않는다.

### 4.4 "경로 설정" (`/route-config`)

Gateway 동적 라우트의 CRUD. 저장하는 라우트는 `PROPERTIES` 테이블에 들어가고, gateway가 인프로세스 `RefreshRoutesEvent`로 즉시 반영한다.

폼 필드:

- **ID** — gateway route id. 이 값이 `AuthValidation` 필터·`CONNECTOR.id`·`PERMISSION.connector`에서 참조될 수 있다.
- **URI** — 타겟 백엔드 (예: `http://my-service:8080`)
- **Order** — 낮을수록 우선순위 높음
- **Predicates** — `Path`, `Method`, `Host`, `Header`, `Query`, `Cookie`, `After`, `Weight` 중 선택
- **Filters** — `StripPrefix`, `RewritePath`, `AddRequestHeader`, …, `RequestRateLimiter`, `AuthValidation` 등. `AuthValidation`은 `connector` 인자를 받는다(§5.3).
- **Gateway Swagger에 등록** (체크박스) — 켜두면 저장 시 `{id}-api-docs` 동반 라우트와 `springdoc.swagger-ui.urls[*]` 엔트리가 함께 생성되어 Swagger 드롭다운에 새 spec이 등장. 백엔드가 `/v3/api-docs`를 제공하지 않으면 꺼둘 것.

저장 즉시 라우트는 반영되지만, `springdoc.swagger-ui.urls` 같은 일부 설정은 bean rebind가 보장되지 않아 `docker compose restart gateway-service`가 필요할 수 있다(§5.6).

### 4.5 "권한 관리" (`/auth-manage`)

`PERMISSION` 테이블의 CRUD + 토큰 발급 디버그 도구.

**사용자 권한 조회** — App Name(기본 `portal`) + Employee Number 입력 → "조회". 결과 테이블에 (system, connector, role) 행 표시, 각 행에 삭제 버튼.

**권한 부여** — App Name, Employee Number, System(자유 텍스트), Connector(현재 gateway route id 목록에서 선택), Role(`admin`/`operator`/`user`/`viewer`) 입력 → `POST /api/admin/permissions`.

**토큰 발급 (디버그)** — 임의의 (app_name, employee_number)에 대해 JWT를 즉시 발급. 현재 로그인 세션에는 영향 없음(인터셉터를 우회하는 raw 호출). 결과 영역에 JWT 원문·permissions 테이블·payload decode 표시, 복사 버튼 제공.

### 4.6 "설정 정보" (`/settings`)

`PROPERTIES` 테이블의 일반 프로퍼티 CRUD. 라우트 이외의 설정을 여기서 관리한다.

---

## 5. 설정 방법

### 5.1 첫 기동 — 초기 권한 부여

초기 admin 사번(`2078432`)은 두 경로로 보장된다:

1. **Seed (`infra/oracle/init.sql`)** — 빈 oracle 볼륨에 대해 최초 기동 시 실행. 재실행에 안전한 `MERGE`로 작성. 2078432=admin, 2065162=user를 connector `portal-route`로 부여.
2. **`PermissionBootstrapRunner` (gateway-service)** — 기동 시마다 PERMISSION 테이블을 체크해 누락 시 UPSERT. 볼륨 재사용으로 seed가 스킵되어도 동작. 설정 기본값:
   ```
   macs.bootstrap.admin.enabled=true
   macs.bootstrap.admin.employee-number=2078432
   macs.bootstrap.admin.app-name=portal
   macs.bootstrap.admin.system=common
   macs.bootstrap.admin.connector=portal-route
   macs.bootstrap.admin.role=admin
   ```
   `.env`로 override 가능 (`MACS_BOOTSTRAP_ADMIN_*`).

추가 사용자는 init.sql seed 또는 `/auth-manage`에서 부여. 라이브 DB에 직접 넣으려면:

```bash
docker exec -i macs-oracle bash -c "sqlplus -S macs/macs_password@localhost:1521/FREE" <<'SQL'
INSERT INTO PERMISSION (APP_NAME, EMPLOYEE_NUMBER, SYSTEM, CONNECTOR, ROLE)
  VALUES ('portal', '9999999', 'common', 'portal-route', 'admin');
COMMIT;
SQL
```

### 5.2 새 백엔드 서비스를 gateway에 붙이기

1. **`/route-config`에서 새 라우트 생성**
   - `id`: 예 `rms-service` (= gateway route id. `PERMISSION.connector`·`CONNECTOR.id`에서도 이 값을 쓴다면 일치시킬 것)
   - `uri`: `http://rms-service:8080`
   - Predicate: `Path=/api/rms/**`
   - Filter: `StripPrefix=2` (`/api/rms/foo` → `/foo`)
   - Filter: `AuthValidation`(arg `connector=portal-route` 또는 다른 논리 connector)
   - "Gateway Swagger에 등록" 체크 (해당 서비스가 `/v3/api-docs`를 제공할 때만)
2. **저장** → PROPERTIES에 행이 쓰이고 gateway가 인프로세스 `RefreshRoutesEvent`로 라우트 반영.
3. **권한 부여**: `/auth-manage`에서 대상 사용자에게 (`system`, `connector`, `role`) 부여. `AuthValidation`의 `connector` arg와 일치해야 함.
4. **커넥터 카드 등록** (선택): `/connector` → "등록하기" → route id 선택 → title/description/type 입력.

MCP 서버를 추가할 때는 별도 라우트 없이 `/connector`에서 MCP 등록 폼에 endpoint URL을 입력하면 된다(`MCP_SERVER` 테이블에 저장, gateway가 JSON-RPC 프록시).

### 5.3 `AuthValidation` 필터 설계 팁

`AuthValidation` 필터는 `connector` arg를 받아 JWT의 `permissions` claim 안에 `connector == <arg>`인 항목이 하나라도 있는지 검사한다.

- `- AuthValidation=portal-route` — shorthand(arg 한 개)
- 전체 형식:
  ```yaml
  - name: AuthValidation
    args:
      connector: portal-route
  ```
- `connector` arg를 생략하면 현재 route id로 fallback — 사용자의 `PERMISSION.connector`가 route id와 정확히 같아야 함.

**코스트**: 매 보호 요청마다 gateway가 auth-server `/api/auth/validate`로 1회 HTTP 호출(컨테이너 내부 통신). DB 조회가 아니라 JWT 파싱 + claim 검사다.

### 5.4 Swagger CORS

OpenAPI 문서의 `servers` 필드를 `[{url:"/"}]`로 고정하는 것이 표준 해결책. auth-server는 이미 적용(`config/OpenApiConfig.java`). gateway 자신은 자체 spec을 `/v3/api-docs`로 노출하고, 새 백엔드 서비스를 추가할 때도 동일 패턴을 따른다:

```java
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
            .info(new Info().title("My Service API").version("1.0"))
            .servers(List.of(new Server().url("/")));
}
```

### 5.5 Observability

모든 Spring Boot 컨테이너는 OTel Java agent(`JAVA_TOOL_OPTIONS=-javaagent:...`)를 주입받아 구동된다. 트레이스·메트릭·로그가 `otel-collector:4317`(gRPC OTLP)로 송신된다. Kibana(`http://localhost:5601`)에서 "Observability → APM"으로 이동하면 `auth-server`, `gateway-service` 서비스가 나타난다. `elastic-agent`는 Docker 컨테이너 로그/메트릭을 별도로 수집한다.

### 5.6 라우트/설정 변경 반영 (Spring Cloud Bus 대체)

admin-server 병합 이후 라우트 관리 API와 gateway가 **같은 프로세스**이므로 Kafka/Spring Cloud Bus가 필요 없다. `/api/config/routes` CRUD가 처리되면 `ConfigPropertyService`가 로컬에서 `RefreshRoutesEvent`를 발행하고, Spring Cloud Gateway의 `CompositeRouteDefinitionLocator`가 `DbRouteDefinitionRepository`를 다시 읽어 라우트 테이블을 갱신한다.

확인:

```bash
curl http://localhost:8080/actuator/gateway/routes
# 또는 수동 refresh
curl -X POST http://localhost:8080/actuator/refresh
```

주의: `spring.cloud.gateway.server.webflux.routes`는 정상 갱신되지만, springdoc의 `SwaggerUiConfigProperties` 등 일부 bean은 refresh에 반응하지 않을 수 있다. 확실한 방법은 재시작:

```bash
docker compose restart gateway-service
```

---

## 6. 디렉토리 레이아웃

```
macs-system/
├─ gateway-service/          # Spring Cloud Gateway (WebFlux) + 병합된 관리 API (R2DBC)
│  └─ src/main/java/com/macs/gateway/
│     ├─ admin/
│     │  ├─ audit/           # AUDIT_LOG CRUD
│     │  ├─ connector/       # 논리 커넥터 메타데이터
│     │  ├─ mcp/             # MCP 서버 레지스트리 + JSON-RPC 프록시 (client/controller/service)
│     │  ├─ permission/      # PERMISSION CRUD + bootstrap + auth-server용 조회
│     │  └─ property/        # PROPERTIES (config routes·properties) CRUD
│     │     └─ routing/      #   DbRouteDefinitionRepository (Config Server 대체)
│     ├─ config/             # Cors / KeyResolver / Swagger / WebClient
│     └─ filter/             # AuthValidation / HeaderValidation / RequestResponseLogging
├─ auth-server/              # JWT 발급·검증 (WebFlux, repository 없음)
│  └─ src/main/java/com/macs/authserver/
│     ├─ config/             # JwtProperties / OpenApiConfig / WebClientConfig(→gateway)
│     ├─ controller/         # /api/auth/token, /api/auth/validate
│     ├─ dto/
│     └─ service/            # AuthTokenService, AuthValidationService, AdminPermissionClient
├─ portal/                   # Next.js 15 (standalone) SPA
│  ├─ app/
│  │  ├─ (protected)/        # connector, auth-manage, route-config, settings, layout
│  │  ├─ components/         # Sidebar, TopBar, ProtectedRoute, ConnectorFormModal,
│  │  │                      #   McpDetailPanel, ApiDocsViewer, SwaggerEmbed
│  │  ├─ hooks/              # useAuth, useResource
│  │  ├─ stores/authStore.ts
│  │  ├─ types/  utils/      # api(axios), permissions
│  │  ├─ login/ unauthorized/  error.tsx  layout.tsx  page.tsx
│  └─ next.config.ts         # rewrites: /api,/v3,/swagger-ui,/webjars,/actuator → gateway
├─ shared/common-logging/    # 공통 로깅 설정 (Log4j2 + OTel appender)
├─ infra/
│  ├─ oracle/init.sql        # 스키마 + seed 데이터
│  └─ dummy-mcp-server/      # MCP 테스트 픽스처 (Node, server.js)
├─ monitoring/               # OTel / ES / Kibana / APM / Elastic Agent config
├─ gradle/libs.versions.toml # 버전 카탈로그
├─ scripts/                  # verify-system.sh, verify-otel.sh, verify-portal.sh
└─ docker-compose.yml
```

빌드 모듈은 `settings.gradle.kts`에 `gateway-service`, `auth-server`, `shared:common-logging` 세 개만 포함된다.

### 기술 스택 / 버전

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.13 |
| Spring Cloud | 2025.0.2 (Gateway Server WebFlux) |
| Gradle | 8.14 |
| springdoc-openapi | 2.7.0 |
| jjwt | 0.12.6 |
| Oracle R2DBC / OJDBC | 1.2.0 / 23.5 |
| bucket4j (rate limit) | 8.14.0 |
| OpenTelemetry SDK / instrumentation | 1.43.0 / 2.16.0-alpha |
| Next.js / React | 15.x / 19.x |
| Tailwind CSS / zustand | 4.x / 5.x |
| Elastic Stack | 8.17.0 |
| Oracle | 23 Free |

---

## 7. 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| 로그인 성공 후 바로 /login으로 돌아감 | 첫 페이지 API가 403 → axios 인터셉터가 강제 로그아웃. `PERMISSION`에 사용자가 누락됐거나 route의 `AuthValidation` connector가 사용자 permission과 불일치. |
| /api/admin/* 호출이 401 | `Authorization: Bearer ...` 헤더 누락. portal의 axios interceptor가 자동 주입하지만 raw 호출 시 직접 넣어야 함. |
| /api/admin/* 호출이 400 "Missing required header: app_name" | gateway `HeaderValidationFilter`가 전역으로 `app_name` + `employee_number` 헤더를 요구. |
| 커넥터 페이지에서 "등록하기"가 안 보임 | 현재 사용자 role이 `admin`이 아니거나 `PERMISSION` row가 없어 `permissions`가 비어있음. |
| 새 route 저장했는데 gateway가 못 알아봄 | 라우트는 인프로세스 `RefreshRoutesEvent`로 즉시 반영되지만, `/actuator/gateway/routes`로 확인. 안 보이면 `curl -X POST .../actuator/refresh` 또는 `docker compose restart gateway-service`. |
| MCP 카드에서 tools/list가 비거나 오류 | 대상 MCP endpoint 연결 실패. `GET /api/admin/mcp/servers/{id}/health`(initialize 프로브)로 연결 확인. 테스트는 `dummy-mcp-server`(http://localhost:8765/health). |
| auth-server 로그인 시 403 `No permissions for ...` | `PERMISSION`에 사번 row 없음. **gateway**(병합된 admin) 로그에서 `Permission bootstrap:` 확인, 없으면 `macs.bootstrap.admin.enabled=true`인지 보고 gateway 재시작. |
| ORA-18716 ("not in any time zone.DATE") | Hibernate/매핑이 Oracle 23에서 `TIMESTAMP`를 `OffsetDateTime`으로 읽으려 할 때. 도메인 시간 필드는 `LocalDateTime` 유지. |
| Swagger-UI "Try it out" CORS 오류 | 해당 백엔드의 `OpenAPI` bean에 `.servers(List.of(new Server().url("/")))` 누락. |
| portal 컨테이너 health 실패 | alpine이 `localhost`를 IPv6(::1)로 해석 → Next standalone(IPv4 bind)에 미도달. healthcheck는 `127.0.0.1` 명시 사용. |

---

## 8. 관련 설계 문서 / 자료

- 아키텍처 의도·대안 비교: [`comparison.md`](comparison.md)
- MCP 통합 계획: [`mcp-plan.md`](mcp-plan.md)
- 초기 구현 플랜(역사적): 루트 [`../plan.md`](../plan.md) — admin-server 병합 **이전** 구상이므로 현재 구조와 다름.
- 검증 스크립트: [`scripts/verify-system.sh`](scripts/verify-system.sh), [`scripts/verify-otel.sh`](scripts/verify-otel.sh), [`scripts/verify-portal.sh`](scripts/verify-portal.sh)

---

## 9. TODO

권한 체계 v2(token에서 권한 분리, validate에서 체크) 도입 후 남은 후속 작업.

- [ ] **Role 체계 enforce** — 현재 `PERMISSION.ROLE`은 저장만 하고 권한 검사(connector 매칭)에서는 무시된다. role별 허용 동작을 정의하고 `AuthValidationService` 매칭에 role 체크를 추가, portal 사이드바 가드도 업데이트.
- [ ] **그룹/리소스 RBAC 연결** — `GROUP_INFO`·`GROUP_MEMBER`·`GROUP_RESOURCE`·`USER_RESOURCE`·`SYSTEM_CONNECTOR` 테이블이 스키마에 예약돼 있으나 API에 미연결. 그룹 기반 권한 부여 모델로 확장 시 gateway admin 모듈에 repository/controller 추가.
- [ ] **Gateway → auth-server 검증 호출 캐시** — 모든 보호 route 요청마다 `/api/auth/validate` 1회 호출(현재 의도적 무캐시). 운영 트래픽 측정 후 `(token+app_name+connector → allowed)` 키로 짧은 TTL 캐시 검토. 도입 시 권한 변경 즉시 반영 invalidation 경로도 함께 설계.
- [ ] **토큰의 app_name 격리 정책 결정** — JWT에 `employee_number`만 들어 있어 한 토큰을 여러 client_app에서 재사용 가능(권한 체크는 호출 측 `app_name` 헤더 기준). app 단위 격리가 필요하면 `/token` 발급 시 app_name 검증·기록 + `/validate`에서 일치 검사 추가.
- [ ] **MCP 인증 강화** — `MCP_SERVER.AUTH_TYPE`이 `none`/`bearer`만 지원. 사내 MCP 서버 연동 시 토큰 보관(현재 평문 컬럼) 암호화 및 추가 transport·인증 방식 검토.
- [ ] **`macs` DB 사용자의 DBA 권한 분리** — PoC 편의로 `init.sql`이 `GRANT DBA TO macs`를 부여한다. 운영 전 최소 권한(`CREATE SESSION/TABLE/VIEW/SEQUENCE/INDEX`, `UNLIMITED TABLESPACE`)으로 분리.
- [ ] **Gateway CORS 정책 좁히기** — `config/CorsConfig.java`가 origin/method/header 전부 와일드카드 + `allowCredentials=true`(PoC 편의). 운영 전 허용 origin 화이트리스트 + 필요한 메서드/헤더만 명시.
```
