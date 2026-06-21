#!/usr/bin/env bash
# ============================================================
# MACS E2E — 권한체크 / 유량제어 / 헤더필수 / client_app 바인딩 검증
#
# 전제: docker compose 로 gateway(8080), auth(9000), dummy-api(8088) 기동됨.
# 라우트·권한은 이 스크립트가 런타임 admin API 로 주입 (DB 볼륨 보존, 재실행 안전).
# ============================================================
set -u
GW="http://localhost:8080"
PASS=0; FAIL=0
BODY=/tmp/macs_e2e_body.txt

# ── curl helper: stdout = HTTP status, body → $BODY ─────────
req() { local m="$1" u="$2"; shift 2; curl -s -o "$BODY" -w "%{http_code}" -X "$m" "$u" "$@"; }

assert() { # expected actual name
  if [ "$1" = "$2" ]; then printf '  \033[32mPASS\033[0m [%s] status=%s\n' "$3" "$2"; PASS=$((PASS+1));
  else printf '  \033[31mFAIL\033[0m [%s] expected=%s got=%s\n        body=%s\n' "$3" "$1" "$2" "$(head -c 300 "$BODY")"; FAIL=$((FAIL+1)); fi
}
assert_contains() { # needle name
  if grep -q "$1" "$BODY"; then printf '  \033[32mPASS\033[0m [%s] body⊇"%s"\n' "$2" "$1"; PASS=$((PASS+1));
  else printf '  \033[31mFAIL\033[0m [%s] body missing "%s"\n        body=%s\n' "$2" "$1" "$(head -c 300 "$BODY")"; FAIL=$((FAIL+1)); fi
}

# ── 0. 서비스 헬스 대기 ─────────────────────────────────────
echo "== 0. 헬스 체크 대기 =="
for url in "$GW/actuator/health" "http://localhost:9000/actuator/health" \
           "http://localhost:8088/health" "http://localhost:8090/health" \
           "http://localhost:8765/health" "http://localhost:8766/health" \
           "http://localhost:8767/health" "http://localhost:8768/health"; do
  for i in $(seq 1 30); do
    c=$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)
    [ "$c" = "200" ] && { echo "  up: $url"; break; }
    sleep 2
    [ "$i" = "30" ] && { echo "  TIMEOUT: $url"; exit 1; }
  done
done

# admin 엔드포인트가 이제 토큰+admin role 을 요구하므로 admin 토큰을 선발급
TOKEN_ADMIN=$(curl -s -X POST "$GW/api/auth/token" \
  -H "app_name: portal" -H "employee_number: 2078432" -H "Content-Type: application/json" \
  -d '{"employee_number":"2078432","client_app":"portal"}' | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4)
H_ADMIN=(-H "app_name: portal" -H "employee_number: 2078432" -H "Authorization: Bearer $TOKEN_ADMIN" -H "Content-Type: application/json")

# ── 1. 라우트 주입 (delete→create, 멱등) ────────────────────
echo "== 1. 보호 라우트/유량 라우트 주입 =="
req DELETE "$GW/api/config/routes/e2e-echo-route" "${H_ADMIN[@]}" >/dev/null
req DELETE "$GW/api/config/routes/e2e-rl-route"   "${H_ADMIN[@]}" >/dev/null

c=$(req POST "$GW/api/config/routes" "${H_ADMIN[@]}" -d '{
  "id":"e2e-echo-route","uri":"http://dummy-api-server:8088",
  "predicates":[{"name":"Path","args":{"_genkey_0":"/api/echo/**"}}],
  "filters":[{"name":"StripPrefix","args":{"_genkey_0":"2"}},
             {"name":"AuthValidation","args":{"connector":"e2e-echo"}}],
  "order":-100,"registerSwagger":false}')
echo "  echo-route create: $c"

