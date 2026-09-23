import { test as setup } from '@playwright/test'
import fs from 'node:fs'
import path from 'node:path'

/**
 * 认证 setup（P13-06）：每 seed 用户登录一次生成 storageState。
 * 单会话铁律: 本文件仅在 setup project 执行一次；测试主体绝不重登 seed 用户。
 * state 文件在 test-results/.auth/（.gitignore 已忽略），token 不进 Git。
 */
const AUTH_DIR = path.resolve('test-results/.auth')
const CREDENTIALS = {
  admin: { username: 'admin', password: 'Admin@123456', file: 'admin.json' },
  user: { username: 'user1', password: 'Member@123456', file: 'user.json' },
}

for (const [name, cred] of Object.entries(CREDENTIALS)) {
  setup(`authenticate as ${name}`, async ({ request }) => {
    fs.mkdirSync(AUTH_DIR, { recursive: true })
    const stateFile = path.join(AUTH_DIR, cred.file)
    // 每次 playwright 调用都重新登录一次（轮内仅此一次，满足单会话铁律）——
    // 不做 existsSync 短路：陈旧 state（如上轮被其他脚本顶掉）会导致整轮假失败
    const resp = await request.post('/api/v1/auth/login', {
      data: { username: cred.username, password: cred.password },
    })
    if (resp.status() !== 200) {
      throw new Error(
        `setup 登录失败（${name}）: ${resp.status()}——请确认后端已运行于 :8080`,
      )
    }
    const { data } = await resp.json()
    // 前端认证态 = localStorage.workflowx_access_token（utils/token.ts）
    fs.writeFileSync(
      stateFile,
      JSON.stringify({
        cookies: [],
        origins: [
          {
            origin: 'http://localhost:5173',
            localStorage: [{ name: 'workflowx_access_token', value: data.accessToken }],
          },
        ],
      }),
    )
  })
}
