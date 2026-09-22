#!/bin/bash
# Phase 8 E2E — Comment & Attachment 真实文件全链路（P8-16）
# 链路: 登录→组织→项目→成员→Issue→Comment 增改删→Attachment 上传/下载/删除
# 一致性: DB 元数据（mysql 直连核验）与 MinIO 对象（mc 核验）同步；终态残留=0
set -u
B=http://localhost:8080
SS=$(date +%s)
USS=$(echo "$SS" | tr -d '\n' | awk '{print toupper($0)}')
FAILS=""
ok()  { echo "  [OK]   $1"; }
bad() { echo "  [FAIL] $1"; FAILS="$FAILS|$1"; }

login() { curl -s -X POST $B/api/v1/auth/login -H "Content-Type: application/json" \
  -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python -c "import sys,json;d=json.load(sys.stdin);print(d['data']['accessToken'] if isinstance(d.get('data'),dict) else 'X')"; }
jget() { python -c "import sys,json;d=json.load(sys.stdin);print(d$1)" 2>/dev/null; }
code() { curl -s -o /dev/null -w "%{http_code}" "$@"; }

# MinIO/MySQL 工具（基础设施级终态核验；业务断言全部走真实 HTTP）
WSL=/c/Windows/System32/wsl.exe
mcsh() { "$WSL" -d Ubuntu -u root -- docker exec workflowx-minio sh -c "mc alias set local http://localhost:9000 minioadmin workflowx_dev_minio >/dev/null 2>&1; $1" 2>/dev/null; }
mcount() { mcsh "mc ls --recursive local/workflowx/issues 2>/dev/null | wc -l" | tr -d '[:space:]'; }
mstat() { mcsh "mc stat local/workflowx/$1 >/dev/null 2>&1 && echo YES || echo NO" | tr -d '[:space:]'; }
mysql_q() { mysql -h127.0.0.1 -P3307 -uroot -pworkflowx_dev_root -N -e "$1" workflowx 2>/dev/null | tr -d '[:space:]'; }

T=$(login admin Admin@123456)
M=$(login user1 Member@123456)
[ "${#T}" -gt 50 ] && ok "1. admin login" || bad "1. admin login"
[ "${#M}" -gt 50 ] && ok "2. member login" || bad "2. member login"

# ---- 建链 ----
OID=$(curl -s -X POST $B/api/v1/orgs -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d "{\"name\":\"P8E2E Org\",\"code\":\"P8E2EORG$USS\",\"description\":null}" | jget "['data']['id']")
[ -n "$OID" ] && ok "3. org id=$OID" || bad "3. org create"
curl -s -o /dev/null -X POST $B/api/v1/orgs/$OID/members -H "Authorization: Bearer $T" \
  -H "Content-Type: application/json" -d '{"userId":2,"role":"MEMBER","departmentId":null}'
PID2=$(curl -s -X POST $B/api/v1/projects -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d "{\"name\":\"P8E2E Proj\",\"key\":\"P8E2EP$USS\",\"orgId\":$OID,\"description\":null}" | jget "['data']['id']")
[ -n "$PID2" ] && ok "4. project id=$PID2" || bad "4. project create"
curl -s -o /dev/null -X POST $B/api/v1/projects/$PID2/members -H "Authorization: Bearer $T" \
  -H "Content-Type: application/json" -d '{"userId":2,"role":"MEMBER"}'
IID=$(curl -s -X POST $B/api/v1/projects/$PID2/issues -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d "{\"title\":\"P8E2E Issue\",\"description\":\"e2e\",\"type\":\"TASK\",\"priority\":\"MEDIUM\",\"assigneeId\":null}" | jget "['data']['id']")
[ -n "$IID" ] && ok "5. issue id=$IID" || bad "5. issue create"

CB="$B/api/v1/projects/$PID2/issues/$IID/comments"
AB="$B/api/v1/projects/$PID2/issues/$IID/attachments"

# ---- Comment 全生命周期 ----
CR=$(curl -s -X POST $CB -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"content":"P8E2E comment one"}')
CID=$(echo "$CR" | jget "['data']['id']")
CAU=$(echo "$CR" | jget "['data']['authorId']")
[ -n "$CID" ] && [ "$CAU" = "1" ] && ok "6. comment created id=$CID author=1" || bad "6. comment create ($CR)"
EU=$(curl -s -X PUT $CB/$CID -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d '{"content":"P8E2E comment edited"}')
[ "$(echo "$EU" | jget "['data']['content']")" = "P8E2E comment edited" ] && ok "7. comment edited" || bad "7. comment edit"
CDB=$(mysql_q "SELECT COUNT(*) FROM issue_comments WHERE id=$CID")
[ "$CDB" = "1" ] && ok "8. comment DB row exists" || bad "8. comment DB row=$CDB"
curl -s -o /dev/null -X DELETE $CB/$CID -H "Authorization: Bearer $T"
CDB2=$(mysql_q "SELECT COUNT(*) FROM issue_comments WHERE id=$CID")
[ "$CDB2" = "0" ] && ok "9. comment deleted (DB row gone)" || bad "9. comment DB row=$CDB2"

# ---- user1 权限路径: 无 attachment:upload → 403 ----
F=./p8e2e_tmp_file.txt; printf 'P8E2E attachment payload 0123' > $F
C3=$(code -X POST $AB -H "Authorization: Bearer $M" -F "file=@./p8e2e_tmp_file.txt;type=text/plain")
[ "$C3" = "403" ] && ok "10. member upload w/o authority -> 403" || bad "10. expected 403 got $C3"

