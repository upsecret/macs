# MACS System

**Micro Application Configuration System** — Spring Cloud Gateway를 중심으로 인증·라우팅·권한·관측(observability)을 한 번에 다루는 내부 플랫폼. 모든 엔드 사용자 트래픽은 portal(Nginx) → gateway를 거치며, gateway 라우팅·권한·OpenAPI 문서는 런타임에 portal UI에서 관리한다.

---

## 1. 아키텍처 개요

```
         ┌──────────────┐
 Browser │  portal      │  React + Nginx (:3000)
   │     │   (SPA)      │  - 정적 자산 서빙
   │     └──────┬───────┘  - /api, /v3, /swagger-ui, /webjars → gateway 로 reverse proxy
   ▼            │ same-origin
┌──────────────────────────────────────────────────────────────┐
│  gateway-service  (:8080)                                    │
│  Spring Cloud Gateway                                        │
│  - static routes: auth-route, admin-route, *-api-docs        │
│  - dynamic routes: PROPERTIES 테이블 (Spring Cloud Config)   │
│  - HeaderValidationFilter (global) : app_name / employee_number 검사 │
│  - AuthValidationGatewayFilterFactory (per-route): JWT 검증  │
│  - swagger-ui 통합: /swagger-ui/index.html                   │
└──┬──────────────────────────┬────────────────────────────┬───┘
   │                          │                            │
   ▼                          ▼                            ▼
┌──────────────┐   ┌────────────────────┐      ┌──────────────────┐
│ auth-server  │   │   admin-server     │      │ (dynamic routes) │
│   (:9000)    │   │     (:8888)        │      │  RMS / EES / ... │
│  WebFlux     │   │    Spring MVC      │      └──────────────────┘
│              │   │                    │
│ - /api/auth/ │   │ - Config Server    │
│   token      │   │   (PROPERTIES 테이블)│
│ - /api/auth/ │   │ - /api/admin/      │
│   validate   │   │   permissions,    │
│              │   │   connectors,     │
│              │   │   audit, etc.     │
└──────┬───────┘   └────────┬───────────┘
       │                    │
       │   REST (fetch)     │ JPA / r2dbc
       └──────► admin ◄─────┘
              permissions
                  │
                  ▼
       ┌─────────────────────┐
       │ Oracle (:1521)      │
       │ FREEPDB1 / MACS     │
       │  - PROPERTIES       │
       │  - PERMISSION       │
       │  - CONNECTOR        │
       │  - AUDIT_LOG        │
       └─────────────────────┘

Infra: Redis(rate-limit, refresh), Kafka(Spring Cloud Bus),
Observability: OTel Collector → APM Server → Elasticsearch → Kibana
```

### 트래픽 흐름

1. **로그인**: 브라우저가 portal(nginx)에 `/api/auth/token` POST → nginx가 gateway로 프록시 → gateway가 `auth-route`로 라우팅 → auth-server가 admin-server의 `/api/admin/permissions/users/{app}/{emp}`로 권한을 조회하고 JWT를 발급. 응답 body에 `permissions: [{system,connector,role}]` 포함.
2. **일반 API**: 브라우저가 `Authorization: Bearer <token>`과 함께 호출 → gateway의 해당 라우트 필터 체인 실행. 라우트에 `AuthValidation` 필터가 있으면 auth-server `/api/auth/validate` 호출 → JWT claim의 `permissions` 중 `connector == {filter.arg.connector}` 있는지 확인 → 통과/403.
3. **Swagger**: `/swagger-ui/index.html`에 접속하면 gateway가 집계한 urls 리스트를 보여줌. 각 스펙은 `/v3/api-docs/{name}` 경로. OpenAPI 문서 자체의 `servers` 필드가 `[{url:"/"}]`로 고정되어 있어 "Try it out" 요청이 same-origin으로 발사됨 → CORS 없음.

---

## 2. 서비스별 역할