c=$(req POST "$GW/api/config/routes" "${H_ADMIN[@]}" -d '{
  "id":"e2e-rl-route","uri":"http://dummy-api-server:8088",
  "predicates":[{"name":"Path","args":{"_genkey_0":"/api/rl/**"}}],
  "filters":[{"name":"StripPrefix","args":{"_genkey_0":"2"}},
             {"name":"RequestRateLimiter","args":{
               "redis-rate-limiter.replenishRate":"1",
               "redis-rate-limiter.burstCapacity":"2",
               "key-resolver":"#{@headerKeyResolver}",
               "deny-empty-key":"false"}}],
  "order":-100,"registerSwagger":false}')
echo "  rl-route create: $c"

# ── 2. 권한 부여: 2078432 → e2e-echo (allow 대상) ───────────
echo "== 2. 권한 부여 (portal/2078432/common/e2e-echo) =="
c=$(req POST "$GW/api/admin/permissions" "${H_ADMIN[@]}" -d '{
  "appName":"portal","employeeNumber":"2078432","system":"common",
  "connector":"e2e-echo","role":"admin"}')
echo "  grant: $c (201=신규, 409=기존)"

# 라우트 refresh 전파 대기 (echo-route 가 라우팅될 때까지)
for i in $(seq 1 15); do
  c=$(curl -s -o /dev/null -w "%{http_code}" "$GW/api/echo/ping" \
        -H "app_name: portal" -H "employee_number: 2078432")
  [ "$c" != "404" ] && break; sleep 1
done
echo "  echo-route 라우팅 활성화 (probe status=$c)"

# ── 3. 토큰 발급 (client_app 바인딩) ────────────────────────
echo "== 3. 토큰 발급: client_app 필수 검증 =="
c=$(req POST "$GW/api/auth/token" "${H_ADMIN[@]}" -d '{"employee_number":"2078432","client_app":"portal"}')
assert 201 "$c" "토큰발급(client_app 포함)"
assert_contains '"client_app":"portal"' "응답에 client_app 반환"
TOKEN_ADMIN=$(grep -o '"token":"[^"]*"' "$BODY" | head -1 | cut -d'"' -f4)

c=$(req POST "$GW/api/auth/token" "${H_ADMIN[@]}" -d '{"employee_number":"2078432"}')
assert 400 "$c" "토큰발급: client_app 누락 → 400"

c=$(req POST "$GW/api/auth/token" "${H_ADMIN[@]}" -d '{"client_app":"portal"}')
assert 400 "$c" "토큰발급: employee_number 누락 → 400"

# 2065162 (portal-route 권한만, e2e-echo 없음)
c=$(req POST "$GW/api/auth/token" -H "app_name: portal" -H "employee_number: 2065162" -H "Content-Type: application/json" \
      -d '{"employee_number":"2065162","client_app":"portal"}')
TOKEN_USER=$(grep -o '"token":"[^"]*"' "$BODY" | head -1 | cut -d'"' -f4)
echo "  admin/user 토큰 확보 (admin len=${#TOKEN_ADMIN}, user len=${#TOKEN_USER})"

# ── 4. 헤더 필수 (client-app / employee-number) ─────────────
echo "== 4. 헤더 필수 제약 =="
c=$(req POST "$GW/api/auth/token" -H "employee_number: 2078432" -H "Content-Type: application/json" -d '{"employee_number":"2078432","client_app":"portal"}')
assert 400 "$c" "app_name 헤더 누락 → 400"
c=$(req POST "$GW/api/auth/token" -H "app_name: portal" -H "Content-Type: application/json" -d '{"employee_number":"2078432","client_app":"portal"}')
assert 400 "$c" "employee_number 헤더 누락 → 400"

# ── 5. 권한 체크 (allow / deny) ─────────────────────────────
echo "== 5. 권한 체크 (커넥터 e2e-echo) =="
c=$(req GET "$GW/api/echo/ping" -H "app_name: portal" -H "employee_number: 2078432" -H "Authorization: Bearer $TOKEN_ADMIN")
assert 200 "$c" "권한 보유자(2078432) → 200 ALLOW"
assert_contains '"service":"dummy-api-server"' "업스트림 도달 (echo)"
assert_contains '"path":"/ping"' "StripPrefix 적용 (/api/echo 제거)"

