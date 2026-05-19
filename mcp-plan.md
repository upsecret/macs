# MACS 시스템 MCP 지원 플랜

> **문서 버전**: v1.0
> **작성일**: 2026-05-19
> **대상**: MACS 백엔드 / 프론트엔드 / 인프라 팀
> **목적**: 기존 REST API(Swagger 기반) 연동에 더해 MCP(Model Context Protocol) 서버 연동을 MACS에 도입하기 위한 아키텍처·UI·운영 플랜 정의

---

## 1. 배경 및 문제 정의

### 1.1 현재 구조 (REST API / Swagger)

MACS는 외부 서비스 연동 시 Swagger(OpenAPI 3.x) 문서를 입력으로 받아, 다음 정보를 정적으로 파싱하여 UI를 자동 생성하고 있다.

- `paths` → 호출 가능한 엔드포인트 목록
- `operation.parameters` / `requestBody` → 입력 폼
- `responses` → 응답 스키마
- `components.securitySchemes` → 인증 설정

한 번 URL 등록 → 문서 fetch → 통합 UI 자동 생성, 이라는 단순한 모델이다.

### 1.2 MCP 도입의 필요성

MCP는 LLM·에이전트 친화적 도구 노출 표준(JSON-RPC 2.0 기반)으로, 자사 외부 서버 및 서드파티 도구 생태계가 빠르게 MCP로 이동하고 있다. MACS가 REST 외에 MCP도 1급(first-class) 연동 채널로 지원하면 다음을 얻는다.

- **자동 도구 발견**: 서버 측에서 도구 정의를 런타임에 노출 → 변경 시 재배포 불필요
- **풍부한 응답 타입**: 텍스트·이미지·임베디드 리소스를 한 번의 호출로 반환
- **양방향 통신**: progress 알림, sampling, capability 변경 알림 등 푸시형 이벤트 지원
- **생태계 호환**: Claude 등 LLM 클라이언트와 동일한 인터페이스 공유

### 1.3 REST/Swagger vs MCP 핵심 차이

| 구분 | REST / OpenAPI | MCP |
|------|---------------|-----|
| 도구 정의 위치 | 정적 문서(.yaml/.json) | 런타임 핸드셰이크 (`tools/list`) |
| 호출 모델 | HTTP path + method + body | JSON-RPC `tools/call(name, arguments)` |
| 응답 형태 | JSON 단일 객체 | `content[]` (text/image/resource 블록 배열) |
| 양방향 통신 | 없음(요청-응답) | 알림·진행률·sampling 지원 |
| 전송 계층 | HTTP | stdio / SSE / Streamable HTTP |
| 인증 | API key, OAuth2 등 자유 | 헤더 기반 / OAuth 2.1 (2025-06-18 스펙) |
| 유량제어 | 명세 없음(서버 자유) | 명세 없음 (직접 구현 필요) |

---

## 2. MCP 지원 아키텍처

### 2.1 전체 구성

```
┌──────────────────────────────────────────────────────────┐
│                    MACS Frontend (UI)                     │
│  ┌────────────────┐    ┌──────────────────────────────┐  │
│  │ REST Connector │    │ MCP Connector UI             │  │
│  │ Form (Swagger) │    │ - Tools / Resources / Prompts│  │
│  └────────────────┘    │ - JSON Schema Form Renderer  │  │
│                        │ - Multi-content Result Viewer│  │
│                        └──────────────────────────────┘  │
└─────────────────────┬─────────────────────┬──────────────┘
                      │                     │
┌─────────────────────▼─────────────────────▼──────────────┐
│         MACS Backend - Unified Connector Interface        │
│         discover() / invoke() / stream()                  │
│  ┌──────────────────┐    ┌───────────────────────────┐   │
│  │ REST Connector   │    │ MCP Connector Layer       │   │
│  │ (OpenAPI parser) │    │ (Session / Discovery /    │   │
│  │                  │    │  Invoke proxy)            │   │
│  └──────────────────┘    └───────────┬───────────────┘   │
│           │                          │                    │
│  ┌────────▼──────────────────────────▼───────────────┐   │
│  │  Rate Limit & Concurrency Gateway                 │   │
│  │  (Redis token bucket + in-flight counter)         │   │
│  └────────┬──────────────────────────┬───────────────┘   │
└───────────┼──────────────────────────┼───────────────────┘
            │                          │
       External APIs              External MCP Servers
       (Swagger)                  (stdio / Streamable HTTP)
```

### 2.2 MCP Connector Layer 책임

MACS 백엔드 내부에 신규 모듈로 추가한다. 책임은 세 영역이다.

**(1) 세션 관리**

