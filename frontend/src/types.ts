// ── Auth ──────────────────────────────────────────────────────────────────────
export interface AuthUser {
  token: string
  username: string
  role: 'SUPPORT' | 'INVESTIGATOR' | 'OPS_LEAD' | 'AUDITOR'
  fullName: string
  clientIds: string[]
}

// ── Imports ───────────────────────────────────────────────────────────────────
export type SourceStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'ERROR'

export interface ImportRecord {
  id: string
  streamName: string
  cutAt: string | null
  receivedAt: string
  filename: string
  sha256Hash: string
  schemaVersion: string
  mappingVersion: string
  rowCount: number
  errorCount: number
  status: SourceStatus
  errorDetail: string | null
  idempotent: boolean
  casesCreated: number
  casesReopened: number
  errorMessages: string[]
  importedBy: string | null
}

// ── Cases ─────────────────────────────────────────────────────────────────────
export type CaseSeverity  = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'
export type CaseState     = 'OPEN' | 'INVESTIGATING' | 'NEEDS_SOURCE' | 'RESOLVED' | 'REOPENED'
export type CaseType      = 'HOLDING_MISMATCH' | 'PENDING_DP' | 'CASH_RECONCILIATION' | 'MISSING_SOURCE' | 'UNMATCHED_IDENTITY'
export type EvidenceState = 'MATCHED' | 'UNMATCHED_IDENTITY' | 'STALE_CUT' | 'CONFLICTING_EVIDENCE' | 'MISSING_SOURCE' | 'UNKNOWN'

export interface CaseSummary {
  id: string
  caseType: CaseType
  severity: CaseSeverity
  state: CaseState
  clientId: string
  isin: string | null
  cutAt: string | null
  quantityDelta: number | null
  amountDeltaPaise: number | null
  evidenceState: EvidenceState
  description: string | null
  version: number
  createdAt: string
  updatedAt: string
  noteCount: number
}

export interface EvidenceItem {
  evidenceId: string
  evidenceRole: string
  sourceFileId: string
  streamName: string
  cutAt: string | null
  receivedAt: string
  filename: string
  sha256Hash: string
  mappingVersion: string
  rawRowId: string | null
  rawJson: string | null
}

export interface NoteItem {
  id: string
  authorUsername: string
  body: string
  createdAt: string
  evidenceVersionSnapshot: string | null
}

export interface TransitionItem {
  id: string
  fromState: CaseState
  toState: CaseState
  reason: string
  byUsername: string
  evidenceVersion: number
  changedAt: string
}

export interface CaseDetail extends CaseSummary {
  evidence: EvidenceItem[]
  notes: NoteItem[]
  transitions: TransitionItem[]
}

// ── Pagination ────────────────────────────────────────────────────────────────
export interface PageResponse<T> {
  items: T[]
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

// ── Metrics ───────────────────────────────────────────────────────────────────
export interface MetricsSnapshot {
  collectedAt: string
  pendingImports: number
  errorImports: number
  streamFreshness: Record<string, string | null>
  openCasesBySeverity: Record<string, number>
  totalOpenCases: number
}