c=$(req GET "$GW/api/echo/ping" -H "app_name: portal" -H "employee_number: 2065162" -H "Authorization: Bearer $TOKEN_USER")
assert 403 "$c" "권한 없는자(2065162) → 403 DENY"

# ── 6. 토큰 claim ↔ 헤더 일치 (스푸핑 차단) ─────────────────
echo "== 6. claim ↔ 요청 헤더 일치 검증 =="
c=$(req GET "$GW/api/echo/ping" -H "app_name: evil-app" -H "employee_number: 2078432" -H "Authorization: Bearer $TOKEN_ADMIN")
assert 401 "$c" "client_app 불일치(portal≠evil-app) → 401"
c=$(req GET "$GW/api/echo/ping" -H "app_name: portal" -H "employee_number: 9999999" -H "Authorization: Bearer $TOKEN_ADMIN")
assert 401 "$c" "employee_number 불일치(2078432≠9999999) → 401"

# 토큰 없이 보호 라우트 → 401
c=$(req GET "$GW/api/echo/ping" -H "app_name: portal" -H "employee_number: 2078432")
assert 401 "$c" "보호 라우트 토큰 없음 → 401"

# ── 7. 유량 제어 (RequestRateLimiter, burst=2 replenish=1) ──
echo "== 7. 유량 제어 (e2e-rl-route, 12연속 요청) =="
ok=0; limited=0
for i in $(seq 1 12); do
  c=$(curl -s -o /dev/null -w "%{http_code}" "$GW/api/rl/ping" -H "app_name: portal" -H "employee_number: 2078432")
  [ "$c" = "200" ] && ok=$((ok+1)); [ "$c" = "429" ] && limited=$((limited+1))
done
echo "  결과: 200=$ok, 429=$limited"
if [ "$ok" -ge 1 ] && [ "$limited" -ge 1 ]; then
  printf '  \033[32mPASS\033[0m [유량제어] 일부 통과(%s) 후 429 차단(%s)\n' "$ok" "$limited"; PASS=$((PASS+1))
else
  printf '  \033[31mFAIL\033[0m [유량제어] 200=%s 429=%s (둘 다 ≥1 기대)\n' "$ok" "$limited"; FAIL=$((FAIL+1))
fi

# ── 8. MCP 권한 게이트 (게이트웨이 게이팅: AuthValidation, connector=route id) ───
echo "== 8. MCP 권한 게이트 (게이트웨이 라우트 경유) =="
# 라우트·MCP 서버·권한 모두 런타임 주입 (시드 없음). dummy-mcp-server(8765, stateless/인증없음).
req DELETE "$GW/api/config/routes/dummy-mcp" "${H_ADMIN[@]}" >/dev/null
req POST "$GW/api/config/routes" "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp","uri":"http://dummy-mcp-server:8765",
  "predicates":[{"name":"Path","args":{"_genkey_0":"/mcp/dummy-mcp"}}],
  "filters":[{"name":"RewritePath","args":{"regexp":"/mcp/dummy-mcp","replacement":"/mcp"}},
             {"name":"AuthValidation","args":{}}],
  "order":0,"registerSwagger":false}' >/dev/null
req POST "$GW/api/admin/mcp/servers" "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp","name":"Dummy MCP Server","description":"echo, add",
  "transport":"streamable-http","authType":"none","system":"common"}' >/dev/null
# 권한 부여: 2078432 → dummy-mcp (MCP 도 API 와 동일하게 connector = route id)
req POST "$GW/api/admin/permissions" "${H_ADMIN[@]}" -d '{
  "appName":"portal","employeeNumber":"2078432","system":"common",
  "connector":"dummy-mcp","role":"admin"}' >/dev/null