| 서비스 | 포트 | 스택 | 책임 |
|---|---|---|---|
| **portal** | 3000 | React Router 7 + Tailwind 4 + Nginx | SPA UI 서빙 및 동일 origin 리버스 프록시 (모든 `/api`, `/v3`, `/swagger-ui`, `/webjars`, `/actuator`를 gateway로 전달). 로그인, 권한/라우트/커넥터 관리 화면 제공. |
| **gateway-service** | 8080 | Spring Cloud Gateway (WebFlux) | 엣지 라우터. 정적 라우트는 `application.yml`, 동적 라우트는 `PROPERTIES` 테이블(Spring Cloud Config)에서 로드. 전역 필터로 헤더 검증·요청 로깅, 네임드 필터로 `AuthValidation` 제공. Swagger-UI 집계. Redis rate limiter. |
| **auth-server** | 9000 | Spring WebFlux (no DB) | JWT 발급·검증만 담당. 토큰 발급 시 admin-server에 REST로 권한을 가져와 claim에 embed. 검증 시 JWT만 파싱하고 요구되는 `connector`가 claim에 있는지 확인. |
| **admin-server** | 8888 | Spring Cloud Config Server + Spring MVC (JPA/JDBC) | 런타임 설정과 메타데이터의 단일 소유자.<br>• **Config Server**: `PROPERTIES` 테이블을 gateway에 서빙 (spring-cloud-config-server).<br>• `/api/admin/permissions`: 권한 CRUD.<br>• `/api/admin/connectors`: 논리적 커넥터(메타데이터) CRUD.<br>• `/api/config/routes`, `/api/config/properties`: 동적 라우트·프로퍼티 CRUD.<br>• `/api/admin/audit`: 운영 로그. |
| **oracle** | 1521 | Oracle 23 Free | 단일 데이터 저장소. 스키마 `MACS`. |
| **redis** | 6379 | Redis 7 | gateway의 rate limiter 상태 저장. |
| **kafka** | 9092/29092 | Kafka 7 (KRaft) | Spring Cloud Bus 브로드캐스트 — PROPERTIES 변경 시 gateway에 refresh 이벤트 전달. |
| **elasticsearch / kibana / apm-server / otel-collector / elastic-agent** | 9200 / 5601 / 8200 / 4317 / - | Elastic 8.17 + OTel Contrib | 관측 스택. 각 Spring Boot 앱은 OTel Java agent로 APM 트레이스·메트릭·로그를 OTel Collector로 보내고, Collector가 APM Server를 거쳐 Elasticsearch에 저장. Kibana에서 조회. |

### 데이터 모델 (핵심)

- `PROPERTIES(APPLICATION, PROFILE, LABEL, PROP_KEY, PROP_VALUE)` — Spring Cloud Config 저장소. `gateway-service` 앱의 `spring.cloud.gateway.routes[*]` 및 `springdoc.swagger-ui.urls[*]` 프로퍼티가 여기 들어간다.
- `PERMISSION(APP_NAME, EMPLOYEE_NUMBER, SYSTEM, CONNECTOR, ROLE)` — (사용자 × 논리 connector) → role 부여. PK = 앞 4개 컬럼. 토큰 발급 시 이 행들이 JWT claim에 embed.
- `CONNECTOR(ID, TITLE, DESCRIPTION, TYPE, CREATED_AT)` — gateway 라우트 위에 얹히는 메타데이터. `ID = gateway route id`. 활성 상태는 해당 route가 현재 라우트 목록에 있는지로 runtime 파생.
- `AUDIT_LOG(...)` — 운영 로그.

---

## 3. 빌드 · 실행

### 요구사항

- Docker 20.10+ / Docker Compose v2
- (선택) JDK 21 + Gradle 8 — 로컬에서 개별 서비스 빌드 시. 컨테이너 빌드는 내부에서 gradle을 실행하므로 로컬 JDK 없이도 가능.

### 사전 준비 — `libs/` (오프라인 빌드 자산)

서비스 Dockerfile 들은 빌드 단계에서 다음 두 파일을 참조한다:

