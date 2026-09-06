/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** API 统一前缀（docs/api/api-conventions.md），如 /api/v1 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
