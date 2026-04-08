#!/bin/bash
# ============================================================
# MACS Portal – 인증/권한 시나리오 검증
#
# Gateway를 통한 토큰 발급 → 그룹별 권한 → 리소스 접근 확인
# ============================================================

set -euo pipefail

GATEWAY="http://localhost:8080"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

PASS=0; FAIL=0

pass() { ((PASS++)); echo -e "  ${GREEN}✔${NC} $1"; }
fail() { ((FAIL++)); echo -e "  ${RED}✘${NC} $1 — $2"; }
section() { echo -e "\n${BOLD}${CYAN}── $1 ──${NC}"; }

# 토큰 발급 함수
issue_token() {
  local emp="$1"
  curl -s -X POST "$GATEWAY/api/auth/token" \
    -H "Content-Type: application/json" \
    -H "app_name: portal" \
    -H "employee_number: $emp" \
    -d "{\"app_name\":\"portal\",\"employee_number\":\"$emp\"}" 2>/dev/null
}

extract() {
  echo "$1" | grep -o "\"$2\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}

extract_list() {
  echo "$1" | grep -o "\"$2\":\[[^]]*\]" | head -1
}

# ============================================================
section "시나리오 1: Admin (EMP001) – 전체 권한"
# ============================================================

RESP=$(issue_token "EMP001")
TOKEN=$(extract "$RESP" "token")
GROUP=$(extract "$RESP" "group")
RESOURCES=$(extract_list "$RESP" "allowed_resources_list")

echo "  Token: ${TOKEN:0:20}..."
echo "  Group: $GROUP"
echo "  Resources: $RESOURCES"

[ "$GROUP" = "admin" ] && pass "group = admin" || fail "group" "expected admin, got $GROUP"
[ -n "$TOKEN" ] && pass "토큰 발급 성공" || fail "토큰 발급" "empty token"

# admin은 /api/**, /admin/**, /actuator/** 등 모두 접근 가능
for path in "/api/v1/test" "/admin/dashboard" "/actuator/health" "/portal/admin/config"; do
  VALID=$(curl -s -X POST "$GATEWAY/api/auth/validate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -H "app_name: portal" -H "employee_number: EMP001" \
    -d "{\"request_app\":\"$path\"}" 2>/dev/null)
  if echo "$VALID" | grep -q '"valid":true'; then
    pass "admin 접근 허용: $path"
  else
    fail "admin 접근" "$path denied"
  fi
done

# ============================================================
section "시나리오 2: Developer (EMP002) – 제한적 접근"
# ============================================================

RESP=$(issue_token "EMP002")
TOKEN=$(extract "$RESP" "token")
GROUP=$(extract "$RESP" "group")

[ "$GROUP" = "developer" ] && pass "group = developer" || fail "group" "got $GROUP"

# developer는 /api/**, /swagger-ui/**, /v3/api-docs/**, /portal/** 접근 가능
for path in "/api/v1/users" "/swagger-ui/index.html" "/portal/dashboard"; do
  VALID=$(curl -s -X POST "$GATEWAY/api/auth/validate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -H "app_name: portal" -H "employee_number: EMP002" \
    -d "{\"request_app\":\"$path\"}" 2>/dev/null)
  if echo "$VALID" | grep -q '"valid":true'; then
    pass "developer 접근 허용: $path"
  else
    fail "developer 접근" "$path — might be denied by resource pattern"
  fi
done

# developer는 /admin/** 접근 불가
ADMIN_CHECK=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/auth/validate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "app_name: portal" -H "employee_number: EMP002" \
  -d '{"request_app":"/admin/settings"}' 2>/dev/null)
if [ "$ADMIN_CHECK" = "403" ]; then
  pass "developer /admin/** 접근 차단 (403)"
else
  fail "developer /admin/** 차단" "expected 403, got $ADMIN_CHECK"
fi

# ============================================================
section "시나리오 3: User (EMP004) – 최소 권한"
# ============================================================

RESP=$(issue_token "EMP004")
TOKEN=$(extract "$RESP" "token")
GROUP=$(extract "$RESP" "group")
RESOURCES=$(extract_list "$RESP" "allowed_resources_list")

[ "$GROUP" = "user" ] && pass "group = user" || fail "group" "got $GROUP"
echo "  Resources: $RESOURCES"

# user는 /api/v1/** 과 /portal/** 만 접근 가능
VALID=$(curl -s -X POST "$GATEWAY/api/auth/validate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "app_name: portal" -H "employee_number: EMP004" \
  -d '{"request_app":"/api/v1/data"}' 2>/dev/null)
if echo "$VALID" | grep -q '"valid":true'; then
  pass "user /api/v1/data 접근 허용"
else
  fail "user /api/v1/data" "denied"
fi

# user는 /admin/**, /actuator/** 접근 불가
for path in "/admin/users" "/actuator/prometheus"; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/auth/validate" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -H "app_name: portal" -H "employee_number: EMP004" \
    -d "{\"request_app\":\"$path\"}" 2>/dev/null)
  if [ "$STATUS" = "403" ]; then
    pass "user $path 접근 차단 (403)"
  else
    fail "user $path 차단" "expected 403, got $STATUS"
  fi
done

# ============================================================
section "시나리오 4: 토큰 만료/위변조"
# ============================================================

# 위변조 토큰
FAKE_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/auth/validate" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.FAKE.INVALID" \
  -H "app_name: portal" -H "employee_number: EMP001" \
  -d '{"request_app":"/api/v1/test"}' 2>/dev/null)
[ "$FAKE_STATUS" = "401" ] && pass "위변조 토큰 → 401" || fail "위변조 토큰" "got $FAKE_STATUS"

# Authorization 헤더 없이
NO_AUTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/auth/validate" \
  -H "Content-Type: application/json" \
  -H "app_name: portal" -H "employee_number: EMP001" \
  -d '{"request_app":"/api/v1/test"}' 2>/dev/null)
[ "$NO_AUTH_STATUS" = "401" ] && pass "Authorization 없음 → 401" || fail "Authorization 없음" "got $NO_AUTH_STATUS"

# ============================================================
section "시나리오 5: 권한 관리 CRUD"
# ============================================================

ADMIN_RESP=$(issue_token "EMP001")
ADMIN_TOKEN=$(extract "$ADMIN_RESP" "token")
AUTH_HEADERS="-H 'Authorization: Bearer $ADMIN_TOKEN' -H 'app_name: portal' -H 'employee_number: EMP001'"

# 그룹에 테스트 멤버 추가
ADD_MEMBER=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/auth/groups/portal-user/members" \
  -H "Content-Type: application/json" \
  -H "app_name: portal" -H "employee_number: EMP001" \
  -d '{"employeeNumber":"EMP099"}' 2>/dev/null)
[ "$ADD_MEMBER" = "201" ] && pass "멤버 추가 EMP099 → portal-user (201)" || fail "멤버 추가" "got $ADD_MEMBER"

# 멤버 확인
MEMBERS=$(curl -s "$GATEWAY/api/auth/groups/portal-user/members" \
  -H "app_name: portal" -H "employee_number: EMP001" 2>/dev/null)
if echo "$MEMBERS" | grep -q "EMP099"; then
  pass "멤버 EMP099 조회 확인"
else
  fail "멤버 조회" "EMP099 not found"
fi

# 멤버 삭제
DEL_MEMBER=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
  "$GATEWAY/api/auth/groups/portal-user/members/EMP099" \
  -H "app_name: portal" -H "employee_number: EMP001" 2>/dev/null)
[ "$DEL_MEMBER" = "204" ] && pass "멤버 삭제 EMP099 (204)" || fail "멤버 삭제" "got $DEL_MEMBER"

# 리소스 추가
ADD_RES=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY/api/auth/groups/portal-user/resources" \
  -H "Content-Type: application/json" \
  -H "app_name: portal" -H "employee_number: EMP001" \
  -d '{"resourceName":"/api/test/**"}' 2>/dev/null)
[ "$ADD_RES" = "201" ] && pass "리소스 추가 /api/test/** → portal-user (201)" || fail "리소스 추가" "got $ADD_RES"

# 리소스 삭제
DEL_RES=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE \
  "$GATEWAY/api/auth/groups/portal-user/resources?resourceName=/api/test/**" \
  -H "app_name: portal" -H "employee_number: EMP001" 2>/dev/null)
[ "$DEL_RES" = "204" ] && pass "리소스 삭제 /api/test/** (204)" || fail "리소스 삭제" "got $DEL_RES"

# ============================================================
# 결과
# ============================================================
echo ""
echo -e "${BOLD}════════════════════════════════════════════${NC}"
echo -e "${BOLD} Portal 인증/권한 검증 결과${NC}"
echo -e "${BOLD}════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}PASS: $PASS${NC}   ${RED}FAIL: $FAIL${NC}"
echo -e "${BOLD}════════════════════════════════════════════${NC}"

[ "$FAIL" -gt 0 ] && exit 1 || exit 0
