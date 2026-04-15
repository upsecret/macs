# Gateway → port 80, Portal → 루트 경로 직접 서빙

## 목적

- `http://localhost` (80 번) 로 접속하면 바로 portal 이 보이도록.
- `/api/**`, `/v3/api-docs/**` 등 백엔드 경로는 기존대로 gateway 가 라우팅.
- Portal 코드 / 빌드 / nginx 는 **전혀 건드리지 않음**.

## 핵심 아이디어

Gateway 의 기존 `routes[4] portal-route` 의 **path predicate 만** `/portal/**` 에서 **portal 이 서빙하는 구체 경로 리스트** 로 교체. `StripPrefix=1` 제거. Portal 은 이미 루트 기준이라 빌드/설정 변경 불필요.

`/actuator/**`, `/swagger-ui.html`, `/webjars/**`, `/v3/api-docs` (gateway 자체) 는 어떤 route predicate 에도 매칭되지 않으므로 Spring Boot 기본 핸들러로 fall-through → 그대로 동작.

> catch-all `/**` 를 쓰지 않은 이유: Spring Cloud Gateway 는 route 매칭 성공 시 내부 핸들러로 fall-through 하지 않음. `/**` 면 gateway 자체 actuator/swagger 가 portal 로 가버려 죽음.

---

## 수정 파일

### 1. `docker-compose.yml` (line 273)

Gateway 호스트 포트 매핑 변경. Spring Boot 내부는 8080 유지 → actuator / healthcheck 설정 수정 불필요.

```diff
   gateway-service:
     container_name: macs-gateway-service
     ports:
-      - "8080:8080"
+      - "80:8080"
```

### 2. `infra/oracle/init.sql` (routes[4] 영역)

Portal route 의 predicate 를 경로 리스트로 교체하고, 불필요해진 `StripPrefix=1` filter MERGE 블록을 삭제.

```diff
 MERGE INTO PROPERTIES t USING (SELECT 'gateway-service' APPLICATION,'default' PROFILE,'main' LABEL,
-    'spring.cloud.gateway.server.webflux.routes[4].predicates[0]' PROP_KEY,'Path=/portal/**' PROP_VALUE FROM dual) s
+    'spring.cloud.gateway.server.webflux.routes[4].predicates[0]' PROP_KEY,'Path=/,/login,/connector/**,/route-config/**,/auth-manage/**,/settings/**,/assets/**,/favicon.ico,/favicon.svg,/manifest.json' PROP_VALUE FROM dual) s
   ON (t.APPLICATION=s.APPLICATION AND t.PROFILE=s.PROFILE AND t.LABEL=s.LABEL AND t.PROP_KEY=s.PROP_KEY)
   WHEN NOT MATCHED THEN INSERT (APPLICATION,PROFILE,LABEL,PROP_KEY,PROP_VALUE)
     VALUES (s.APPLICATION,s.PROFILE,s.LABEL,s.PROP_KEY,s.PROP_VALUE);
-MERGE INTO PROPERTIES t USING (SELECT 'gateway-service' APPLICATION,'default' PROFILE,'main' LABEL,
-    'spring.cloud.gateway.server.webflux.routes[4].filters[0]' PROP_KEY,'StripPrefix=1' PROP_VALUE FROM dual) s
-  ON (t.APPLICATION=s.APPLICATION AND t.PROFILE=s.PROFILE AND t.LABEL=s.LABEL AND t.PROP_KEY=s.PROP_KEY)
-  WHEN NOT MATCHED THEN INSERT (APPLICATION,PROFILE,LABEL,PROP_KEY,PROP_VALUE)
-    VALUES (s.APPLICATION,s.PROFILE,s.LABEL,s.PROP_KEY,s.PROP_VALUE);
```

**매칭 경로 근거**

| 경로 | 출처 |
|---|---|
| `/` | portal index |
| `/login` | [portal/app/routes.ts:4](../portal/app/routes.ts) |
| `/connector/**`, `/route-config/**`, `/auth-manage/**`, `/settings/**` | [portal/app/routes.ts:7-10](../portal/app/routes.ts) (`**` 로 중첩 라우트 대비) |
| `/assets/**` | Vite 빌드 산출물 |
| `/favicon.svg` | [portal/app/root.tsx:19](../portal/app/root.tsx) |
| `/favicon.ico`, `/manifest.json` | 일반 SPA 표준 파일 |

> 향후 React 라우트가 늘면 이 predicate 에 항목 추가 필요. Trade-off: portal 코드 무변경.

### 3. `portal/vite.config.ts` (line 5-7)

로컬 `npm run dev` 시 `/api/**` 등 프록시 target 을 호스트 80 번 gateway 로 변경.

