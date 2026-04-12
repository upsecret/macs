#!/bin/bash
# ES 기동 후 내장 유저 비밀번호 설정
# docker-compose.yml에서 kibana 기동 전(es-setup 이전)에 실행할 때 사용

ES_HOST="http://localhost:9200"
ES_PASS="elastic_password"

echo "Waiting for Elasticsearch..."
until curl -sf -u "elastic:$ES_PASS" "$ES_HOST/_cluster/health" > /dev/null 2>&1; do
  sleep 3
done

echo "Setting kibana_system password..."
curl -s -X POST -u "elastic:$ES_PASS" \
  "$ES_HOST/_security/user/kibana_system/_password" \
  -H "Content-Type: application/json" \
  -d "{\"password\":\"$ES_PASS\"}"

echo ""
echo "Done."
