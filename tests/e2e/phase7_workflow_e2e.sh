#!/bin/bash
# Phase 7 E2E v2 — 严格断言版（真实 HTTP）
set -u
B=http://localhost:8080
SS=$(date +%s)
FAILS=""
ok()  { echo "  [OK]   $1"; }
bad() { echo "  [FAIL] $1"; FAILS="$FAILS|$1"; }

login() { curl -s -X POST $B/api/v1/auth/login -H "Content-Type: application/json" \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python -c "import sys,json;d=json.load(sys.stdin);print(d['data']['accessToken'] if isinstance(d.get('data'),dict) else 'X')"; }
jget() { python -c "import sys,json;d=json.load(sys.stdin);print(d$1)" 2>/dev/null; }
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

T=$(login admin Admin@123456)
M=$(login user1 Member@123456)
[ "${#T}" -gt 50 ] && ok "1. admin login" || bad "1. admin login"
[ "${#M}" -gt 50 ] && ok "2. member login" || bad "2. member login"

# ---- 预清理: user1 残留 P7E2E* 角色绑定 + 角色本体（保证 403 场景前置干净）----
for RC in $(curl -s $B/api/v1/users/2/roles -H "Authorization: Bearer $T" | python -c "import sys,json;[print(c) for c in json.load(sys.stdin)['data'] if c.startswith('P7E2E')]" 2>/dev/null); do
  curl -s -o /dev/null -X DELETE "$B/api/v1/users/2/roles/$RC" -H "Authorization: Bearer $T"
done
for RID in $(curl -s "$B/api/v1/roles?page=1&size=100" -H "Authorization: Bearer $T" | python -c "import sys,json;d=json.load(sys.stdin);recs=d['data']['records'] if isinstance(d['data'],dict) else d['data'];[print(r['id']) for r in recs if str(r.get('code','')).startswith('P7E2E_TRANS')]" 2>/dev/null); do
  curl -s -o /dev/null -X DELETE "$B/api/v1/roles/$RID" -H "Authorization: Bearer $T"
done
LEFT=$(curl -s $B/api/v1/users/2/roles -H "Authorization: Bearer $T" | python -c "import sys,json;print(sum(1 for c in json.load(sys.stdin)['data'] if c.startswith('P7E2E')))")
[ "$LEFT" = "0" ] && ok "0. residual trans roles purged" || bad "0. residual trans roles left=$LEFT"

# ---- 建链 ----
OID=$(curl -s -X POST $B/api/v1/orgs -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d "{\"name\":\"P7E2E OrgV2\",\"code\":\"P7E2EV2ORG$SS\",\"description\":null}" | jget "['data']['id']")
[ -n "$OID" ] && ok "3. org created id=$OID" || bad "3. org create"
C1=$(code -X POST $B/api/v1/orgs/$OID/members -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"userId":2,"role":"MEMBER","departmentId":null}')
[ "$C1" = "201" -o "$C1" = "200" ] && ok "4. org member added ($C1)" || bad "4. org member ($C1)"

PID2=$(curl -s -X POST $B/api/v1/projects -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d "{\"name\":\"P7E2E ProjV2\",\"key\":\"P7E2EV2P$SS\",\"orgId\":$OID,\"description\":null}" | jget "['data']['id']")
[ -n "$PID2" ] && ok "5. project created id=$PID2" || bad "5. project create"
C2=$(code -X POST $B/api/v1/projects/$PID2/members -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"userId":2,"role":"MEMBER"}')
[ "$C2" = "201" -o "$C2" = "200" ] && ok "6. project member added ($C2)" || bad "6. project member ($C2)"

IRESP=$(curl -s -X POST $B/api/v1/projects/$PID2/issues -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d "{\"title\":\"P7E2E IssueV2\",\"description\":\"e2e\",\"type\":\"BUG\",\"priority\":\"URGENT\",\"severity\":\"S1\",\"assigneeId\":2}")
IID=$(echo "$IRESP" | jget "['data']['id']"); NO=$(echo "$IRESP" | jget "['data']['issueNo']"); ST=$(echo "$IRESP" | jget "['data']['status']")
RP=$(echo "$IRESP" | jget "['data']['reporterId']")
[ -n "$IID" ] && [ "$ST" = "OPEN" ] && [ "$RP" = "1" ] && ok "7. issue id=$IID no=$NO status=$ST reporter=$RP" || bad "7. issue create ($IRESP)"

# ---- 权限双层校验: member 是项目成员但无 issue:transition → 403 ----
C3=$(code -X PATCH $B/api/v1/projects/$PID2/issues/$IID/status -H "Authorization: Bearer $M" -H "Content-Type: application/json" -d '{"fromStatus":"OPEN","toStatus":"IN_PROGRESS"}')
[ "$C3" = "403" ] && ok "8. member w/o issue:transition -> 403" || bad "8. expected 403 got $C3"