# 라우트 refresh 전파 대기
for i in $(seq 1 15); do
  c=$(req POST "$GW/api/admin/mcp/servers/dummy-mcp/tools/call" "${H_ADMIN[@]}" -d '{"name":"add","arguments":{"a":2,"b":3}}')
  [ "$c" = "200" ] && break; sleep 1
done

# 8-1: 헤더 없이 → 400 (loopback 라우트의 HeaderValidationFilter 가 app_name 부재 검출)
c=$(req POST "$GW/api/admin/mcp/servers/dummy-mcp/tools/call" -H "Content-Type: application/json" -d '{"name":"add","arguments":{"a":2,"b":3}}')
assert 400 "$c" "MCP 헤더 없음 → 400"

# 8-2: 권한 보유자(2078432) → 200, Sum=5
c=$(req POST "$GW/api/admin/mcp/servers/dummy-mcp/tools/call" \
      -H "app_name: portal" -H "employee_number: 2078432" -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN_ADMIN" -d '{"name":"add","arguments":{"a":2,"b":3}}')
assert 200 "$c" "MCP 권한 보유자 → 200 ALLOW"
assert_contains 'Sum = 5' "MCP tools/call 결과 (add=5)"

# 8-3: 권한 없는자(2065162, mcp 권한 없음) → 403
c=$(req POST "$GW/api/admin/mcp/servers/dummy-mcp/tools/call" \
      -H "app_name: portal" -H "employee_number: 2065162" -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN_USER" -d '{"name":"add","arguments":{"a":2,"b":3}}')
assert 403 "$c" "MCP 권한 없는자 → 403 DENY"

# 8-4: client_app 불일치 → 401
c=$(req POST "$GW/api/admin/mcp/servers/dummy-mcp/tools/call" \
      -H "app_name: evil-app" -H "employee_number: 2078432" -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN_ADMIN" -d '{"name":"add","arguments":{"a":2,"b":3}}')
assert 401 "$c" "MCP client_app 불일치 → 401"

# ── 9. admin/config 엔드포인트 게이트 (토큰 + admin role) ──
echo "== 9. admin/config 관리 엔드포인트 보호 =="
# 9-1: 토큰 없이 admin → 401
c=$(req GET "$GW/api/config/routes" -H "app_name: portal" -H "employee_number: 2078432")
assert 401 "$c" "admin: 토큰 없음 → 401"
# 9-2: 비-admin 토큰(2065162, role=user) → 403
c=$(req GET "$GW/api/config/routes" -H "app_name: portal" -H "employee_number: 2065162" -H "Authorization: Bearer $TOKEN_USER")
assert 403 "$c" "admin: 비-admin role → 403"
# 9-3: admin 토큰 → 200
c=$(req GET "$GW/api/config/routes" "${H_ADMIN[@]}")
assert 200 "$c" "admin: admin role → 200"
# 9-4: 사용자 본인 권한 조회 → 200 (admin 불필요)
c=$(req GET "$GW/api/admin/permissions/users/portal/2065162" -H "app_name: portal" -H "employee_number: 2065162" -H "Authorization: Bearer $TOKEN_USER")
assert 200 "$c" "self 권한 조회(본인) → 200"
# 9-5: 타인 권한 조회 시도 → 403
c=$(req GET "$GW/api/admin/permissions/users/portal/2078432" -H "app_name: portal" -H "employee_number: 2065162" -H "Authorization: Bearer $TOKEN_USER")
assert 403 "$c" "self 권한 조회(타인) → 403"
# 9-6: 중복 grant → 409 (다운스트림 컨트롤러 에러가 게이트에 삼켜지지 않음)
c=$(req POST "$GW/api/admin/permissions" "${H_ADMIN[@]}" -d '{
  "appName":"portal","employeeNumber":"2078432","system":"common","connector":"e2e-echo","role":"admin"}')
assert 409 "$c" "admin: 중복 grant → 409 (다운스트림 에러 보존)"

# ── 10. API 커넥터 문서연동 (swagger-api-server) ─────────────
echo "== 10. API 커넥터 문서연동 (swagger-api-server) =="
req DELETE "$GW/api/config/routes/swagger-api" "${H_ADMIN[@]}" >/dev/null
req POST "$GW/api/config/routes" "${H_ADMIN[@]}" -d '{
  "id":"swagger-api","uri":"http://swagger-api-server:8090",
  "predicates":[{"name":"Path","args":{"_genkey_0":"/api/products/**"}}],
  "filters":[{"name":"AuthValidation","args":{}}],
  "order":0,"registerSwagger":false}' >/dev/null
req POST "$GW/api/admin/connectors" "${H_ADMIN[@]}" -d '{
  "id":"swagger-api","title":"Swagger API Test","type":"api","system":"common"}' >/dev/null
req POST "$GW/api/admin/permissions" "${H_ADMIN[@]}" -d '{
  "appName":"portal","employeeNumber":"2078432","system":"common","connector":"swagger-api","role":"admin"}' >/dev/null
