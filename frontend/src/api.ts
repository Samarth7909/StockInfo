import axios from 'axios'
import type { AuthUser, ImportRecord, CaseSummary, CaseDetail, PageResponse, MetricsSnapshot } from './types'

const api = axios.create({ baseURL: '/api' })

// Attach JWT on every request
api.interceptors.request.use(cfg => {
  const raw = localStorage.getItem('auth')
  if (raw) {
    const user: AuthUser = JSON.parse(raw)
    cfg.headers.Authorization = `Bearer ${user.token}`
  }
  return cfg
})

// Auto-logout on 401
api.interceptors.response.use(
  r => r,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('auth')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

// ── Auth ──────────────────────────────────────────────────────────────────────
export const login = async (username: string, password: string): Promise<AuthUser> => {
  const { data } = await api.post('/auth/login', { username, password })
  return data as AuthUser
}

// ── Imports ───────────────────────────────────────────────────────────────────
export const uploadImport = async (streamName: string, file: File): Promise<ImportRecord> => {
  const form = new FormData()
  form.append('file', file)
  form.append('streamName', streamName)
  const { data } = await api.post('/imports', form)
  return data
}

export const listImports = async (): Promise<ImportRecord[]> => {
  const { data } = await api.get('/imports')
  return data
}

export const getImport = async (id: string): Promise<ImportRecord> => {
  const { data } = await api.get(`/imports/${id}`)
  return data
}

// ── Cases ─────────────────────────────────────────────────────────────────────
export const listCases = async (params: {
  page?: number
  size?: number
  severity?: string
  state?: string
  sort?: string
}): Promise<PageResponse<CaseSummary>> => {
  const { data } = await api.get('/cases', { params })
  return data
}

export const getCase = async (id: string): Promise<CaseDetail> => {
  const { data } = await api.get(`/cases/${id}`)
  return data
}

export const addNote = async (caseId: string, body: string): Promise<void> => {
  await api.post(`/cases/${caseId}/notes`, { body })
}

export const transitionCase = async (
  caseId: string,
  targetState: string,
  reason: string,
  expectedVersion: number
): Promise<CaseSummary> => {
  const { data } = await api.post(`/cases/${caseId}/transition`, {
    targetState, reason, expectedVersion,
  })
  return data
}

// ── Metrics ───────────────────────────────────────────────────────────────────
export const getMetrics = async (): Promise<MetricsSnapshot> => {
  const { data } = await api.get('/metrics')
  return data
}

export default api
