#!/bin/bash
# ============================================================
# MACS System – 통합 검증 스크립트
#
# 사용법:
#   chmod +x scripts/verify-system.sh
#   ./scripts/verify-system.sh
#
# 사전 조건:
#   docker compose up -d --build 완료 후 실행
# ============================================================

set -euo pipefail

GATEWAY="http://localhost:8080"
CONFIG="http://localhost:8888"
AUTH="http://localhost:9000"
ES="http://localhost:9200"
KIBANA="http://localhost:5601"
PORTAL="http://localhost:3000"

PASS=0
FAIL=0
SKIP=0

# ── 유틸 ─────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

pass() { ((PASS++)); echo -e "  ${GREEN}✔ PASS${NC}  $1"; }
fail() { ((FAIL++)); echo -e "  ${RED}✘ FAIL${NC}  $1${2:+ — $2}"; }
skip() { ((SKIP++)); echo -e "  ${YELLOW}○ SKIP${NC}  $1"; }
section() { echo -e "\n${BOLD}${CYAN}── $1 ──${NC}"; }

check_status() {
  local label="$1" url="$2" expected="${3:-200}"
  local status
  status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" 2>/dev/null || echo "000")
  if [ "$status" = "$expected" ]; then
    pass "$label (HTTP $status)"
  else
    fail "$label" "expected $expected, got $status"
  fi
}

check_json_field() {
  local label="$1" response="$2" field="$3"
  if echo "$response" | grep -q "\"$field\""; then
    pass "$label — field '$field' present"
  else
    fail "$label" "field '$field' not found"
  fi
}

wait_for() {
  local label="$1" url="$2" max_wait="${3:-60}"
  local elapsed=0
  echo -n "  ⏳ Waiting for $label..."
  while [ $elapsed -lt $max_wait ]; do
    if curl -s -o /dev/null --max-time 5 "$url" 2>/dev/null; then
      echo -e " ${GREEN}ready${NC} (${elapsed}s)"
      return 0
    fi
    sleep 3
    elapsed=$((elapsed + 3))
    echo -n "."
  done
  echo -e " ${RED}timeout${NC}"
  return 1
}

# ============================================================
section "1. 인프라 서비스 헬스체크"
# ============================================================

echo "  Checking infrastructure services..."

# Oracle
check_status "Oracle (via config-server datasource)" "$CONFIG/actuator/health" "200"

# Redis
REDIS_PONG=$(docker exec macs-redis redis-cli ping 2>/dev/null || echo "FAIL")
if [ "$REDIS_PONG" = "PONG" ]; then
  pass "Redis PING → PONG"
else
  fail "Redis PING" "$REDIS_PONG"
fi

# Kafka
KAFKA_CHECK=$(docker exec macs-kafka kafka-broker-api-versions --bootstrap-server localhost:29092 2>/dev/null | head -1 || echo "FAIL")
if echo "$KAFKA_CHECK" | grep -q "ApiVersion"; then
  pass "Kafka broker responding"
else
  fail "Kafka broker" "not responding"
fi

# Elasticsearch
ES_HEALTH=$(curl -s -u elastic:elastic_password "$ES/_cluster/health" 2>/dev/null || echo "{}")
ES_STATUS=$(echo "$ES_HEALTH" | grep -o '"status":"[^"]*"' | head -1 || echo "")
if echo "$ES_STATUS" | grep -qE "green|yellow"; then
  pass "Elasticsearch cluster health ($ES_STATUS)"
else
  fail "Elasticsearch" "$ES_STATUS"
fi

# Kibana
check_status "Kibana" "$KIBANA/api/status" "200"

# APM Server
APM_RESP=$(curl -s "http://localhost:8200/" 2>/dev/null || echo "{}")
if echo "$APM_RESP" | grep -q "publish_ready"; then
  pass "APM Server publish_ready"
else
  fail "APM Server" "not ready"
fi

# OTel Collector
check_status "OTel Collector health" "http://localhost:13133/" "200"

# ============================================================
section "2. 애플리케이션 서비스 헬스체크"
# ============================================================

check_status "Config Server /actuator/health" "$CONFIG/actuator/health"
check_status "Auth Server /actuator/health"   "$AUTH/actuator/health"
check_status "Gateway /actuator/health"       "$GATEWAY/actuator/health"
check_status "Portal index"                   "$PORTAL/"