- `libs/gradle-8.14-bin.zip` (≈132 MB)
- `libs/opentelemetry-javaagent.jar` (≈24 MB)

저장소 크기를 줄이기 위해 두 파일은 **git 추적 대상이 아니다**. `docker compose build` 전에 사내 미러(또는 외부 다운로드)에서 두 파일을 받아 `libs/` 에 두어야 한다.

```bash
# 사내 배포 예시
mkdir -p libs
cp /sas/mirror/macs/libs/gradle-8.14-bin.zip libs/
cp /sas/mirror/macs/libs/opentelemetry-javaagent.jar libs/
```

> 과거 커밋(`ee4c5d6`)에는 LFS 포인터가 남아있지만, repo 루트 `.lfsconfig` 의 `fetchexclude = libs/**` 설정으로 clone/fetch 시 binary 가 다운로드되지 않는다. 사내 git 서버에서 처음 clone 받을 때 `git lfs install` 이후에도 156MB 가 받아지지 않는다면 정상.

### 전체 기동

```bash
# 빌드 + 기동 (첫 실행)
docker compose up -d --build

# 다시 실행 (이미지 재사용)
docker compose up -d

# 특정 서비스만 재빌드·배포
docker compose build admin-server auth-server gateway-service portal
docker compose up -d admin-server auth-server gateway-service portal

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
| Gateway Swagger UI | http://localhost:3000/swagger-ui/index.html |
| Kibana | http://localhost:5601 (elastic / elastic_password) |
| Oracle | localhost:1521/${ORACLE_SERVICE_NAME:-FREE} (macs / macs_password) |

### 환경변수 (`.env`)

`.env.example` 을 `.env` 로 복사해 환경별 값을 조정한다.

- `ORACLE_SERVICE_NAME` — Oracle 리스너에 등록된 서비스명. 기본 `FREE`. 컨테이너가 PDB(`FREEPDB1`)를 등록하는 환경이면 `FREEPDB1` 로 override.
- `MACS_BOOTSTRAP_ADMIN_EMPLOYEE_NUMBER` — 초기 admin 사번(기본 `2078432`). admin-server 기동 시 PERMISSION 테이블에 자동 UPSERT.
- `MACS_BOOTSTRAP_ADMIN_ENABLED` — bootstrap 끄려면 `false`.

---

## 4. Portal 사용 방법

### 4.1 로그인

1. `http://localhost:3000` 접속 → 자동으로 `/login`으로 이동
2. **사번** 입력 (초기 seed: `2078432` = admin, `2065162` = user)
3. "로그인" 클릭 → 성공 시 `/connector`로 이동

로그인이 되지 않고 다시 `/login`으로 튕기는 경우는 `PERMISSION` 테이블에 해당 사번이 등록되지 않은 경우다.

- 기본 admin(`2078432`)은 admin-server 기동 시 `PermissionBootstrapRunner`가 자동으로 UPSERT하므로 볼륨을 재사용해도 복구된다. 혹시 로그에 `Permission bootstrap: inserted portal/2078432` 가 없고 `already present`도 없다면 bootstrap 설정을 확인(`macs.bootstrap.admin.*`).
- 다른 사번을 초기 admin 으로 쓰려면 `.env`에 `MACS_BOOTSTRAP_ADMIN_EMPLOYEE_NUMBER=사번` 설정 후 admin-server 재시작.
- 그 외 사번은 `auth-manage` 페이지나 직접 DB INSERT 로 추가.

### 4.2 페이지 및 권한

사이드바 메뉴는 사용자의 최고 권한 role을 기준으로 필터링된다.

| 페이지 | 경로 | admin | user |
|---|---|:---:|:---:|
| 커넥터 연동 | `/connector` | ✓ | ✓ |
| 권한관리 | `/auth-manage` | ✓ | ✗ |
| 경로설정 | `/route-config` | ✓ | ✗ |
| 설정정보 | `/settings` | ✓ | ✗ |

