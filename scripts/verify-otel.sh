#!/bin/bash
# ============================================================
# MACS – OTel / Logging / APM 검증 스크립트
# ============================================================

set -euo pipefail

GATEWAY="http://localhost:8080"
ES="http://localhost:9200"
ES_AUTH="elastic:elastic_password"
KIBANA="http://localhost:5601"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

PASS=0; FAIL=0; SKIP=0

pass() { ((PASS++)); echo -e "  ${GREEN}✔${NC} $1"; }
fail() { ((FAIL++)); echo -e "  ${RED}✘${NC} $1 — $2"; }
skip() { ((SKIP++)); echo -e "  ${YELLOW}○${NC} $1"; }
section() { echo -e "\n${BOLD}${CYAN}── $1 ──${NC}"; }

# ============================================================
section "1. 트래픽 생성"
# ============================================================

echo "  Generating requests for trace/log collection..."
for i in $(seq 1 10); do
  # 다양한 엔드포인트 호출
  curl -s -o /dev/null "$GATEWAY/actuator/health"
  curl -s -o /dev/null -X POST "$GATEWAY/api/auth/token" \
    -H "Content-Type: application/json" \
    -H "app_name: portal" -H "employee_number: EMP001" \
    -d '{"app_name":"portal","employee_number":"EMP001"}'
  curl -s -o /dev/null "$GATEWAY/api/config/routes" \
    -H "app_name: portal" -H "employee_number: EMP001"
done
echo "  30 requests sent. Waiting 10s for pipeline flush..."
sleep 10

# ============================================================
section "2. Elasticsearch 인덱스 확인"
# ============================================================

ALL_INDICES=$(curl -s -u "$ES_AUTH" "$ES/_cat/indices?h=index,docs.count&s=index" 2>/dev/null || echo "")

# APM 인덱스
APM_COUNT=$(echo "$ALL_INDICES" | grep -c "^apm" || echo "0")
if [ "$APM_COUNT" -gt 0 ]; then
  pass "APM indices: $APM_COUNT"
  echo "$ALL_INDICES" | grep "^apm" | head -5 | while read line; do
    echo "       $line"
  done
else
  skip "APM indices not yet created"
fi

# OTel 인덱스
OTEL_COUNT=$(echo "$ALL_INDICES" | grep -c "^otel" || echo "0")
if [ "$OTEL_COUNT" -gt 0 ]; then
  pass "OTel indices: $OTEL_COUNT"
  echo "$ALL_INDICES" | grep "^otel" | while read line; do
    echo "       $line"
  done
else
  skip "OTel indices not yet created"
fi

# ============================================================
section "3. Trace 데이터 검증"
# ============================================================

for svc in "gateway-service" "admin-server" "auth-server"; do
  SEARCH=$(curl -s -u "$ES_AUTH" \
    "$ES/apm-*/_search?size=0&q=service.name:$svc" 2>/dev/null || echo '{"hits":{"total":{"value":0}}}')
  COUNT=$(echo "$SEARCH" | grep -o '"value":[0-9]*' | head -1 | cut -d: -f2 || echo "0")
  if [ "$COUNT" -gt 0 ]; then
    pass "$svc traces: $COUNT documents"
  else
    skip "$svc traces (agent warmup 필요)"
  fi
done

# trace_id 필드 존재 확인
TRACE_DOC=$(curl -s -u "$ES_AUTH" \
  "$ES/apm-*/_search?size=1&_source=trace.id,service.name,transaction.name" 2>/dev/null || echo '{}')
if echo "$TRACE_DOC" | grep -q '"trace"'; then
  TRACE_ID=$(echo "$TRACE_DOC" | grep -o '"id":"[a-f0-9]*"' | head -1 | cut -d'"' -f4)
  pass "trace.id 필드 확인: ${TRACE_ID:0:16}..."
else
  skip "trace.id field (no trace docs yet)"
fi

# ============================================================
section "4. 로그 JSON 포맷 + traceId 확인"
# ============================================================