```diff
-// 로컬 개발: 백엔드는 docker compose로 띄우고 포털만 `npm run dev`로 실행.
-// 아래 경로들은 gateway-service(8080)로 프록시 — nginx.conf의 규칙과 동일.
-const GATEWAY = "http://localhost:8080";
+// 로컬 개발: 백엔드는 docker compose로 띄우고 포털만 `npm run dev`로 실행.
+// 아래 경로들은 호스트 80 번 gateway 로 프록시 — docker-compose 에서 80:8080 매핑.
+const GATEWAY = "http://localhost";
```

### 4. `gateway-service/src/main/java/com/macs/gateway/filter/HeaderValidationFilter.java`

`HeaderValidationFilter` 는 모든 요청에 `app_name`, `employee_number` 헤더를 강제하는 **GlobalFilter** 다. 기존 `shouldSkip` 은 `/actuator`, `/swagger-ui`, `/v3/api-docs`, `/webjars`, `/assets`, `/favicon.*`, `/manifest.json` 만 path prefix 로 화이트리스트해서 `/`, `/login`, `/connector` 같은 **포털 SPA 경로가 400 으로 차단**됐다 (브라우저에선 whitelabel 비슷한 JSON 에러로 보임).

**접근 방법 선택**: path prefix 기반 allowlist/denylist 는 유지보수 지옥이다.
- `/api/**` 만 검증 (denylist 반전) → ❌ `/rms/api`, `/fdc/api`, `/token-dic` 같은 다른 백엔드 prefix 가 전부 검증 누락됨
- 포털 경로를 allowlist 에 추가 → ❌ 새 React 라우트가 생길 때마다 두 군데(gateway route predicate + 이 필터)를 동시 수정해야 함

**채택**: **매칭된 gateway route 의 id 기반 skip**. `portal-route` 하나만 skip 집합에 넣고, 그 외 모든 route(auth, admin, rms, fdc, token-dic, 미래의 뭐든) 는 자동으로 검증 대상이 된다. Spring Cloud Gateway 는 route 매칭 결과를 `ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR` 에 담아 필터 체인에 전달하므로 GlobalFilter 에서 접근 가능.

또한 **어떤 gateway route 에도 매칭되지 않은 요청**(`/actuator/**`, `/swagger-ui.html`, `/webjars/**` 등 Spring Boot 내부 핸들러가 담당) 은 그 자체로 이 필터 체인에 아예 진입하지 않으므로 별도 처리 불필요.

```diff
+import org.springframework.cloud.gateway.route.Route;
+import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
+import java.util.Set;

 @Component
 public class HeaderValidationFilter implements GlobalFilter, Ordered {

     private static final Logger log = LoggerFactory.getLogger(HeaderValidationFilter.class);

+    /**
+     * 헤더 검증을 건너뛸 gateway route id 목록.
+     * 포털 SPA 는 HTML/정적 리소스라 app_name/employee_number 헤더가 없음.
+     * 이외 모든 라우트(auth, admin, rms, fdc, token-dic 등)는 검증 대상.
+     */
+    private static final Set<String> SKIPPED_ROUTE_IDS = Set.of("portal-route");
     ...

     private boolean shouldSkip(ServerWebExchange exchange) {
-        String path = exchange.getRequest().getURI().getPath();
-        return path.startsWith("/actuator")
-                || path.startsWith("/swagger-ui")
-                || path.startsWith("/v3/api-docs")
-                || path.startsWith("/webjars")
-                || path.startsWith("/assets")
-                || path.startsWith("/favicon.ico")
-                || path.startsWith("/favicon.svg")
-                || path.startsWith("/manifest.json");
+        Route route = (Route) exchange.getAttributes()
+                .get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
+        return route != null && SKIPPED_ROUTE_IDS.contains(route.getId());
     }
 }
```

**확장 방법**: 새로 헤더 검증을 건너뛸 route 가 생기면 `SKIPPED_ROUTE_IDS` 에 route id 한 줄 추가. Path 도 필터 로직도 건드릴 필요 없음.

---

## 변경하지 **않은** 것

- ❌ `gateway-service/src/main/resources/application.yml` `server.port` — 8080 유지
- ❌ Vite `base`, React Router `basename` / `basePath`
- ❌ `portal/Dockerfile` COPY 경로
- ❌ `portal/nginx.conf` (기존 역프록시 블록은 사실상 dead code 이지만 무해해서 남겨둠)
- ❌ `portal/app/root.tsx`, `portal/app/utils/api.ts` 하드코드 경로

---

## 반영 & 검증 절차

### 반영

```bash
docker compose down -v                  # Oracle 볼륨 초기화 (init.sql 재실행 필요)
docker compose build gateway-service    # HeaderValidationFilter 코드 변경 반영
docker compose up -d
```