- 등록된 MCP 서버마다 연결 세션을 유지한다.
- `initialize` 호출로 프로토콜 버전 협상, capability 교환을 수행한다.
- 세션 ID, 인증 토큰, capability 캐시를 보관한다.
- 연결 끊김 시 지수 백오프 기반 자동 재연결을 시도한다.

**(2) 능력 검색 및 캐싱**

- `tools/list`, `resources/list`, `prompts/list` 결과를 캐싱한다.
- MCP 서버가 `notifications/tools/list_changed` 등을 보낼 경우 캐시를 무효화한다.
- 캐시한 inputSchema는 UI 폼 생성·입력 검증 양쪽에서 재사용한다.

**(3) 실행 프록시**

- 상위 호출을 `tools/call` JSON-RPC 메시지로 변환한다.
- `content[]` 응답을 MACS 표준 응답 포맷으로 정규화(text·image·resource 분기 처리)한다.
- `notifications/progress` 이벤트를 SSE/WebSocket으로 UI에 중계한다.

### 2.3 통합 Connector 추상화

기존 REST Connector와 MCP Connector는 다음 공통 인터페이스를 구현하도록 한다. 상위 UI/오케스트레이션 코드는 두 프로토콜 차이를 거의 의식하지 않게 된다.

```typescript
interface Connector {
  discover(): Promise<OperationCatalog>;          // 호출 가능 작업 목록
  invoke(opId: string, args: object): Promise<NormalizedResult>;
  stream?(opId: string, args: object): AsyncIterable<ProgressOrResult>;
  healthCheck(): Promise<ConnectorStatus>;
}
```

### 2.4 전송 계층 선택

- **기본**: Streamable HTTP (MCP 2025-03-26 권장)
- **외부 SSE 서버**: 호환 모드로 지원, 단 신규 등록은 권장하지 않음
- **stdio 서버**: 사내 로컬 도구 한정. 별도 어댑터(`stdio → HTTP gateway`)로 감싸 운영
- **인증**: None / Bearer / OAuth 2.1 / Custom headers 네 가지 지원

---

## 3. UI 변경 방안

### 3.1 연동 등록 화면 (Connector Registration)

기존 "Swagger URL 입력" 화면 상단에 **연동 타입 토글**을 추가한다.

```
┌─ Connector Type ─────────────────────────────────┐
│  ( ) REST / OpenAPI   ( ● ) MCP                  │
└──────────────────────────────────────────────────┘

[MCP 선택 시 입력 필드]
- Server Endpoint URL    : https://...
- Transport              : [Streamable HTTP ▼]
- Authentication         : [None / Bearer / OAuth 2.1 / Custom Headers ▼]
- (Stdio 선택 시) Command: e.g. `node ./server.js`
- Display Name           : 표시용 별칭
```

등록 직후 백엔드는 백그라운드에서 `initialize → tools/list` 시도 → 결과 미리보기를 UI에 제공한다.

### 3.2 Connector 상세 화면

Swagger UI에서 "엔드포인트 목록"이 노출되던 자리를 **3-탭 구조**로 교체한다.

| 탭 | 내용 | 데이터 소스 |
|---|------|-------------|
| Tools | 도구 카드 목록 (name·description·필수 인자 요약) | `tools/list` |
| Resources | 리소스 URI 목록 (description·MIME type) | `resources/list` |
| Prompts | 프롬프트 템플릿 목록 | `prompts/list` |

### 3.3 도구 실행 화면 (Tool Invocation)

도구 카드 클릭 → 실행 화면이 열린다. 다음 구성요소가 필요하다.

- **입력 폼**: tool의 `inputSchema`(JSON Schema)를 폼 생성기에 그대로 입력. 기존 OpenAPI 파라미터 → 폼 변환기가 이미 JSON Schema 기반이라면 어댑터 한 겹으로 재사용 가능.
- **실행 버튼 + 진행률 인디케이터**: `notifications/progress` 이벤트 스트리밍 표시.
- **결과 패널 (멀티 콘텐츠)**: `content[]` 블록을 타입별로 분기 렌더링.
  - `text` → 마크다운/코드 뷰
  - `image` → 인라인 이미지
  - `resource` → 미리보기 + 다운로드 링크
- **에러 패널**: JSON-RPC 에러 코드별 친절한 메시지(특히 rate limit, auth 만료).

### 3.4 MCP 전용 UI 요소

- **세션 상태 배지**: `Connected` / `Reconnecting` / `Disconnected`
- **Capability 변경 알림**: `tools/list_changed` 수신 시 토스트로 알림 + 리스트 자동 갱신
- **유량제어 정보 노출**: 한도 초과 시 어떤 한도(도구/사용자/조직)에 걸렸는지 명시

