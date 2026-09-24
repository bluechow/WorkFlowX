#!/bin/bash
# ============================================================
# WorkFlowX 部署 Smoke Test（Phase 16; P16-28）
# 14 步真实 HTTP 贯穿：health→login→me→org→project→member→issue→
# transition→comment→upload→download→notification→dashboard→audit
# 用法: bash scripts/deploy/smoke.sh [BASE_URL]
# 默认 BASE_URL=http://localhost:8081（部署前端 Nginx 入口）
# ============================================================
set -u
BASE="${1:-http://localhost:8081}"
PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "  ok $1"; }
fail(){ FAIL=$((FAIL+1)); echo "  FAIL $1"; exit 1; }

echo "===== Deployment Smoke Test → $BASE ====="

# 1. health
C=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/health")
[ "$C" = "200" ] && ok "1. health" || fail "1. health ($C)"

# 2. login
LOGIN=$(curl -s -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123456"}')
TOKEN=$(echo "$LOGIN" | python -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null)
[ -n "$TOKEN" ] && ok "2. login" || fail "2. login"
H="Authorization: Bearer $TOKEN"

# 3. me
C=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/auth/me" -H "$H")
[ "$C" = "200" ] && ok "3. me" || fail "3. me ($C)"

# 4-6. org → project → member
SS=$(date +%s)
ORG=$(curl -s -X POST "$BASE/api/v1/orgs" -H "$H" -H "Content-Type: application/json" \
  -d "{\"name\":\"SMOKE org\",\"code\":\"SMOKEORG$SS\",\"description\":null}")
ORG_ID=$(echo "$ORG" | python -c "import sys,json;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
[ -n "$ORG_ID" ] && ok "4. org id=$ORG_ID" || fail "4. org"
PROJ=$(curl -s -X POST "$BASE/api/v1/projects" -H "$H" -H "Content-Type: application/json" \
  -d "{\"name\":\"SMOKE proj\",\"key\":\"SMOKEP$SS\",\"orgId\":$ORG_ID,\"description\":null}")
PROJ_ID=$(echo "$PROJ" | python -c "import sys,json;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
[ -n "$PROJ_ID" ] && ok "5. project id=$PROJ_ID" || fail "5. project"
C=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/orgs/$ORG_ID/members" \
  -H "$H" -H "Content-Type: application/json" -d '{"userId":2,"role":"MEMBER","departmentId":null}')
[ "$C" = "200" -o "$C" = "201" ] && ok "6a. org member" || fail "6a. org member ($C)"
C=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/projects/$PROJ_ID/members" \
  -H "$H" -H "Content-Type: application/json" -d '{"userId":2,"role":"MEMBER"}')
[ "$C" = "200" -o "$C" = "201" ] && ok "6b. project member" || fail "6b. project member ($C)"

# 7. issue
ISSUE=$(curl -s -X POST "$BASE/api/v1/projects/$PROJ_ID/issues" -H "$H" -H "Content-Type: application/json" \
  -d '{"title":"SMOKE issue","type":"TASK","priority":"HIGH","assigneeId":2}')
ISSUE_ID=$(echo "$ISSUE" | python -c "import sys,json;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
[ -n "$ISSUE_ID" ] && ok "7. issue id=$ISSUE_ID" || fail "7. issue"

# 8. transition OPEN→IN_PROGRESS
C=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE/api/v1/projects/$PROJ_ID/issues/$ISSUE_ID/status" \
  -H "$H" -H "Content-Type: application/json" -d '{"fromStatus":"OPEN","toStatus":"IN_PROGRESS"}')
[ "$C" = "200" ] && ok "8. transition" || fail "8. transition ($C)"

# 9. comment
C=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/projects/$PROJ_ID/issues/$ISSUE_ID/comments" \
  -H "$H" -H "Content-Type: application/json" -d '{"content":"SMOKE comment"}')
[ "$C" = "201" ] && ok "9. comment" || fail "9. comment ($C)"

# 10-11. upload → download 一致性
# 注意: Windows System32 curl 不识别 MSYS /tmp 路径——使用当前目录相对路径（P11 教训）
printf 'SMOKE attachment payload' > .smoke-att.txt
UP=$(curl -s -X POST "$BASE/api/v1/projects/$PROJ_ID/issues/$ISSUE_ID/attachments" \
  -H "$H" -F "file=@.smoke-att.txt;type=text/plain")
rm -f .smoke-att.txt
ATT_ID=$(echo "$UP" | python -c "import sys,json;print(json.load(sys.stdin)['data']['id'])" 2>/dev/null)
[ -n "$ATT_ID" ] && ok "10. upload" || fail "10. upload"
DL=$(curl -s "$BASE/api/v1/projects/$PROJ_ID/issues/$ISSUE_ID/attachments/$ATT_ID/download" -H "$H")
[ "$DL" = "SMOKE attachment payload" ] && ok "11. download byte-equal" || fail "11. download"
# 清理该附件
curl -s -o /dev/null -X DELETE "$BASE/api/v1/projects/$PROJ_ID/issues/$ISSUE_ID/attachments/$ATT_ID" -H "$H"

# 12. notification（分派 user1 → admin 查询）
C=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/notifications?page=1&size=5" -H "$H")
[ "$C" = "200" ] && ok "12. notification list" || fail "12. notification ($C)"

# 13. dashboard
C=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/dashboard/overview" -H "$H")
[ "$C" = "200" ] && ok "13. dashboard" || fail "13. dashboard ($C)"

# 14. audit
C=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/audit-logs?page=1&size=5" -H "$H")
[ "$C" = "200" ] && ok "14. audit" || fail "14. audit ($C)"

# 清理 smoke 数据
curl -s -o /dev/null -X DELETE "$BASE/api/v1/orgs/$ORG_ID" -H "$H"

echo "===== SMOKE RESULT: PASS=$PASS FAIL=$FAIL ====="
[ "$FAIL" = "0" ] && echo "SMOKE PASS" || echo "SMOKE FAIL"
exit "$FAIL"