`user` role이 `/auth-manage` 등의 URL로 직접 진입해도 `ProtectedRoute`가 `/connector`로 리다이렉트한다.

### 4.3 "커넥터 연동" (`/connector`)

논리적 커넥터의 카드형 목록. 활성/비활성은 해당 route가 현재 gateway에 등록되어 있는지로 판정된다.

- **카드 클릭** → 상세 뷰로 전환. system / connector / type / URI / description 표시 + Gateway Swagger iframe으로 해당 route의 OpenAPI 문서 렌더.
- **등록하기** (admin만): 현재 등록된 gateway route 중 아직 커넥터 메타데이터가 없는 것을 드롭다운에서 선택 → title, description, type (agent / api / mcp) 입력.
- **편집 / 삭제** (admin만, 상세 뷰에서): 메타데이터만 수정/삭제됨. gateway route 자체는 건드리지 않는다.

### 4.4 "경로 설정" (`/route-config`)

Gateway 동적 라우트의 CRUD. 여기서 저장하는 라우트는 `PROPERTIES` 테이블에 들어가고 Spring Cloud Bus 이벤트를 통해 gateway에 반영된다.

폼 필드:

- **ID** — gateway route id. 이 값이 `AuthValidation` 필터 등에서 참조될 수 있다.
- **URI** — 타겟 백엔드 (예: `http://my-service:8080`)
- **Order** — 낮을수록 우선순위 높음
- **Predicates** — `Path`, `Method`, `Host`, `Header`, `Query`, `Cookie`, `After`, `Weight` 중 선택
- **Filters** — `StripPrefix`, `RewritePath`, `AddRequestHeader`, ..., `RequestRateLimiter`, `AuthValidation` 등. `AuthValidation`은 `connector` 인자를 받는다 (아래 참조).
- **Gateway Swagger에 등록** (체크박스) — 켜두면 저장 시 자동으로 `{id}-api-docs` 동반 라우트와 `springdoc.swagger-ui.urls[*]` 엔트리가 함께 생성되어 `/swagger-ui/index.html`의 드롭다운에 새 스펙이 등장한다. 백엔드가 `/v3/api-docs`를 제공하지 않으면 꺼두는 걸 권장.

변경 사항을 즉시 gateway에 전파하려면 우측 상단의 **"Config 전파"** 버튼 (Spring Cloud Bus refresh)을 누른다. `springdoc.swagger-ui.urls` 같은 일부 설정은 bean rebind가 보장되지 않아 `docker compose restart gateway-service` 가 필요할 수 있다.

### 4.5 "권한 관리" (`/auth-manage`)

`PERMISSION` 테이블의 CRUD + 토큰 발급 디버그 도구.

**사용자 권한 조회**
- App Name (기본 `portal`) + Employee Number 입력 → "조회" 클릭
- 결과 테이블에 해당 사용자의 (system, connector, role) 행 표시. 각 행에 삭제 버튼.

**권한 부여**
- App Name, Employee Number 입력
- System (자유 텍스트, 예: `common`)
- Connector 드롭다운 — 현재 존재하는 gateway route id 목록에서 선택
- Role: `admin` / `operator` / `user` / `viewer`
- "권한 부여" 클릭 → `POST /api/admin/permissions`

**토큰 발급 (디버그)**
- 임의의 (app_name, employee_number)에 대해 JWT를 즉시 발급받는다. 현재 로그인 세션에는 영향 없음 (interceptor를 우회하는 raw axios 사용).
- 결과 영역에 JWT 원문, permissions 테이블, JWT payload decode 결과 표시. 복사 버튼으로 토큰을 클립보드에 복사 가능.

### 4.6 "설정 정보" (`/settings`)

`PROPERTIES` 테이블의 일반 프로퍼티 CRUD. gateway 이외의 서비스용 설정(e.g. `application.yml` 오버라이드)을 여기서 관리한다.

---

## 5. 설정 방법

### 5.1 첫 기동 — 초기 권한 부여

초기 admin 사번(`2078432`)은 두 경로로 보장된다:

1. **Seed (`infra/oracle/init.sql`)** — 빈 oracle 볼륨에 대해 최초 기동 시 실행. 재실행에 안전한 `MERGE` 로 작성되어 있다.
2. **`PermissionBootstrapRunner` (admin-server)** — 기동 시마다 PERMISSION 테이블을 체크해 누락 시 UPSERT. 볼륨 재사용으로 seed가 스킵되어도 동작한다. 설정 기본값:
   ```
   macs.bootstrap.admin.enabled=true
   macs.bootstrap.admin.employee-number=2078432
   macs.bootstrap.admin.app-name=portal
   ```
   `.env`로 override 가능 (`MACS_BOOTSTRAP_ADMIN_*`).

추가 사용자(예 `2065162`=user)는 init.sql seed 또는 `/auth-manage` 페이지에서 부여. 라이브 DB에 직접 넣으려면:

```bash
docker exec -i macs-oracle bash -c "sqlplus -S macs/macs_password@localhost:1521/$ORACLE_SERVICE_NAME" <<'SQL'
INSERT INTO PERMISSION (APP_NAME, EMPLOYEE_NUMBER, SYSTEM, CONNECTOR, ROLE)
  VALUES ('portal', '9999999', 'common', 'portal', 'admin');
COMMIT;
SQL
```

### 5.2 새 백엔드 서비스를 gateway에 붙이기

1. **`/route-config` 페이지에서 새 라우트 생성**
   - `id`: 예 `rms-service` (= gateway route id. `PERMISSION.connector` 및 `CONNECTOR.id` 에서도 이 값을 쓴다면 일치시킬 것)
   - `uri`: `http://rms-service:8080`
   - Predicate: `Path=/api/rms/**`
   - Filter: `StripPrefix=2` (`/api/rms/foo` → `/foo`)
   - Filter: `AuthValidation` (arg `connector=portal` 또는 다른 논리 connector 이름)
   - "Gateway Swagger에 등록" 체크 (해당 서비스가 `/v3/api-docs`를 제공할 때만)
2. **저장** → PROPERTIES에 행이 쓰이고 Spring Cloud Bus가 gateway에 refresh 이벤트를 전송.
3. **권한 부여**: `/auth-manage`에서 대상 사용자에게 (`system`, `connector=portal`, `role=...`) 부여. `AuthValidation` 필터의 `connector` arg와 일치해야 함.
4. **커넥터 카드 등록** (선택): `/connector`에서 "등록하기" → 방금 만든 route id 선택 → title/description/type 입력. 이후 사용자들은 이 카드에서 Swagger를 바로 볼 수 있다.

### 5.3 `AuthValidation` 필터 설계 팁

`AuthValidation` 필터는 `connector` arg를 받아 JWT의 `permissions` claim 안에 `connector == <arg>`인 항목이 하나라도 있는지 검사한다.

- `- AuthValidation=portal` — shorthand (arg 한 개)
- 전체 형식:
  ```yaml
  - name: AuthValidation
    args:
      connector: portal
  ```
- `connector` arg를 생략하면 현재 route id로 fallback — 즉 사용자의 `PERMISSION.connector`가 route id와 정확히 같아야 한다. 세분화된 접근 제어가 필요할 때 유용.

**코스트 고려**: 이 필터는 매 요청마다 auth-server로 HTTP 호출한다 (gateway 컨테이너 내부 통신). DB 조회는 아니다 — JWT 파싱 + claim 검사만.

### 5.4 Swagger CORS

OpenAPI 문서의 `servers` 필드를 `[{url:"/"}]`로 고정하는 것이 CORS 문제의 표준 해결책이다. admin-server와 auth-server는 이미 이 패턴을 적용한 상태 (`config/OpenApiConfig.java` 참고). 새 백엔드 서비스를 추가할 때도 동일 패턴을 따를 것:

```java
@Bean
public OpenAPI openAPI() {
    return new OpenAPI()
            .info(new Info().title("My Service API").version("1.0"))
            .servers(List.of(new Server().url("/")));
}
```