# 授予 user1 attachment 全套（幂等预清理后创建）
for RC in $(curl -s $B/api/v1/users/2/roles -H "Authorization: Bearer $T" | python -c "import sys,json;[print(c) for c in json.load(sys.stdin)['data'] if c.startswith('P8E2E')]" 2>/dev/null); do
  curl -s -o /dev/null -X DELETE "$B/api/v1/users/2/roles/$RC" -H "Authorization: Bearer $T"
done
RID=$(curl -s -X POST $B/api/v1/roles -H "Authorization: Bearer $T" -H "Content-Type: application/json" \
  -d "{\"code\":\"P8E2EATT$USS\",\"name\":\"att-$USS\",\"description\":null}" | jget "['data']['id']")
curl -s -o /dev/null -X PUT $B/api/v1/roles/$RID/permissions -H "Authorization: Bearer $T" \
  -H "Content-Type: application/json" -d '{"permissionCodes":["attachment:upload","attachment:get","attachment:delete"]}'
CB2=$(code -X POST $B/api/v1/users/2/roles -H "Authorization: Bearer $T" -H "Content-Type: application/json" -d "{\"roleCode\":\"P8E2EATT$USS\"}")
[ "$CB2" = "201" -o "$CB2" = "200" ] && ok "11. attachment authorities granted" || bad "11. grant ($CB2)"
M=$(login user1 Member@123456)

# ---- Attachment: 上传（真实文件）----
UP=$(curl -s -X POST $AB -H "Authorization: Bearer $M" -F "file=@./p8e2e_tmp_file.txt;type=text/plain")
AID=$(echo "$UP" | jget "['data']['id']")
OKEY=$(mysql_q "SELECT object_key FROM attachments WHERE id=$AID")
[ -n "$AID" ] && [ -n "$OKEY" ] && ok "12. uploaded id=$AID objectKey=$OKEY" || bad "12. upload ($UP)"
MS=$(mstat "$OKEY")
[ "$MS" = "YES" ] && ok "13. MinIO object exists" || bad "13. MinIO stat=$MS"
MDB=$(mysql_q "SELECT COUNT(*) FROM attachments WHERE id=$AID AND file_name='p8e2e_tmp_file.txt' AND uploader_id=2")
[ "$MDB" = "1" ] && ok "14. metadata row (file_name/uploader) correct" || bad "14. metadata row=$MDB"

# 下载逐字节一致
DL=$(curl -s $AB/$AID/download -H "Authorization: Bearer $M")
[ "$DL" = "P8E2E attachment payload 0123" ] && ok "15. download bytes identical" || bad "15. download mismatch: $DL"

# user1（非上传者）不能删 ADMIN 的附件 → 先验证删除自己的 200
DD=$(code -X DELETE $AB/$AID -H "Authorization: Bearer $M")
[ "$DD" = "200" ] && ok "16. uploader delete -> 200" || bad "16. delete got $DD"
MS2=$(mstat "$OKEY")
MDB2=$(mysql_q "SELECT COUNT(*) FROM attachments WHERE id=$AID")
[ "$MS2" = "NO" ] && [ "$MDB2" = "0" ] && ok "17. MinIO object + DB row both gone" || bad "17. object=$MS2 row=$MDB2"

# ---- ownership: admin 上传、user1 删除 → 403 ----
UP2=$(curl -s -X POST $AB -H "Authorization: Bearer $T" -F "file=@./p8e2e_tmp_file.txt;type=text/plain")
AID2=$(echo "$UP2" | jget "['data']['id']")
OKEY2=$(mysql_q "SELECT object_key FROM attachments WHERE id=$AID2")
D403=$(code -X DELETE $AB/$AID2 -H "Authorization: Bearer $M")
[ "$D403" = "403" ] && ok "18. non-uploader delete -> 403" || bad "18. expected 403 got $D403"
curl -s -o /dev/null -X DELETE $AB/$AID2 -H "Authorization: Bearer $T"

# ---- 清理与终态 ----
CD=$(code -X DELETE $B/api/v1/orgs/$OID -H "Authorization: Bearer $T")
curl -s -o /dev/null -X DELETE "$B/api/v1/users/2/roles/P8E2EATT$USS" -H "Authorization: Bearer $T"
curl -s -o /dev/null -X DELETE "$B/api/v1/roles/$RID" -H "Authorization: Bearer $T"
LEFT=$(curl -s $B/api/v1/users/2/roles -H "Authorization: Bearer $T" | python -c "import sys,json;print(sum(1 for c in json.load(sys.stdin)['data'] if c.startswith('P8E2E')))")
MC=$(mcount)
[ "$CD" = "200" ] && [ "$LEFT" = "0" ] && [ "$MC" = "0" ] && ok "19. cleanup org=$CD roles=$LEFT MinIO objects=$MC" || bad "19. cleanup org=$CD roles=$LEFT MinIO=$MC"

rm -f ./p8e2e_tmp_file.txt
echo "======================================="
if [ -z "$FAILS" ]; then echo "===== Phase 8 E2E: ALL PASS ====="; else echo "===== Phase 8 E2E: FAIL$FAILS ====="; fi
