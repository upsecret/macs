# MACS 이중화(HA) Docker 구축방안

> 사내 Docker 컨테이너 기반, 서버 2대 가용성 구성. 인프라는 1대(서버 A)에 구축 후 다른 서버(B)와 연동.
> 작성일: 2026-06-16

---

## 1. 목표와 범위

- **목표**: gateway-service · auth-server · portal 을 **서버 2대**에 이중화하여 앱 계층 가용성 확보.
- **인프라**: Oracle / Redis / Elasticsearch·Kibana·APM·OTel 은 **서버 A 1대에만** 구축하고, 서버 B 의 앱이 네트워크로 연동.
- **앞단**: 사내 LB(또는 keepalived VIP)로 두 서버에 트래픽 분배 + 장애 자동 제외.

---

## 2. 현황과 핵심 제약

| 구성요소 | 상태 | HA 관점 함의 |
|---|---|---|
| Oracle / Redis / ES·Kibana·APM·OTel | 인프라, **상태 보유** | 한 서버에만 둠 → 공유 백엔드 |
| auth-server | 무상태, 단 **JWT secret 공유 필요** | 두 인스턴스가 같은 secret이어야 토큰 교차검증 |
| gateway-service | 무상태, Redis(레이트리밋)·DB 의존 | Redis 공유해야 레이트리밋 카운터 일관 |
| portal | 무상태 (Next.js standalone) | 자유롭게 복제 가능 |
| 서비스 간 호출 | **URL 하드코딩** | gateway+auth+portal을 **호스트별 1세트로 동거** 필요 |

**하드코딩된 서비스 간 URL** (코드 레벨, 변경 회피 위해 동거 전제):
- `gateway-service/.../config/WebClientConfig.java` → `http://auth-server:9000`
- `auth-server/.../config/WebClientConfig.java` → `http://gateway-service:8080`

→ gateway·auth·portal 을 **호스트별 1세트**로 같은 compose 네트워크에 묶으면 컨테이너명 그대로 해석되어 **코드 변경 불필요**.

---

## 3. 토폴로지

- 서버 A = **인프라 + 앱 1세트**
- 서버 B = **앱 1세트** (인프라 엔드포인트는 서버 A 를 가리킴)
- 교차 호스트 통신은 **DB / Redis / OTel 백엔드 접근만** 발생 (앱 간 호출은 호스트 내부).

```
                    [ VIP (keepalived) + 사내 LB ]
                               │
              ┌────────────────┴────────────────┐
        ┌─────────────┐                   ┌─────────────┐
        │  서버 A      │                   │  서버 B      │
        │ ┌─────────┐ │                   │ ┌─────────┐ │
        │ │edge nginx│ │                   │ │edge nginx│ │
        │ │ / →portal│ │                   │ │ / →portal│ │
        │ │/api →gw  │ │                   │ │/api →gw  │ │
        │ ├─────────┤ │                   │ ├─────────┤ │
        │ │ portal   │ │                   │ │ portal   │ │
        │ │ gateway  │ │                   │ │ gateway  │ │
        │ │ auth     │ │                   │ │ auth     │ │
        │ └────┬────┘ │                   │ └────┬────┘ │
        │ ┌────┴─────┐│   DB/Redis/OTel   │      │       │
        │ │  INFRA   ││◄──────────────────┼──────┘       │
        │ │ Oracle   ││   (서버 A IP)      │              │
        │ │ Redis    ││                   │              │
        │ │ ES/OTel  ││                   │              │
        │ └──────────┘│                   │              │
        └─────────────┘                   └─────────────┘
```

각 호스트의 gateway 는 **자기 호스트의** auth 를, auth 는 자기 호스트의 gateway 를 호출. 인증/권한 데이터 경로:
`gateway 필터 → (로컬) auth /validate → (로컬) gateway /api/admin/permissions → Oracle(서버 A)`

---

## 4. Compose 분리 구조

현재 단일 `docker-compose.yml` 을 3개로 분리한다.