for i in $(seq 1 15); do
  c=$(req GET "$GW/api/products" "${H_ADMIN[@]}"); [ "$c" != "404" ] && break; sleep 1
done
# 10-1: OpenAPI 문서 프록시 (포털 API 문서 뷰어 경로)
c=$(req GET "$GW/api/admin/connectors/swagger-api/api-docs" "${H_ADMIN[@]}")
assert 200 "$c" "API docs 프록시 → 200"
assert_contains '"openapi"' "OpenAPI 문서 취득"
# 10-2: 게이트웨이 통한 실제 호출
c=$(req GET "$GW/api/products" "${H_ADMIN[@]}")
assert 200 "$c" "API 호출(게이트웨이) → 200"

# ── 11. MCP 자체 bearer 검증 (dummy-mcp-auth, AuthValidation 없음) ──
echo "== 11. MCP 자체 bearer (dummy-mcp-auth) =="
req DELETE "$GW/api/config/routes/dummy-mcp-auth" "${H_ADMIN[@]}" >/dev/null
req POST "$GW/api/config/routes" "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp-auth","uri":"http://dummy-mcp-auth:8765",
  "predicates":[{"name":"Path","args":{"_genkey_0":"/mcp/dummy-mcp-auth"}}],
  "filters":[{"name":"RewritePath","args":{"regexp":"/mcp/dummy-mcp-auth","replacement":"/mcp"}}],
  "order":0,"registerSwagger":false}' >/dev/null
req POST "$GW/api/admin/mcp/servers" "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp-auth","name":"Dummy MCP (bearer)","transport":"streamable-http",
  "authType":"bearer","authToken":"mcp-secret-xyz","system":"common"}' >/dev/null
for i in $(seq 1 15); do
  c=$(req GET "$GW/api/admin/mcp/servers/dummy-mcp-auth/tools" "${H_ADMIN[@]}"); [ "$c" = "200" ] && break; sleep 1
done
# 11-1: 게이트웨이가 저장 bearer 주입 → 200
assert 200 "$c" "bearer 주입 tools → 200"
# 11-2: 라우트 직접 호출, bearer 없이 → 업스트림 401 (게이트웨이는 게이팅 안 함)
c=$(req POST "$GW/mcp/dummy-mcp-auth" -H "app_name: portal" -H "employee_number: 2078432" \
      -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}')
assert 401 "$c" "bearer 없이 직접호출 → 401 (서버 검증)"

# ── 12. MCP stateful 세션 (dummy-mcp-stateful, 게이트웨이 게이팅) ──
echo "== 12. MCP stateful 세션 (dummy-mcp-stateful) =="
req DELETE "$GW/api/config/routes/dummy-mcp-stateful" "${H_ADMIN[@]}" >/dev/null
req POST "$GW/api/config/routes" "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp-stateful","uri":"http://dummy-mcp-stateful:8765",
  "predicates":[{"name":"Path","args":{"_genkey_0":"/mcp/dummy-mcp-stateful"}}],
  "filters":[{"name":"RewritePath","args":{"regexp":"/mcp/dummy-mcp-stateful","replacement":"/mcp"}},
             {"name":"AuthValidation","args":{}}],
  "order":0,"registerSwagger":false}' >/dev/null
