#!/bin/bash
# 完整 restore 验证（单脚本原子执行）
set -uo pipefail
cd /mnt/d/codex协同项目/WorkFlowX
TS=$(date +%H%M%S)
DBQ() { docker exec workflowx-deploy-mysql sh -c "mysql -uworkflowx -pdeploy_app_pass_2026 workflowx -N -e \"$1\""; }

echo "== [1] 创建数据 =="
python3 - <<'PYEOF'
import httpx, uuid
c = httpx.Client(base_url='http://localhost:8080', timeout=15)
t = c.post('/api/v1/auth/login', json={'username':'admin','password':'Admin@123456'}).json()['data']['accessToken']
h = {'Authorization': f'Bearer {t}'}
su = uuid.uuid4().hex[:8].upper()
org = c.post('/api/v1/orgs', headers=h, json={'name':f'RV org {su}','code':f'RVOG{su}','description':None}).json()['data']
proj = c.post('/api/v1/projects', headers=h, json={'name':'RV proj','key':f'RVP{su}','orgId':org['id'],'description':None}).json()['data']
issue = c.post(f'/api/v1/projects/{proj["id"]}/issues', headers=h, json={'title':'RV issue','type':'TASK','priority':'HIGH','assigneeId':None}).json()['data']
payload = b'RESTORE verify payload 999'
att = c.post(f'/api/v1/projects/{proj["id"]}/issues/{issue["id"]}/attachments', headers=h, files={'file': ('rv-verify.txt', payload, 'text/plain')}, timeout=30).json()['data']
open('/mnt/d/codex协同项目/WorkFlowX/backups/rv_ids.txt','w').write(f"{org['id']} {proj['id']} {issue['id']} {att['id']}")
print('created:', org['id'], proj['id'], issue['id'], att['id'])
PYEOF
RV=$(cat /mnt/d/codex协同项目/WorkFlowX/backups/rv_ids.txt); ORG=$(echo $RV | cut -d' ' -f1); PROJ=$(echo $RV | cut -d' ' -f2); ISSUE=$(echo $RV | cut -d' ' -f3); ATT=$(echo $RV | cut -d' ' -f4)
echo "== [2] backup =="
bash scripts/backup/backup.sh "backups/restore-verify-$TS" >/dev/null
echo "== [3] 破坏: 删附件行 + 删 org（级联清 DB/MinIO 对象）==="
python3 - <<'PYEOF'
import httpx
ids = open('/mnt/d/codex协同项目/WorkFlowX/backups/rv_ids.txt').read().split()
c = httpx.Client(base_url='http://localhost:8080', timeout=15)
t = c.post('/api/v1/auth/login', json={'username':'admin','password':'Admin@123456'}).json()['data']['accessToken']
h = {'Authorization': f'Bearer {t}'}
c.delete(f"/api/v1/projects/{ids[1]}/issues/{ids[2]}/attachments/{ids[3]}", headers=h)
c.delete(f"/api/v1/orgs/{ids[0]}", headers=h)
PYEOF
DB_COUNT=$(DBQ "SELECT COUNT(*) FROM attachments")
echo "破坏后 DB attachments=$DB_COUNT"
echo "== [4] restore =="
bash scripts/restore/restore.sh "backups/restore-verify-$TS" >/dev/null
echo "== [5] 验证 =="
DB_COUNT=$(DBQ "SELECT COUNT(*) FROM attachments")
DB_ROW=$(DBQ "SELECT file_name FROM attachments LIMIT 1")
MINIO_COUNT=$(docker exec workflowx-deploy-minio sh -c 'mc ls --recursive local/workflowx 2>/dev/null | wc -l')
echo "DB attachments=$DB_COUNT | MinIO objects=$MINIO_COUNT | file_name=$DB_ROW"
if [ "$DB_COUNT" -ge 1 ] && [ "$MINIO_COUNT" -ge 1 ]; then
  echo "===== RESTORE VERIFY: PASS ====="
else
  echo "===== RESTORE VERIFY: FAIL ====="
fi