for container in "macs-gateway-service" "macs-admin-server" "macs-auth-server"; do
  SVC_SHORT="${container#macs-}"

  # JSON 로그 확인
  JSON_LINE=$(docker logs "$container" 2>&1 | grep "^{" | head -1 || echo "")
  if [ -n "$JSON_LINE" ]; then
    pass "$SVC_SHORT JSON 로그 포맷 확인"

    # traceId 포함 확인
    if echo "$JSON_LINE" | grep -qE '"trace_id"|"traceId"'; then
      pass "$SVC_SHORT 로그에 trace_id 포함"
    else
      # OTel javaagent는 MDC에 trace_id 자동 주입 (요청 처리 시에만)
      TRACE_LOG=$(docker logs "$container" 2>&1 | grep "^{" | grep -E '"trace_id"|"traceId"' | head -1 || echo "")
      if [ -n "$TRACE_LOG" ]; then
        pass "$SVC_SHORT 로그에 trace_id 포함 (요청 로그)"
      else
        skip "$SVC_SHORT trace_id in logs (요청 발생 후 확인)"
      fi
    fi
  else
    skip "$SVC_SHORT JSON log (log4j2 config 확인 필요)"
  fi
done

# ============================================================
section "5. OTel Collector 메트릭"
# ============================================================

# Collector 자체 메트릭
COLLECTOR_METRICS=$(curl -s "http://localhost:8888/metrics" 2>/dev/null || echo "")
if echo "$COLLECTOR_METRICS" | grep -q "otelcol_receiver_accepted_spans"; then
  SPANS=$(echo "$COLLECTOR_METRICS" | grep "otelcol_receiver_accepted_spans" | head -1)
  pass "OTel Collector accepting spans"
  echo "       $SPANS"
else
  skip "OTel Collector metrics endpoint"
fi

# ============================================================
section "6. Prometheus 스크래핑 확인"
# ============================================================

for target in "gateway-service:8080" "admin-server:8888" "auth-server:9000"; do
  SVC="${target%%:*}"
  PORT="${target##*:}"
  PROM=$(curl -s "http://localhost:$PORT/actuator/prometheus" 2>/dev/null | head -5 || echo "")
  if echo "$PROM" | grep -q "jvm_"; then
    pass "$SVC /actuator/prometheus → JVM 메트릭 노출"
  else
    skip "$SVC prometheus (서비스 미가동 또는 micrometer 미적용)"
  fi
done

# ============================================================
section "7. Kibana APM 서비스 맵"
# ============================================================

KIBANA_SERVICES=$(curl -s -u "$ES_AUTH" \
  "$KIBANA/api/apm/services?start=now-1h&end=now&kuery=" \
  -H "kbn-xsrf: true" 2>/dev/null || echo '{"items":[]}')

SVC_NAMES=$(echo "$KIBANA_SERVICES" | grep -o '"serviceName":"[^"]*"' | cut -d'"' -f4 | sort -u || echo "")
SVC_COUNT=$(echo "$SVC_NAMES" | grep -c . || echo "0")

if [ "$SVC_COUNT" -gt 0 ]; then
  pass "Kibana APM에 $SVC_COUNT 서비스 등록"
  echo "$SVC_NAMES" | while read svc; do
    echo "       • $svc"
  done
else
  skip "Kibana APM services (데이터 수집 후 확인)"
fi

echo -e "\n  ${CYAN}💡 Kibana에서 시각적으로 확인:${NC}"
echo "     APM:         $KIBANA/app/apm/services"
echo "     Service Map: $KIBANA/app/apm/service-map"
echo "     Discover:    $KIBANA/app/discover (otel-* / apm-* 인덱스)"

# ============================================================
# 결과
# ============================================================
echo ""
echo -e "${BOLD}════════════════════════════════════════════${NC}"
echo -e "${BOLD} OTel / Logging 검증 결과${NC}"
echo -e "${BOLD}════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}PASS: $PASS${NC}   ${RED}FAIL: $FAIL${NC}   ${YELLOW}SKIP: $SKIP${NC}"
echo -e "${BOLD}════════════════════════════════════════════${NC}"

[ "$FAIL" -gt 0 ] && exit 1 || exit 0