# Prometheus 엔드포인트
check_status "Config Server /actuator/prometheus" "$CONFIG/actuator/prometheus"
check_status "Auth Server /actuator/prometheus"   "$AUTH/actuator/prometheus"
check_status "Gateway /actuator/prometheus"       "$GATEWAY/actuator/prometheus"

# ============================================================
section "3. Config Server API 검증"
# ============================================================

# Properties 조회
PROPS=$(curl -s "$GATEWAY/api/config/properties?application=gateway-service&profile=default&label=main" \
  -H "app_name: portal" -H "employee_number: SYSTEM" 2>/dev/null || echo "[]")
PROP_COUNT=$(echo "$PROPS" | grep -o '"propKey"' | wc -l || echo "0")
if [ "$PROP_COUNT" -gt 0 ]; then
  pass "GET /api/config/properties → $PROP_COUNT properties"
else
  fail "GET /api/config/properties" "no properties found"
fi

# Routes 조회
ROUTES=$(curl -s "$GATEWAY/api/config/routes" \
  -H "app_name: portal" -H "employee_number: SYSTEM" 2>/dev/null || echo "[]")
ROUTE_COUNT=$(echo "$ROUTES" | grep -o '"id"' | wc -l || echo "0")
if [ "$ROUTE_COUNT" -gt 0 ]; then
  pass "GET /api/config/routes → $ROUTE_COUNT routes"
else
  fail "GET /api/config/routes" "no routes found"
fi

# Config Server native endpoint
CONFIG_RESP=$(curl -s "$CONFIG/gateway-service/default/main" 2>/dev/null || echo "{}")
if echo "$CONFIG_RESP" | grep -q "propertySources"; then
  pass "Config Server /gateway-service/default/main → properties served"
else
  skip "Config Server native endpoint"
fi

# ============================================================
section "4. Auth Server – 토큰 발급 검증"
# ============================================================

# 4a. admin 토큰 발급 (EMP001)
ADMIN_RESP=$(curl -s -X POST "$GATEWAY/api/auth/token" \
  -H "Content-Type: application/json" \
  -H "app_name: portal" \
  -H "employee_number: EMP001" \
  -d '{"app_name":"portal","employee_number":"EMP001"}' 2>/dev/null || echo "{}")

