// Fetch wrapper for the Spring Boot API.
// The access token lives only in memory; the refresh token is an httpOnly cookie scoped to /api/v1/auth.

const BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

export class ApiError extends Error {
  status: number
  code: string
  fields?: Record<string, string> | null
  data?: unknown
  constructor(status: number, code: string, message: string, fields?: Record<string, string> | null, data?: unknown) {
    super(message)
    this.status = status
    this.code = code
    this.fields = fields
    this.data = data
  }
}

export interface SessionUser {
  id: number
  email: string
  name: string
  phone: string | null
  role: 'CUSTOMER' | 'STAFF' | 'ADMIN' | 'OWNER'
}
export interface Session {
  accessToken: string
  expiresAt: string
  user: SessionUser
}

let accessToken: string | null = null
let refreshing: Promise<Session | null> | null = null
const listeners = new Set<(s: Session | null) => void>()

export const onSession = (fn: (s: Session | null) => void) => {
  listeners.add(fn)
  return () => listeners.delete(fn)
}
const HINT = 'void.signedIn'
export const hadSession = () => {
  try {
    return localStorage.getItem(HINT) === '1'
  } catch {
    return false
  }
}
export function setSession(s: Session | null) {
  accessToken = s?.accessToken ?? null
  try {
    if (s) localStorage.setItem(HINT, '1')
    else localStorage.removeItem(HINT)
  } catch {
    /* storage unavailable */
  }
  listeners.forEach((fn) => fn(s))
}
export const hasToken = () => accessToken != null

/** One refresh at a time, shared by every request that hit a 401. */
export function refreshSession(): Promise<Session | null> {
  refreshing ??= fetch(`${BASE}/api/v1/auth/refresh`, { method: 'POST', credentials: 'include' })
    .then(async (r) => (r.ok ? ((await r.json()) as Session) : null))
    .catch(() => null)
    .then((s) => {
      setSession(s)
      return s
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

type Body = Record<string, unknown> | unknown[] | FormData | undefined

async function parseError(r: Response): Promise<ApiError> {
  let data: { code?: string; message?: string; fields?: Record<string, string> } = {}
  try {
    data = await r.json()
  } catch {
    /* not JSON */
  }
  return new ApiError(r.status, data.code ?? `http_${r.status}`, data.message ?? r.statusText, data.fields, data)
}

export async function request<T>(method: string, path: string, body?: Body, opts: { auth?: boolean; raw?: boolean } = {}): Promise<T> {
  const send = () => {
    const headers: Record<string, string> = {}
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`
    let payload: BodyInit | undefined
    if (body instanceof FormData) payload = body
    else if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
      payload = JSON.stringify(body)
    }
    return fetch(`${BASE}/api/v1${path}`, { method, headers, body: payload, credentials: 'include' })
  }

  let r = await send()
  if (r.status === 401 && (accessToken || opts.auth)) {
    const s = await refreshSession()
    if (s) r = await send()
  }
  if (!r.ok) throw await parseError(r)
  if (opts.raw) return r as unknown as T
  if (r.status === 204) return undefined as T
  const text = await r.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const http = {
  get: <T>(path: string, auth = false) => request<T>('GET', path, undefined, { auth }),
  post: <T>(path: string, body?: Body, auth = false) => request<T>('POST', path, body, { auth }),
  put: <T>(path: string, body?: Body) => request<T>('PUT', path, body, { auth: true }),
  patch: <T>(path: string, body?: Body) => request<T>('PATCH', path, body, { auth: true }),
  del: <T>(path: string) => request<T>('DELETE', path, undefined, { auth: true }),
}

export async function logout() {
  try {
    await fetch(`${BASE}/api/v1/auth/logout`, { method: 'POST', credentials: 'include' })
  } finally {
    setSession(null)
  }
}

/** Absolute URL for uploaded images when the API lives on another origin. */
export const assetUrl = (url: string) => (url.startsWith('/uploads/') ? `${BASE}${url}` : url)