### 3.5 변경 영향 범위 요약

| 영역 | 변경 정도 | 비고 |
|------|----------|------|
| 연동 등록 화면 | 중 | 타입 토글 + MCP 입력 필드 추가 |
| 연동 상세 화면 | 대 | 단일 엔드포인트 트리 → 3-탭 구조 |
| 실행/결과 화면 | 대 | 멀티 콘텐츠 렌더러 신규, 진행률 스트리밍 |
| 어드민/정책 | 중 | rate limit 정책 UI 추가 |

---

## 4. 유량제어(Rate Limiting) 방안

### 4.1 핵심 전제

**MCP 표준 스펙 자체에는 rate limiting이 정의되어 있지 않다.** 따라서 MACS가 직접 제어 계층을 책임져야 하며, 단일 지점이 아니라 **다층 방어(defense in depth)** 로 구성한다.

### 4.2 4단계 제어 계층

#### 1차: MCP Connector Layer (메인 게이트웨이)

- 모든 `tools/call`이 통과하는 지점이므로 **핵심 제어 위치**.
- 알고리즘: Token Bucket 또는 Sliding Window.
- 저장소: Redis 분산 카운터(멀티 인스턴스 MACS 일관성 확보).
- 키 구성(중첩 적용):
  1. `(user, server, tool)` — 가장 세밀한 한도
  2. `(user, server)` — 사용자별 서버 한도
  3. `(organization, server)` — 조직 한도
  4. `(global, server)` — 글로벌 보호 한도

#### 2차: 전송 계층 게이트웨이 (Nginx / Envoy)

- IP / 세션 단위의 거친 한도.
- 외부 폭주, DDoS 1차 방어용.
- tool 의미를 모르므로 정밀 제어 불가 — 보조 역할만.

#### 3차: 외부 MCP 서버의 자체 한도

- 서드파티 서버는 자체적으로 한도를 적용하고 JSON-RPC 에러로 반환(보통 `-32000` 계열).
- Connector Layer는 이 에러를 정규화해 UI에 "외부 서버 한도 초과, N초 후 재시도" 노출.
- 동일 사용자에 대해 짧은 자체 백오프 윈도우를 부가 적용.

#### 4차: 동시성 제어 (In-flight Counter)

- 일부 MCP 도구는 수십 초~수 분 소요 → RPS만 제어하면 큐가 무한 증가.
- `(user, server)` 단위 동시 진행 중 호출 수 상한 적용.
- 상한 초과 시 즉시 에러 또는 큐잉(정책에 따라).

### 4.3 정책 관리

MACS 어드민에서 3단 우선순위로 정책 정의.

1. **글로벌 디폴트** (시스템 기본값)
2. **MCP 서버별 오버라이드** (외부 서버 특성 반영)
3. **사용자/조직 등급별 오버라이드** (요금제/권한 반영)

정책 단위 예시:

```yaml
default:
  perUserPerServer:   { rps: 5,  burst: 10 }
  perOrgPerServer:    { rps: 50, burst: 100 }
  perUserPerTool:     { rpm: 30 }
  inFlightPerUser:    3

overrides:
  - server: "ahrefs-mcp"
    perUserPerServer: { rps: 1, burst: 2 }   # 외부 API 비용 보호
  - userTier: "enterprise"
    perUserPerServer: { rps: 20, burst: 40 }
```

### 4.4 한도 초과 응답 표준화

UI/클라이언트에 일관된 형태로 내려보낸다.

```json
{
  "error": {
    "code": "RATE_LIMITED",
    "message": "이 도구는 분당 30회 한도를 초과했습니다.",
    "retryAfterSec": 18,
    "limitScope": "user:tool",
    "limitName": "perUserPerTool"
  }
}
```

UI는 `limitScope`/`limitName`을 사용해 "어떤 한도에 걸렸는지" 명확히 표시한다.

### 4.5 관측성(Observability)

- 한도 적용/차단 메트릭을 Prometheus로 노출(scope·tool 라벨 포함).
- 한도 임계 근접 시 Slack 알림.
- 도구별 사용 패턴 대시보드(Grafana) — 정책 튜닝 기반 자료.

---

## 5. 단계별 도입 로드맵

### Phase 1 — MCP Connector 골격 (4~6주)

- [ ] MCP Connector Layer 모듈 신설
- [ ] Streamable HTTP transport 구현
- [ ] 인증: None / Bearer 지원
- [ ] `initialize` → `tools/list` → `tools/call` 기본 흐름
- [ ] 통합 Connector 추상 인터페이스 정의
- [ ] 내부 MCP 서버 1~2개 파일럿 연동
- [ ] 기본 JSON Schema 폼 렌더러

