// ============ WorkFlowX Mixed Load Scenario（Phase 14）============
// 单会话铁律: 每进程仅登录一次 seed admin（synchronized），token 全线程共享。
// 场景轮盘: 读为主（~90%），issue create ~10%（写保守）。

def HOST = props.get("HOST") ?: "localhost"
def PORT = props.get("PORT") ?: "8080"
def BASE = "http://" + HOST + ":" + PORT
def PROJECT_ID = props.get("PERF_PROJECT_ID") ?: "0"

// ---- 懒加载共享 token（一次登录，synchronized 防并发双登录）----
def token = props.get("auth_token")
if (token == null) {
    synchronized ("AUTH_LOCK") {
        token = props.get("auth_token")
        if (token == null) {
            def conn = new URL(BASE + "/api/v1/auth/login").openConnection()
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.outputStream.withWriter("UTF-8") { it.write('{"username":"admin","password":"Admin@123456"}') }
            def code = conn.responseCode
            if (code != 200) { throw new RuntimeException("setup login failed: " + code) }
            token = new groovy.json.JsonSlurper().parseText(conn.inputStream.getText("UTF-8")).data.accessToken
            props.put("auth_token", token)
            log.info("PERF setup login OK, token len=" + token.length())
        }
    }
}

// ---- HTTP helper（JDK HttpURLConnection，返回 [code, body]）----
def http = { String method, String path, String json ->
    def conn = new URL(BASE + path).openConnection()
    conn.requestMethod = method
    conn.doOutput = (json != null)
    conn.setRequestProperty("Authorization", "Bearer " + token)
    if (json != null) { conn.setRequestProperty("Content-Type", "application/json") }
    conn.connectTimeout = 5000
    conn.readTimeout = 10000
    if (json != null) { conn.outputStream.withWriter("UTF-8") { it.write(json) } }
    def code = conn.responseCode
    def stream = (code < 400 ? conn.inputStream : conn.errorStream)
    def body = (stream != null ? stream.getText("UTF-8") : "")
    return [code, body]
}

// ---- 场景轮盘（读为主，写保守 10%）----
def pick = new Random().nextInt(100)
def scene
def path
def jsonBody = null
if (pick < 5)       { scene = "health";            path = "/api/v1/health" }
else if (pick < 25) { scene = "project list";      path = "/api/v1/projects?page=1&size=20" }
else if (pick < 45) { scene = "issue list";        path = "/api/v1/projects/" + PROJECT_ID + "/issues?page=1&size=20" }
else if (pick < 55) { scene = "notification list"; path = "/api/v1/notifications?page=1&size=20" }
else if (pick < 65) { scene = "audit list";        path = "/api/v1/audit-logs?page=1&size=20" }
else if (pick < 75) { scene = "dashboard";         path = "/api/v1/dashboard/overview" }
else if (pick < 85) { scene = "me";                path = "/api/v1/auth/me" }
else                { scene = "issue create";      path = "/api/v1/projects/" + PROJECT_ID + "/issues"
                      jsonBody = '{"title":"PERF ' + UUID.randomUUID().toString().substring(0,8) + '","type":"TASK","priority":"MEDIUM","assigneeId":null}' }

// ---- 执行与 SampleResult 填充 ----
SampleResult.sampleStart()
try {
    def (code, body) = http((jsonBody != null ? "POST" : "GET"), path, jsonBody)
    SampleResult.setResponseData(body, "UTF-8")
    SampleResult.setDataType(org.apache.jmeter.samplers.SampleResult.TEXT)
    SampleResult.setSuccessful(code >= 200 && code < 300)
    SampleResult.setResponseCode(String.valueOf(code))
    SampleResult.setResponseMessage(scene)
} catch (Exception e) {
    SampleResult.setSuccessful(false)
    SampleResult.setResponseCode("EXC")
    SampleResult.setResponseMessage(e.getMessage())
    SampleResult.setResponseData(String.valueOf(e), "UTF-8")
} finally {
    SampleResult.sampleEnd()
    SampleResult.setSamplerData(scene + " " + path)
}