| 파일 | 배치 | 내용 |
|---|---|---|
| `docker-compose.infra.yml` | **서버 A만** | oracle, redis, elasticsearch, kibana, apm-server, otel-collector, elastic-agent, (es-setup) |
| `docker-compose.app.yml` | **A·B 둘 다** | auth-server, gateway-service, portal, edge-nginx |
| `.env` (호스트별) | 각 서버 | `INFRA_HOST`, DB/Redis 자격증명, `JWT_SECRET` 등 |

> `dummy-mcp-server` 는 테스트 픽스처이므로 운영 앱 compose 에서 제외.

### 앱 compose 환경변수 주입 (발췌)

```yaml
# docker-compose.app.yml
services:
  auth-server:
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATA_REDIS_HOST: ${INFRA_HOST}
      JWT_SECRET: ${JWT_SECRET}                 # ← 양 서버 동일값 필수
      OTEL_EXPORTER_OTLP_ENDPOINT: http://${INFRA_HOST}:4317

  gateway-service:
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_R2DBC_URL: r2dbc:oracle://${INFRA_HOST}:1521/FREE
      SPRING_R2DBC_USERNAME: ${DB_USERNAME}
      SPRING_R2DBC_PASSWORD: ${DB_PASSWORD}
      SPRING_DATA_REDIS_HOST: ${INFRA_HOST}
      OTEL_EXPORTER_OTLP_ENDPOINT: http://${INFRA_HOST}:4317

  edge-nginx:
    image: nginx:1.27-alpine
    ports: ["80:80"]
    volumes:
      - ./edge-nginx.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on: [portal, gateway-service]
```

### `.env` 예시

```dotenv
# 서버 A
INFRA_HOST=10.10.0.11          # 서버 A 사내 IP (또는 host-gateway)
DB_USERNAME=macs
DB_PASSWORD=<운영 비밀번호>
JWT_SECRET=<양 서버 동일한 64+ 바이트 무작위 값>
JWT_EXPIRATION=3600
MACS_INTERNAL_SECRET=<양 서버 동일한 무작위 값>   # auth↔gateway S2S 역호출 보호
```

```dotenv
# 서버 B (INFRA_HOST 만 다르고 JWT_SECRET 은 A 와 동일)
INFRA_HOST=10.10.0.11          # 서버 A IP 를 가리킴
DB_USERNAME=macs
DB_PASSWORD=<운영 비밀번호>
JWT_SECRET=<서버 A 와 동일한 값>
JWT_EXPIRATION=3600
MACS_INTERNAL_SECRET=<서버 A 와 동일한 값>   # auth↔gateway S2S 역호출 보호
```

> gateway↔auth↔portal 간 호출은 compose 내부 네트워크(컨테이너명)로 유지 → **앱 간 URL 은 env 주입 불필요**.

---

## 5. 선행 코드/설정 변경 (필수)

### 5.1 JWT secret 외부화 — **필수**
현재 `auth-server/src/main/resources/application.yml` 에 dev secret 하드코딩
(`macs-jwt-secret-key-for-development-only-change-in-production`).
환경변수로 분리하고 **양 서버에 동일 값** 주입한다.

```yaml
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:3600}
```

> 불일치 시 서버 A 발급 토큰이 서버 B 에서 401 → 로그인/요청 실패.

### 5.2 OTel 엔드포인트 외부화
`otel.exporter.otlp.endpoint` 를 `OTEL_EXPORTER_OTLP_ENDPOINT` 로 override 가능하게 통일.
(R2DBC URL, Redis Host 는 이미 env override 동작 중.)

### 5.3 변경 불필요
- 서비스 간 WebClient URL (`auth-server:9000`, `gateway-service:8080`) — 동거 구조라 그대로 유지.

---

## 6. 부하분산 / 장애전환 (LB 계층)

- **Edge nginx (호스트별 1개)**: `/ → portal:3000`, `/api/** → gateway-service:8080` 경로 라우팅.
  portal 의 `baseURL:""` (same-origin) 설계와 일치.