> **중요**: `HeaderValidationFilter.java` 수정은 Java 코드 변경이므로 gateway-service 이미지를 **반드시 재빌드** 해야 한다. 단순 `docker compose restart gateway-service` 로는 반영 안 됨.
>
> `down -v` 는 Oracle / Redis / Kafka / ES 볼륨을 모두 삭제한다. 다른 데이터 유실이 걱정되면 `-v` 없이 재기동 후 운영 DB 에 수동 SQL (아래) + portal `/route-config` → "변경사항 반영" 으로 bus-refresh 를 돌리는 것도 가능.
>
> ```sql
> UPDATE PROPERTIES
>    SET PROP_VALUE = 'Path=/,/login,/connector/**,/route-config/**,/auth-manage/**,/settings/**,/assets/**,/favicon.ico,/favicon.svg,/manifest.json'
>  WHERE APPLICATION = 'gateway-service'
>    AND PROP_KEY    = 'spring.cloud.gateway.server.webflux.routes[4].predicates[0]';
>
> DELETE FROM PROPERTIES
>  WHERE APPLICATION = 'gateway-service'
>    AND PROP_KEY    = 'spring.cloud.gateway.server.webflux.routes[4].filters[0]';
> COMMIT;
> ```

### 트러블슈팅 체크리스트

`http://localhost/` 접속 시 증상별 원인:

| 증상 | 원인 | 해결 |
|---|---|---|
| whitelabel 404 error page | `routes[4]` predicate 가 옛날 값 (`/portal/**`) — init.sql 재실행 안 됨 | `docker compose down -v` 로 Oracle 볼륨 초기화 후 재기동, 또는 수동 UPDATE SQL |
| `{"error":"Bad Request","message":"Missing required header: app_name"}` | `HeaderValidationFilter` 구버전 — 코드 재빌드 안 됨 | `docker compose build gateway-service` 후 재기동 |
| Connection refused / 포트 80 응답 없음 | Windows `http.sys` 또는 IIS 가 80 선점 | `netstat -ano | findstr :80` 확인, 선점 프로세스 중지 |
| portal index 는 뜨는데 `/assets/xxx.js` 404 | predicate 에 `/assets/**` 누락 | init.sql 의 `routes[4].predicates[0]` 값 재확인 |

라우트가 실제로 반영됐는지 직접 확인:
```bash
curl http://localhost/actuator/gateway/routes | jq '.[] | select(.route_id=="portal-route")'
```

### 검증

1. **포털 렌더**: 브라우저 `http://localhost/` → portal index 페이지. devtools Network 탭에서 `/assets/xxx.js` 가 200 으로 로드되는지 확인.
2. **딥링크**: `http://localhost/login`, `http://localhost/connector` 직접 접근 → React Router SPA fallback (portal nginx `try_files`) 정상 동작.
3. **API 라우팅**: `http://localhost/api/config/routes` → admin-server 응답 (gateway `routes[1]` + AuthValidation 통과).
4. **Gateway 내부 엔드포인트**:
   - `http://localhost/actuator/health` → 200
   - `http://localhost/swagger-ui.html` → gateway swagger UI 로드
   - `http://localhost/v3/api-docs/auth-server` → auth-server api docs (`routes[2]` 매칭)
5. **Dev 모드**: `cd portal && npm run dev` → `http://localhost:3000/`, `/api/**` 호출이 호스트 80 gateway 로 프록시되는지 확인.

## 사전 확인

- **Windows 호스트 80 포트 점유**: `netstat -ano | findstr :80` 로 IIS / `http.sys` 등 선점 여부 확인.
- **OAuth2 redirect URI**: auth-server 로그인 완료 후 redirect 가 `http://localhost:3000/...` 로 하드코드되어 있는 경우 별도 조정 필요 (이번 변경 범위 밖).

---

## 실제 검증 결과

`docker compose down -v && docker compose up -d --build` 후 smoke test:

| 경로 | HTTP | 비고 |
|---|---|---|
| `/` | 200 | Portal index HTML |
| `/login` | 200 | Portal SPA (header filter 차단 해제 확인) |
| `/connector` | 200 | React Router 딥링크 |
| `/assets/manifest-*.js` | 200 | Vite 빌드 산출물 |
| `/favicon.svg` | 200 | 정적 리소스 |
| `/actuator/health` | 200 | Gateway 내부 (route 매칭 없음, Spring Boot fall-through) |
| `/swagger-ui.html` | 302 | 정상 리다이렉트 |
| `/api/config/routes` (헤더 無) | 400 `Missing required header: app_name` | HeaderValidationFilter 정상 동작 |
| `/api/config/routes` (헤더 有) | 401 `Missing Authorization header` | 헤더 통과 후 AuthValidation 에서 JWT 요구 |

→ portal-route 로 매칭된 요청만 header filter skip, 그 외 `/api/**` 백엔드 호출은 변함없이 검증. `/rms/api`, `/fdc/api`, `/token-dic` 등 향후 추가될 route 도 route id 기반이라 자동 검증 대상.