### 5.5 Observability

모든 Spring Boot 애플리케이션 컨테이너는 OTel Java agent를 주입받아 구동된다. 기본 설정으로 트레이스·메트릭·로그가 `otel-collector:4317` (gRPC OTLP)로 송신된다. Kibana (`http://localhost:5601`)에서 "Observability → APM"으로 이동하면 `admin-server`, `auth-server`, `gateway-service` 서비스가 나타난다.

### 5.6 설정 변경 전파 (Spring Cloud Bus)

`/route-config` 또는 `/settings`에서 변경한 뒤 "Config 전파" 버튼을 누르면 admin-server가 `RefreshRemoteApplicationEvent`를 Kafka에 발행한다. 모든 Spring Cloud Bus 구독자(gateway-service)가 메시지를 받고 `@ConfigurationProperties` bean을 rebind한다.

주의: `spring.cloud.gateway.routes`는 정상적으로 rebind되지만, 일부 bean(`SwaggerUiConfigProperties` 등)은 springdoc 버전에 따라 refresh에 반응하지 않을 수 있다. 확실한 방법은 gateway 재시작:

```bash
docker compose restart gateway-service
```

---

## 6. 디렉토리 레이아웃

```
macs-system/
├─ admin-server/            # Config Server + 관리 API (JPA, JDBC)
│  └─ src/main/java/com/macs/adminserver/
│     ├─ audit/             # AUDIT_LOG CRUD
│     ├─ config/            # OpenApiConfig
│     ├─ connector/         # 논리 커넥터 메타데이터
│     ├─ permission/        # PERMISSION CRUD + auth-server용 조회
│     └─ property/          # PROPERTIES (routes, generic props) CRUD
├─ auth-server/             # JWT 발급·검증 (WebFlux, no DB)
│  └─ src/main/java/com/macs/authserver/
│     ├─ config/            # WebClientConfig (admin-server)
│     ├─ controller/        # /api/auth/token, /api/auth/validate
│     ├─ dto/
│     └─ service/           # AuthTokenService, AuthValidationService, AdminPermissionClient
├─ gateway-service/         # Spring Cloud Gateway
│  └─ src/main/java/com/macs/gateway/
│     ├─ config/            # HeaderKeyResolver, WebClientConfig
│     └─ filter/            # Header / AuthValidation / RequestResponseLogging
├─ portal/                  # React Router 7 SPA + Nginx
│  └─ app/
│     ├─ components/        # Sidebar, TopBar, ProtectedRoute, ConnectorFormModal
│     ├─ hooks/useAuth.ts
│     ├─ routes/            # login, connector, auth-manage, route-config, settings, _layout, _index
│     ├─ stores/authStore.ts
│     ├─ types/
│     └─ utils/             # api (axios), permissions
├─ shared/common-logging/   # 공통 로깅 설정 (log4j2 JSON)
├─ infra/oracle/init.sql    # 스키마 + seed 데이터
├─ monitoring/              # OTel / ES / Kibana / APM / Elastic Agent config
├─ scripts/                 # verify-system.sh, verify-otel.sh
└─ docker-compose.yml
```

---

## 7. 트러블슈팅