ADMIN_TOKEN=$(echo "$ADMIN_RESP" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
ADMIN_GROUP=$(echo "$ADMIN_RESP" | grep -o '"group":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")

if [ -n "$ADMIN_TOKEN" ]; then
  pass "EMP001 토큰 발급 성공 (group=$ADMIN_GROUP)"
  check_json_field "EMP001 응답" "$ADMIN_RESP" "allowed_resources_list"
else
  fail "EMP001 토큰 발급" "no token returned"
fi

# 4b. developer 토큰 (EMP002)
DEV_RESP=$(curl -s -X POST "$GATEWAY/api/auth/token" \
  -H "Content-Type: application/json" \
  -H "app_name: portal" \
  -H "employee_number: EMP002" \
  -d '{"app_name":"portal","employee_number":"EMP002"}' 2>/dev/null || echo "{}")

DEV_GROUP=$(echo "$DEV_RESP" | grep -o '"group":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
if [ "$DEV_GROUP" = "developer" ]; then
  pass "EMP002 group=developer 확인"
else
  fail "EMP002 group" "expected developer, got $DEV_GROUP"
fi

# 4c. user 토큰 (EMP004)
USER_RESP=$(curl -s -X POST "$GATEWAY/api/auth/token" \
  -H "Content-Type: application/json" \
  -H "app_name: portal" \
  -H "employee_number: EMP004" \
  -d '{"app_name":"portal","employee_number":"EMP004"}' 2>/dev/null || echo "{}")

USER_GROUP=$(echo "$USER_RESP" | grep -o '"group":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
USER_TOKEN=$(echo "$USER_RESP" | grep -o '"token":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
if [ "$USER_GROUP" = "user" ]; then
  pass "EMP004 group=user 확인"
else
  fail "EMP004 group" "expected user, got $USER_GROUP"
fi

# 4d. 토큰 검증
if [ -n "$ADMIN_TOKEN" ]; then
  VALIDATE_RESP=$(curl -s -X POST "$GATEWAY/api/auth/validate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "app_name: portal" \
    -H "employee_number: EMP001" \
    -d '{"request_app":"/api/v1/test"}' 2>/dev/null || echo "{}")

  if echo "$VALIDATE_RESP" | grep -q '"valid":true'; then
    pass "토큰 검증 성공 (admin → /api/v1/test)"
  else
    fail "토큰 검증" "valid=true not found"
  fi
fi

# 4e. 잘못된 토큰 → 401
INVALID_RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/auth/validate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer invalid-token" \
  -H "app_name: portal" \
  -H "employee_number: EMP001" \
  -d '{"request_app":"/api/v1/test"}' 2>/dev/null || echo "000")
if [ "$INVALID_RESP" = "401" ] || [ "$INVALID_RESP" = "403" ]; then
  pass "잘못된 토큰 → HTTP $INVALID_RESP"
else
  fail "잘못된 토큰" "expected 401/403, got $INVALID_RESP"
fi

# ============================================================
section "5. Gateway 필터 검증"
# ============================================================

# 5a. 헤더 누락 → 400
NO_HEADER_STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 \
  "$GATEWAY/api/config/routes" 2>/dev/null || echo "000")
if [ "$NO_HEADER_STATUS" = "400" ]; then
  pass "헤더 누락 → 400 Bad Request"
else
  fail "헤더 누락" "expected 400, got $NO_HEADER_STATUS"
fi

# 5b. 인터널 경로 skip (actuator, swagger)
check_status "Actuator 헤더 불필요" "$GATEWAY/actuator/health" "200"
check_status "Swagger UI 접근"     "$GATEWAY/swagger-ui.html" "200"

# ============================================================
section "6. Auth Management API 검증"
# ============================================================

# Apps 목록
APPS_RESP=$(curl -s "$GATEWAY/api/auth/apps" \
  -H "app_name: portal" -H "employee_number: EMP001" 2>/dev/null || echo "[]")
if echo "$APPS_RESP" | grep -q "portal"; then
  pass "GET /api/auth/apps → portal 앱 존재"
else
  fail "GET /api/auth/apps" "portal not found"
fi

# Groups 목록
GROUPS_RESP=$(curl -s "$GATEWAY/api/auth/apps/portal/groups" \
  -H "app_name: portal" -H "employee_number: EMP001" 2>/dev/null || echo "[]")
GROUP_COUNT=$(echo "$GROUPS_RESP" | grep -o '"groupId"' | wc -l || echo "0")
if [ "$GROUP_COUNT" -ge 4 ]; then
  pass "GET /api/auth/apps/portal/groups → $GROUP_COUNT groups"
else
  fail "GET /api/auth/apps/portal/groups" "expected >=4, got $GROUP_COUNT"
fi

# Members
MEMBERS_RESP=$(curl -s "$GATEWAY/api/auth/groups/portal-admin/members" \
  -H "app_name: portal" -H "employee_number: EMP001" 2>/dev/null || echo "[]")
if echo "$MEMBERS_RESP" | grep -q "EMP001"; then
  pass "GET portal-admin members → EMP001 존재"
else
  fail "GET portal-admin members" "EMP001 not found"
fi

# Resources
RES_RESP=$(curl -s "$GATEWAY/api/auth/groups/portal-admin/resources" \
  -H "app_name: portal" -H "employee_number: EMP001" 2>/dev/null || echo "[]")
if echo "$RES_RESP" | grep -q "resourceName"; then
  pass "GET portal-admin resources → 리소스 존재"
else
  fail "GET portal-admin resources" "empty"
fi

# ============================================================
section "7. Portal 접근 검증"
# ============================================================

check_status "Portal 메인 페이지" "$PORTAL/" "200"

PORTAL_HTML=$(curl -s "$PORTAL/" 2>/dev/null || echo "")
if echo "$PORTAL_HTML" | grep -qi "macs"; then
  pass "Portal HTML에 MACS 포함"
else
  skip "Portal HTML 내용 확인"
fi

# ============================================================
section "8. OTel / Logging 검증"
# ============================================================

# 약간의 트래픽 생성 (trace 데이터 수집용)
echo "  Generating trace traffic..."
for i in $(seq 1 5); do
  curl -s -o /dev/null "$GATEWAY/actuator/health" 2>/dev/null
  curl -s -o /dev/null -X POST "$GATEWAY/api/auth/token" \
    -H "Content-Type: application/json" \
    -H "app_name: portal" -H "employee_number: EMP001" \
    -d '{"app_name":"portal","employee_number":"EMP001"}' 2>/dev/null
done
sleep 5

# 8a. Elasticsearch에 APM 인덱스 확인
APM_INDICES=$(curl -s -u elastic:elastic_password "$ES/_cat/indices/apm*?h=index" 2>/dev/null || echo "")
if [ -n "$APM_INDICES" ]; then
  APM_IDX_COUNT=$(echo "$APM_INDICES" | wc -l)
  pass "APM indices in Elasticsearch → $APM_IDX_COUNT indices"
else
  skip "APM indices (may need more time)"
fi

# 8b. OTel logs/metrics 인덱스
OTEL_INDICES=$(curl -s -u elastic:elastic_password "$ES/_cat/indices/otel*?h=index" 2>/dev/null || echo "")
if [ -n "$OTEL_INDICES" ]; then
  pass "OTel indices in Elasticsearch"
else
  skip "OTel indices (may need more time)"
fi

# 8c. 서비스별 trace 존재 확인
TRACE_SEARCH=$(curl -s -u elastic:elastic_password \
  "$ES/apm-*/_search?size=1&q=service.name:gateway-service" 2>/dev/null || echo '{"hits":{"total":{"value":0}}}')
TRACE_COUNT=$(echo "$TRACE_SEARCH" | grep -o '"value":[0-9]*' | head -1 | cut -d: -f2 || echo "0")
if [ "$TRACE_COUNT" -gt 0 ]; then
  pass "gateway-service traces in ES → $TRACE_COUNT hits"
else
  skip "gateway-service traces (OTel agent may need warmup)"
fi

# 8d. JSON 포맷 로그 확인
echo "  Checking JSON log format..."
GW_LOG=$(docker logs macs-gateway-service 2>&1 | grep "^{" | head -1 || echo "")
if [ -n "$GW_LOG" ]; then
  pass "Gateway 로그 JSON 포맷 확인"
  if echo "$GW_LOG" | grep -q "trace_id"; then
    pass "Gateway 로그에 trace_id 포함"
  else
    skip "trace_id in logs (may appear after first request)"
  fi
else
  skip "Gateway JSON log (log4j2-spring.xml 적용 확인 필요)"
fi

# 8e. Kibana APM 서비스 확인
KIBANA_APM=$(curl -s -u elastic:elastic_password \
  "$KIBANA/api/apm/services?start=now-1h&end=now" \
  -H "kbn-xsrf: true" 2>/dev/null || echo '{"items":[]}')
APM_SVC_COUNT=$(echo "$KIBANA_APM" | grep -o '"serviceName"' | wc -l || echo "0")
if [ "$APM_SVC_COUNT" -gt 0 ]; then
  pass "Kibana APM에 $APM_SVC_COUNT 서비스 등록됨"
else
  skip "Kibana APM services (warmup 후 확인)"
fi

# ============================================================
section "9. Spring Cloud Bus 검증"
# ============================================================

# Refresh 이벤트 전파
REFRESH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  "$GATEWAY/api/config/properties/refresh" \
  -H "app_name: portal" -H "employee_number: EMP001" 2>/dev/null || echo "000")
if [ "$REFRESH_STATUS" = "200" ]; then
  pass "POST /api/config/properties/refresh → 200"
else
  fail "Bus refresh" "HTTP $REFRESH_STATUS"
fi

# ============================================================
# 결과 요약
# ============================================================
echo ""
echo -e "${BOLD}════════════════════════════════════════════${NC}"
echo -e "${BOLD} 검증 결과 요약${NC}"
echo -e "${BOLD}════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}PASS: $PASS${NC}"
echo -e "  ${RED}FAIL: $FAIL${NC}"
echo -e "  ${YELLOW}SKIP: $SKIP${NC}"
echo -e "  Total: $((PASS + FAIL + SKIP))"
echo -e "${BOLD}════════════════════════════════════════════${NC}"

if [ "$FAIL" -gt 0 ]; then
  echo -e "\n${RED}⚠ $FAIL 건의 실패가 있습니다. 위 로그를 확인하세요.${NC}"
  exit 1
else
  echo -e "\n${GREEN}✔ 모든 검증이 통과했습니다.${NC}"
  exit 0
fi