req POST "$GW/api/admin/mcp/servers" "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp-stateful","name":"Dummy MCP (stateful)","transport":"streamable-http",
  "authType":"none","system":"common"}' >/dev/null
req POST "$GW/api/admin/permissions" "${H_ADMIN[@]}" -d '{
  "appName":"portal","employeeNumber":"2078432","system":"common","connector":"dummy-mcp-stateful","role":"admin"}' >/dev/null
for i in $(seq 1 15); do
  c=$(req GET "$GW/api/admin/mcp/servers/dummy-mcp-stateful/tools" "${H_ADMIN[@]}"); [ "$c" = "200" ] && break; sleep 1
done
# 12-1: 게이트웨이가 세션(initialize→Mcp-Session-Id→initialized→tools/list) 처리 → 200
assert 200 "$c" "stateful tools (세션 처리) → 200"
# 12-2: 세션 없이 직접 tools/list → 400 (서버가 세션 강제)
c=$(req POST "$GW/mcp/dummy-mcp-stateful" "${H_ADMIN[@]}" -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}')
assert 400 "$c" "세션 없이 직접 tools/list → 400 (세션 강제)"

# ── 13. MCP stateful + bearer 동시 (dummy-mcp-both) ──────────
echo "== 13. MCP stateful + bearer (dummy-mcp-both) =="
req DELETE "$GW/api/config/routes/dummy-mcp-both" "${H_ADMIN[@]}" >/dev/null
req POST "$GW/api/config/routes" "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp-both","uri":"http://dummy-mcp-both:8765",
  "predicates":[{"name":"Path","args":{"_genkey_0":"/mcp/dummy-mcp-both"}}],
  "filters":[{"name":"RewritePath","args":{"regexp":"/mcp/dummy-mcp-both","replacement":"/mcp"}}],
  "order":0,"registerSwagger":false}' >/dev/null
req POST "$GW/api/admin/mcp/servers" "${H_ADMIN[@]}" -d '{
  "id":"dummy-mcp-both","name":"Dummy MCP (stateful+bearer)","transport":"streamable-http",
  "authType":"bearer","authToken":"both-secret-789","system":"common"}' >/dev/null
for i in $(seq 1 15); do
  c=$(req GET "$GW/api/admin/mcp/servers/dummy-mcp-both/tools" "${H_ADMIN[@]}"); [ "$c" = "200" ] && break; sleep 1
done
# 13-1: bearer 주입 + 세션 처리 동시 → 200
assert 200 "$c" "bearer+세션 tools → 200"
c=$(req POST "$GW/api/admin/mcp/servers/dummy-mcp-both/tools/call" "${H_ADMIN[@]}" -d '{"name":"add","arguments":{"a":6,"b":7}}')
assert_contains 'Sum = 13' "bearer+세션 tools/call (add=13)"
# 13-2: bearer 없이 직접 → 401
c=$(req POST "$GW/mcp/dummy-mcp-both" -H "app_name: portal" -H "employee_number: 2078432" \
      -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}')
assert 401 "$c" "bearer 없이 직접 → 401"
# 13-3: bearer O + 세션 없이 tools/list → 400
c=$(req POST "$GW/mcp/dummy-mcp-both" -H "Authorization: Bearer both-secret-789" \
      -H "app_name: portal" -H "employee_number: 2078432" -H "Content-Type: application/json" \
      -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}')
assert 400 "$c" "bearer O, 세션 없이 → 400"

# ── 요약 ────────────────────────────────────────────────────
echo ""
echo "════════════════════════════════════════"
printf "  PASS=%s  FAIL=%s\n" "$PASS" "$FAIL"
echo "════════════════════════════════════════"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