- **앞단 LB**:
  - 사내 L4/L7 LB 가 있으면 A·B 의 80/443 으로 분배 + health check 기반 자동 제외.
  - 없으면 **keepalived(VIP) + nginx/HAProxy** 2대 active-standby 로 LB 자체 이중화.
- **세션**: JWT 무상태 → sticky session 불필요, 라운드로빈 OK.
- **Health check 대상**: gateway `/actuator/health`, auth `/actuator/health`, portal `/`.

### edge-nginx.conf (예시)

```nginx
server {
  listen 80;

  location /api/ {
    proxy_pass http://gateway-service:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
  }

  location / {
    proxy_pass http://portal:3000;
    proxy_set_header Host $host;
  }
}
```

---

## 7. 보안 (교차 호스트 백엔드 노출)

서버 A 의 Oracle(1521) · Redis(6379) · OTel(4317) 이 네트워크에 노출됨 → **방화벽으로 서버 B IP 만 허용**.

- **Redis**: 현재 비밀번호 없음 → `requirepass` 설정 + B IP 만 허용 **(필수)**.
- **Oracle**: 강한 패스워드 + B IP 만 허용.
- **OTel(4317)**: B IP 만 허용.
- `.env` / secret 은 git 제외(`.gitignore` 반영) + 최소 파일권한, 가능하면 사내 secret 관리 사용.

---

## 8. 가용성 한계 (반드시 인지)

**인프라가 서버 A 단독 = 단일 장애점(SPOF).**
서버 A 장애 시 서버 B 의 앱이 살아 있어도 Oracle/Redis 접근 불가로 **전체 다운**.
이 설계는 *앱 계층 무중단 배포 · 앱 인스턴스 장애 대응* 에는 유효하나 *인프라 장애* 에는 무방비.
SLA 기준으로 수용 여부 판단 필요. 단계적 완화책:

| 단계 | 개선 | 효과 |
|---|---|---|
| 0 (현재) | 인프라 A 단독 | 앱 HA 만 확보 |
| 1 | Oracle 정기 백업 + B 에 cold standby, Redis AOF 백업 | RPO/RTO 단축 |
| 2 | Oracle Data Guard(standby) + Redis replica/Sentinel | 인프라 장애전환 |
| 3 | 인프라도 2노드 클러스터 | 진정한 HA |

> **더 나은 대안**: 인프라를 self-host 하지 않고 **사내 기존 인프라(HA 운영 중)** 에 연결하면 이 SPOF 자체가 사라진다 → **§11 참고(권장)**.

---

## 9. 구축 순서

1. **선행 변경**: JWT secret · OTel 엔드포인트 외부화 → 이미지 재빌드 후 사내 레지스트리 push (`scripts/build-and-push-*.bat` 활용).
2. **서버 A 인프라**: `docker-compose.infra.yml` 기동 → Oracle `init.sql` 적용 · 모든 health green 확인.
3. **서버 A 앱**: `.env`(INFRA_HOST=A) 작성 → `docker-compose.app.yml` 기동.
4. **서버 B 앱**: `.env`(INFRA_HOST=A IP, **동일 JWT_SECRET**) 작성 → 서버 A 방화벽 오픈 후 `docker-compose.app.yml` 기동.
5. **LB 연결**: 양 호스트 edge nginx → 앞단 LB/VIP 등록, health check 구성.
6. **검증**:
   - A·B 각각 로그인 → 토큰 발급 정상.
   - **서버 A 발급 토큰을 서버 B 로 검증**(교차검증) 성공 — JWT secret 동기화 확인.
   - 레이트리밋 카운터가 A·B 간 공유되는지 확인(공유 Redis).
   - OTel 트레이스/메트릭 수집 확인.
   - `scripts/verify-system.sh` 재활용.

---

## 10. 산출물 체크리스트