**완료 기준**: 내부 MCP 서버 1개 등록 → 도구 1개 UI에서 실행 → 결과 표시

### Phase 2 — UI 풀 통합 (3~4주)

- [ ] 연동 등록 화면 타입 토글 + MCP 입력 폼
- [ ] Tools / Resources / Prompts 3-탭 화면
- [ ] 멀티 콘텐츠 결과 렌더러 (text/image/resource)
- [ ] 진행률 스트리밍 UI
- [ ] 세션 상태 배지, capability 변경 알림

**완료 기준**: REST와 동등한 UX로 MCP 도구 사용 가능

### Phase 3 — 유량제어 (3주)

- [ ] Redis 기반 token bucket 구현 (1차 계층)
- [ ] In-flight counter 동시성 제어 (4차 계층)
- [ ] 어드민 정책 UI (글로벌/서버별/사용자등급별 3단)
- [ ] 표준화된 RATE_LIMITED 에러 흐름
- [ ] 메트릭/대시보드/알림

**완료 기준**: 정책 변경 → 즉시 반영 → UI에 한도 초과 메시지 정확 표시

### Phase 4 — 고급 기능 (선택)

- [ ] OAuth 2.1 인증 흐름 (2025-06-18 스펙)
- [ ] Resources / Prompts 완전 지원
- [ ] `notifications/*` 기반 캐시 무효화
- [ ] stdio 어댑터 (사내 로컬 도구용)
- [ ] Sampling 기능 (MCP → MACS LLM 호출)

---

## 6. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|--------|------|------|
| MCP 스펙이 활발히 변경 중 | 호환성 깨짐 | 프로토콜 버전 negotiation 활용, 버전별 어댑터 |
| 외부 MCP 서버 품질 편차 | 응답 지연/장애 | 타임아웃·서킷브레이커·동시성 제한 필수 |
| 표준 rate limit 부재 | 외부 서버 한도 정보 비표준 | 사내 정규화 레이어, 학습 기반 백오프 |
| `inputSchema`가 복잡한 JSON Schema 사용 | 폼 렌더러 제한 | $ref·oneOf·anyOf 단계적 지원, 미지원 시 raw JSON 입력 fallback |
| stdio 서버 운영 복잡성 | 인프라 부담 | Phase 4로 후순위, 우선 HTTP 기반만 |

---

## 7. 결론

Swagger 자동 연동이 **정적 문서 1회 파싱** 모델이라면, MCP 연동은 **라이브 세션 + 런타임 검색** 모델이다. 큰 차이는 (a) 연결·세션 라이프사이클을 관리하는 Connector Layer가 필요하다는 점, (b) 응답이 멀티 콘텐츠 블록이라 렌더러 확장이 필요하다는 점, (c) MCP 프로토콜이 rate limiting을 표준으로 제공하지 않으므로 MACS가 직접 다층 제어를 구현해야 한다는 점이다.

다행히 tool의 `inputSchema`가 JSON Schema라서 기존 폼 렌더러를 대부분 재활용할 수 있고, 통합 Connector 추상 인터페이스로 상위 UI/오케스트레이션 코드의 변경 범위를 최소화할 수 있다. 유량제어는 MCP 프로토콜 차원이 아니라 **Connector Layer(메인) + 게이트웨이(보조) + 외부 서버 응답 정규화 + 동시성 제어**의 4층 조합으로 충분히 정밀한 통제가 가능하다.

총 예상 일정은 Phase 1~3 합산 **10~13주**이며, Phase 4는 수요에 따라 선택 진행 권장.

---

## 부록 A. MCP 핵심 메서드 요약

| 메서드 | 방향 | 용도 |
|--------|------|------|
| `initialize` | Client → Server | 세션 시작, 버전·capability 협상 |
| `tools/list` | Client → Server | 사용 가능 도구 목록 조회 |
| `tools/call` | Client → Server | 도구 실행 |
| `resources/list` | Client → Server | 리소스 목록 |
| `resources/read` | Client → Server | 리소스 내용 조회 |
| `prompts/list` | Client → Server | 프롬프트 템플릿 목록 |
| `notifications/tools/list_changed` | Server → Client | 도구 목록 변경 알림 |
| `notifications/progress` | Server → Client | 장기 실행 진행률 |

## 부록 B. 참고 자료

- MCP Specification: https://modelcontextprotocol.io
- JSON-RPC 2.0: https://www.jsonrpc.org/specification
- JSON Schema: https://json-schema.org
- OAuth 2.1 draft: https://datatracker.ietf.org/doc/draft-ietf-oauth-v2-1/