# ---- 授予 issue:transition 后合法流转 200 ----
RROLE=$(curl -s -X POST $B/api/v1/roles -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d "{\"code\":\"P7E2ETRANS$SS\",\"name\":\"trans-$SS\",\"description\":null}")
RID=$(echo "$RROLE" | jget "['data']['id']")
CP=$(code -X PUT $B/api/v1/roles/$RID/permissions -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"permissionCodes":["issue:transition"]}')
CB=$(code -X POST $B/api/v1/users/2/roles -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d "{\"roleCode\":\"P7E2ETRANS$SS\"}")
[ "$CP" = "200" ] && [ "$CB" = "201" -o "$CB" = "200" ] && ok "9a. role granted+bound ($CP/$CB)" || bad "9a. grant ($CP/$CB)"
# 重新登录获取含新角色的 token
M=$(login user1 Member@123456)
C4=$(code -X PATCH $B/api/v1/projects/$PID2/issues/$IID/status -H "Authorization: Bearer $M" -H "Content-Type: application/json" -d '{"fromStatus":"OPEN","toStatus":"IN_PROGRESS"}')
[ "$C4" = "200" ] && ok "9b. member transition after grant -> 200" || bad "9b. expected 200 got $C4"

# ---- 并发 8x stale-from（当前 IN_PROGRESS，fromStatus=OPEN）→ 全 409 ----
S200=0; S409=0; SOTH=0
for i in 1 2 3 4 5 6 7 8; do
  C=$(code -X PATCH $B/api/v1/projects/$PID2/issues/$IID/status -H "Authorization: Bearer $M" -H "Content-Type: application/json" -d '{"fromStatus":"OPEN","toStatus":"IN_PROGRESS"}')
  case $C in 200) S200=$((S200+1));; 409) S409=$((S409+1));; *) SOTH=$((SOTH+1));; esac
done
[ "$S200" = "0" ] && [ "$S409" = "8" ] && [ "$SOTH" = "0" ] && ok "10. stale-from 8x: 200x$S200 409x$S409 otherx$SOTH" || bad "10. stale-from 200x$S200 409x$S409 otherx$SOTH"

# ---- 矩阵: 非法跳过 IN_PROGRESS->CLOSED → 409 ----
C5=$(code -X PATCH $B/api/v1/projects/$PID2/issues/$IID/status -H "Authorization: Bearer $M" -H "Content-Type: application/json" -d '{"fromStatus":"IN_PROGRESS","toStatus":"CLOSED"}')
[ "$C5" = "409" ] && ok "11. IN_PROGRESS->CLOSED skip -> 409" || bad "11. expected 409 got $C5"

# ---- 主链全程: ->RESOLVED->TESTING->REOPENED->IN_PROGRESS->RESOLVED->TESTING->CLOSED ----
chain() { code -X PATCH $B/api/v1/projects/$PID2/issues/$IID/status -H "Authorization: Bearer $M" -H "Content-Type: application/json" -d "{\"fromStatus\":\"$1\",\"toStatus\":\"$2\"}"; }
C6=$(chain IN_PROGRESS RESOLVED);  [ "$C6" = "200" ] && ok "12a. ->RESOLVED"  || bad "12a. got $C6"
C7=$(chain RESOLVED TESTING);      [ "$C7" = "200" ] && ok "12b. ->TESTING"    || bad "12b. got $C7"
C8=$(chain TESTING REOPENED);      [ "$C8" = "200" ] && ok "12c. ->REOPENED(回路)" || bad "12c. got $C8"
C9=$(chain REOPENED IN_PROGRESS);  [ "$C9" = "200" ] && ok "12d. ->IN_PROGRESS" || bad "12d. got $C9"
C10=$(chain IN_PROGRESS RESOLVED); [ "$C10" = "200" ] && ok "12e. ->RESOLVED" || bad "12e. got $C10"
C11=$(chain RESOLVED TESTING);     [ "$C11" = "200" ] && ok "12f. ->TESTING"   || bad "12f. got $C11"
C12=$(chain TESTING CLOSED);       [ "$C12" = "200" ] && ok "12g. ->CLOSED"    || bad "12g. got $C12"

# ---- 终态: CLOSED->IN_PROGRESS → 409 ----
C13=$(chain CLOSED IN_PROGRESS)
[ "$C13" = "409" ] && ok "13. CLOSED terminal -> 409" || bad "13. expected 409 got $C13"

# ---- 回收 ----
CD=$(code -X DELETE $B/api/v1/orgs/$OID -H "Authorization: Bearer $T")
curl -s -o /dev/null -X DELETE "$B/api/v1/users/2/roles/P7E2ETRANS$SS" -H "Authorization: Bearer $T"
curl -s -o /dev/null -X DELETE "$B/api/v1/roles/$RID" -H "Authorization: Bearer $T"
LEFT2=$(curl -s $B/api/v1/users/2/roles -H "Authorization: Bearer $T" | python -c "import sys,json;print(sum(1 for c in json.load(sys.stdin)['data'] if c.startswith('P7E2E')))")
[ "$CD" = "200" ] && [ "$LEFT2" = "0" ] && ok "14. cleanup org=$CD residualRoles=$LEFT2" || bad "14. cleanup org=$CD residualRoles=$LEFT2"

echo "======================================="
if [ -z "$FAILS" ]; then echo "===== Phase 7 E2E: ALL PASS ====="; else echo "===== Phase 7 E2E: FAIL$FAILS ====="; fi