| 증상 | 원인 / 해결 |
|---|---|
| 로그인 성공 후 바로 /login으로 돌아감 | 로그인 후 첫 페이지 API가 403 → axios 인터셉터가 강제 로그아웃. `PERMISSION` 테이블에 해당 사용자가 누락되었거나, 해당 route의 `AuthValidation` connector가 사용자 permission과 일치하지 않음. |
| /api/admin/* 호출이 401 | `Authorization: Bearer ...` 헤더 누락. portal의 axios interceptor가 자동 주입하지만 raw fetch로 호출한 경우 직접 넣어야 함. |
| /api/admin/* 호출이 400 "Missing required header: app_name" | gateway의 `HeaderValidationFilter` 가 전역으로 `app_name` + `employee_number` 헤더를 요구. |
| 커넥터 페이지에서 "등록하기" 버튼이 안 보임 | 현재 사용자의 role이 `admin`이 아니거나, `PERMISSION` row가 없어 `permissions` 배열이 비어있음. |
| 새 route 저장했는데 gateway가 못 알아봄 | "변경사항 반영" 버튼을 누르지 않았거나, Kafka/Spring Cloud Bus가 떠있지 않음. gateway는 부팅 시 PROPERTIES 테이블에서 전체 route 를 주입받는 단일 소스 구조이므로, 버튼 클릭 후 `curl http://localhost:8080/actuator/gateway/routes`로 반영 확인. 확실한 해결: `docker compose restart gateway-service`. |
| admin/auth-server 부팅 실패 `ORA-12514` (listener does not currently know of service) | `.env`의 `ORACLE_SERVICE_NAME`이 Oracle 리스너에 등록되지 않은 값. `docker exec macs-oracle lsnrctl status`로 실제 서비스명 확인 후 `.env`를 맞춘다(보통 `FREE` 또는 `FREEPDB1`). |
| 로그인 시 403 `No permissions for 2078432 in portal` | `PERMISSION` 테이블에 해당 사번 row 없음. admin-server 로그에서 `Permission bootstrap:` 메시지 확인. 없으면 `macs.bootstrap.admin.enabled=true`인지 확인하고 admin-server 재시작. |
| ORA-18716 ("not in any time zone.DATE") | Hibernate가 Oracle 23 JDBC에서 `TIMESTAMP` 컬럼을 `OffsetDateTime`으로 읽으려고 할 때 발생. 엔티티 필드는 `LocalDateTime`으로 유지할 것. |
| Swagger-UI "Try it out"에서 CORS 오류 | 해당 백엔드 서비스의 `OpenAPI` bean에 `.servers(List.of(new Server().url("/")))` 가 누락. |

---

## 8. 관련 설계 문서

- 권한 모델 재설계 경위와 현재 흐름: `git log --grep="auth"` 참조
- 커넥터 레지스트리 도입: `git log --grep="connector"` 참조
- 지난 변경사항 요약은 commit `93c0523` 메시지 참고

---

## 9. TODO

권한 체계 v2 (token에서 권한 분리, validate에서 체크) 도입 후 남은 후속 작업.

- [ ] **Role 체계 enforce** — 현재 `PERMISSION.ROLE` 컬럼은 `admin`/`viewer`/`operator` 값을 저장만 하고 gateway/auth-server 의 권한 검사에서는 무시한다(connector 매칭만 수행). role별 허용 동작(읽기/쓰기 등)을 정의하고 `AuthValidationService` 의 매칭 로직에 role 체크를 추가할 것. 정책이 정해지면 `ROUTE_MIN_ROLE` 기반의 portal 사이드바 가드도 업데이트.
- [ ] **Gateway → auth-server 검증 호출 캐시** — 모든 보호 route 요청마다 gateway 가 auth-server `/api/auth/validate` 를 1회 호출한다(현재 의도적으로 캐시 없음). 운영 트래픽 측정 후 `(token+app_name+connector → allowed)` 키로 짧은 TTL(예: Caffeine 30s) 캐시 도입을 검토. 캐시 도입 시 권한 변경 즉시 반영을 위한 invalidation 경로(예: `/api/admin/permissions` 변경 → Spring Cloud Bus 이벤트 → gateway 캐시 초기화) 도 함께 설계.
- [ ] **토큰의 app_name 격리 정책 결정** — 현재 JWT 에는 `employee_number` 만 들어 있어, 한 토큰을 여러 client_app 에서 재사용할 수 있다(권한 체크는 매번 호출 측 `app_name` 헤더 기준). 보안 정책상 토큰을 app 단위로 격리해야 한다면 `/api/auth/token` 에서 `app_name` 헤더를 검증·기록하고 `/validate` 에서 토큰 발급 당시 app_name 과 헤더 app_name 일치 검사를 추가할 것.