- [ ] `docker-compose.infra.yml` (서버 A)
- [ ] `docker-compose.app.yml` (A·B 공용)
- [ ] `.env.example` + 호스트별 `.env`
- [ ] `edge-nginx.conf`
- [ ] JWT secret · OTel 엔드포인트 외부화 코드/yml 수정
- [ ] (LB 자체 이중화 시) keepalived / HAProxy 설정
- [ ] 방화벽 규칙(서버 B → 서버 A: 1521/6379/4317)
- [ ] Redis `requirepass` · Oracle 운영 패스워드 적용

---

## 11. 사내 기존 인프라 연동 (self-host 대신) ★권장

Oracle/Redis/Elastic 을 docker 로 직접 띄우지 않고 **이미 사내에 운영 중인 인프라**에 연결하는 방식.
이러면 **§8 의 SPOF 가 사라진다** (사내 인프라가 이미 HA/백업 운영되므로). 앱 컨테이너만 배포하면 된다.

### 11.1 핵심 원리

앱은 인프라를 **컨테이너명**(`oracle`, `redis`, `otel-collector`)이 아니라 **환경변수**로 찾는다.
따라서 `docker-compose.infra.yml` 을 **아예 띄우지 않고**, `docker-compose.app.yml` 의 env 만
사내 엔드포인트로 치환하면 된다. 앱 간 호출(`auth-server:9000` 등)은 호스트 내부라 그대로 유지.

> 모든 앱 호스트(A·B)에 동일하게 적용. `depends_on: oracle/redis/...` 항목은 외부 인프라엔 의미 없으니
> 앱 compose 에서 **제거**하고, 앱의 자체 health check + 재시도에 맡긴다.

### 11.2 인프라별 연결 설정

| 인프라 | 사용 서비스 | 환경변수 | 예시(사내 엔드포인트) |
|---|---|---|---|
| **Oracle** | gateway (·auth) | `SPRING_R2DBC_URL` | `r2dbc:oracle://oracle.corp:1521/ORCLPDB1` |
| | | `SPRING_R2DBC_USERNAME` / `SPRING_R2DBC_PASSWORD` | 사내 DBA 발급 계정 |
| | | `SPRING_R2DBC_PROPERTIES_DEFAULTSCHEMA` | 앱 스키마명 (기본 `MACS`) |
| **Redis** | gateway, auth | `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | `redis.corp` / `6379` |
| | | `SPRING_DATA_REDIS_PASSWORD` | 사내 Redis 비밀번호 |
| | | `SPRING_DATA_REDIS_SSL_ENABLED` | TLS 사용 시 `true` |
| **Elastic(관측)** | gateway, auth | `OTEL_EXPORTER_OTLP_ENDPOINT` | `https://apm.corp:8200` (또는 사내 OTel collector) |
| | | `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` 또는 `http/protobuf` |
| | | `OTEL_EXPORTER_OTLP_HEADERS` | `Authorization=Bearer <APM 토큰>` (인증 시) |

> portal 은 인프라 의존이 없다(앱 호출만). 위 env 는 gateway/auth 에만 주입.

### 11.3 Oracle — 스키마 프로비저닝

`infra/oracle/init.sql` 은 일회용 컨테이너 전용(`CREATE USER macs` + `GRANT DBA`)이라 **그대로 쓰면 안 된다.**
사내 Oracle 에는 다음만 적용한다:

1. **DBA 가 앱 계정/스키마 생성** + 최소 권한(테이블 생성, 본 스키마 DML). DBA 권한 부여는 금지.
2. `init.sql` 에서 **DDL(테이블·인덱스) + 시드(PROPERTIES 라우트, PERMISSION, MCP_SERVER)** 부분만 추출해
   그 스키마에서 실행. (앞부분 `CREATE USER`/`GRANT DBA`/`CONNECT` 는 제외)
3. `SPRING_R2DBC_PROPERTIES_DEFAULTSCHEMA` 를 그 스키마명으로 지정.
4. 접속 문자열은 사내 표준에 맞춰: service name(`/ORCLPDB1`) 또는 SID. TLS 면 `r2dbc:oracle:thin:@tcps://...` 형태/지갑(wallet) 적용.

> 기동 시 `PermissionBootstrapRunner` 가 admin 권한을 UPSERT 하므로, 시드를 못 넣어도 admin 1행은 보정된다.
> 단 라우트(PROPERTIES) 시드는 반드시 넣어야 게이트웨이 라우팅이 동작한다.

### 11.4 Redis — 운영 모드 주의

- **단일/Sentinel/Cluster** 중 사내 구성 확인. Spring Data Redis 는 셋 다 지원하나 프로퍼티가 다르다:
  - 단일: `SPRING_DATA_REDIS_HOST/PORT/PASSWORD`
  - Sentinel: `spring.data.redis.sentinel.master` + `.nodes`
  - Cluster: `spring.data.redis.cluster.nodes`
- 게이트웨이 **레이트리밋**이 Redis 를 쓰므로, **모든 앱 인스턴스가 같은 Redis**를 봐야 카운터가 공유된다(현 설계 그대로).
- TLS/인증은 사내 정책에 맞춰 `SSL_ENABLED`/`PASSWORD` 적용.

### 11.5 Elastic 관측 — 수집기로 전송만

- 앱은 **OTel javaagent** 로 트레이스/메트릭/로그를 OTLP 로 내보낸다. 사내에 OTel collector 나
  APM Server(OTLP 수신) 가 있으면 `OTEL_EXPORTER_OTLP_ENDPOINT` 만 그쪽으로 돌리면 끝.
- 자체 `kibana`/`apm-server`/`elastic-agent`/`elasticsearch` 컨테이너는 **모두 불필요** — 사내 Elastic 이 담당.
- 프로토콜 불일치 주의: 사내 수신부가 4318(HTTP)이면 `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`,
  4317(gRPC)이면 `grpc`.
- 인증이 필요한 사내 APM 은 `OTEL_EXPORTER_OTLP_HEADERS` 로 토큰/API 키 전달.

### 11.6 네트워크 · 보안

- 앱 호스트(A·B) → 사내 인프라 엔드포인트(1521/6379/8200 등) **방화벽 허용** 필요.
- 자격증명은 **사내 secret 관리/`.env`(git 제외)** 로 주입. 이미지에 박지 말 것.
- 사내 인프라가 TLS 를 요구하면 truststore/wallet 를 컨테이너에 마운트.

### 11.7 이 방식의 배포 형태

```
              [ 사내 LB / VIP ]
                    │
        ┌───────────┴───────────┐
   서버 A (앱만)           서버 B (앱만)
   portal/gateway/auth     portal/gateway/auth
        └───────────┬───────────┘
                    ▼  (env 로 지정)
         사내 인프라 (이미 HA 운영)
       Oracle RAC · Redis Cluster · Elastic/APM
```

- `docker-compose.infra.yml` **불필요**. 양 호스트는 `docker-compose.app.yml` 만 기동.
- §4 의 `INFRA_HOST` 대신 인프라별 실제 엔드포인트를 `.env` 에 직접 지정.
- **SPOF 해소**: 인프라 HA 는 사내가 책임지므로 §8 의 단계적 완화책이 불필요해진다.

### 11.8 체크리스트 (사내 인프라 연동 시)

- [ ] DBA 로부터 Oracle 스키마/계정 발급 + 최소 권한
- [ ] `init.sql` 의 DDL+시드만 추출해 사내 스키마에 적용
- [ ] `.env`: R2DBC URL/계정/스키마, Redis host/port/password(+SSL), OTLP endpoint/protocol/headers
- [ ] 앱 compose 에서 인프라 서비스 블록 + `depends_on` 제거
- [ ] 앱 호스트 → 사내 인프라 방화벽 허용
- [ ] (TLS) truststore/wallet 마운트
- [ ] 기동 후: 로그인·라우팅·레이트리밋·트레이스 수집 검증 (`scripts/e2e-permission-test.sh`)
