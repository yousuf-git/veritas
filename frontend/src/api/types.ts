export type RunStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH'
export type DiscrepancyStatus = 'OPEN' | 'IN_REVIEW' | 'RESOLVED' | 'ESCALATED'

export type DiscrepancyType =
  | 'MISSING_PAYOUT'
  | 'MISSING_LEDGER_ENTRY'
  | 'AMOUNT_DRIFT'
  | 'DUPLICATE_CHARGE'
  | 'UNEXPECTED_FEE'
  | 'FX_ROUNDING'

export type ResolutionAction =
  | 'ACCEPT_FEE'
  | 'LINK_MANUALLY'
  | 'ESCALATE'
  | 'WRITE_OFF'
  | 'REJECT'

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresAt: string
  username: string
  displayName: string
  role: string
  permissions: string[]
}

export interface SettlementFile {
  id: string
  filename: string
  provider: string
  checksumSha256: string
  sizeBytes: number
  status: 'REGISTERED' | 'PARSED' | 'PARSE_FAILED'
  lineCount: number
  parseError: string | null
  uploadedAt: string
  newlyIngested: boolean | null
}

export interface Run {
  id: string
  fileId: string
  status: RunStatus
  totalLines: number
  matchedExact: number
  matchedHeuristic: number
  unmatched: number
  discrepancyCount: number
  matchRate: number
  matchedAmountMinor: number
  unmatchedAmountMinor: number
  currency: string | null
  error: string | null
  startedAt: string
  completedAt: string | null
}

export interface DiscrepancyBreakdown {
  type: DiscrepancyType
  severity: Severity
  count: number
  amountAtRiskMinor: number
  amountAtRisk: string
}

export interface RunReport {
  runId: string
  fileId: string
  filename: string
  checksumSha256: string
  status: RunStatus
  startedAt: string
  completedAt: string | null
  totalLines: number
  matchedExact: number
  matchedHeuristic: number
  unmatched: number
  matchRate: number
  discrepancyCount: number
  matchedAmountMinor: number
  matchedAmount: string
  unmatchedAmountMinor: number
  unmatchedAmount: string
  outstandingAmountMinor: number
  outstandingAmount: string
  currency: string
  error: string | null
  discrepancies: DiscrepancyBreakdown[]
}

export interface ExceptionSummary {
  id: string
  runId: string
  type: DiscrepancyType
  severity: Severity
  status: DiscrepancyStatus
  amountImpactMinor: number
  amountImpact: string
  currency: string
  detail: string
  assignedTo: string | null
  version: number
  createdAt: string
  resolvedAt: string | null
}

export interface SettlementLine {
  id: string
  lineNumber: number
  providerTxnId: string
  providerRef: string | null
  txnType: string
  grossMinor: number
  feeMinor: number
  netMinor: number
  gross: string
  fee: string
  net: string
  currency: string
  createdAtProvider: string
  availableOn: string | null
  description: string | null
  raw: Record<string, string>
}

export interface LedgerEntry {
  id: string
  entryType: 'ORDER' | 'REFUND' | 'FEE' | 'ADJUSTMENT'
  externalRef: string
  providerRef: string | null
  amountMinor: number
  amount: string
  currency: string
  occurredAt: string
  description: string | null
  metadata: Record<string, string>
  createdAt: string
}

export interface ResolutionRecord {
  id: string
  action: ResolutionAction
  linkedLedgerEntryId: string | null
  note: string
  resolvedByUsername: string
  resolvedAt: string
}

export interface ExceptionDetail {
  exception: ExceptionSummary
  settlementLine: SettlementLine | null
  ledgerEntry: LedgerEntry | null
  history: ResolutionRecord[]
}

export interface ScenarioResponse {
  file: SettlementFile
  ledgerEntriesCreated: number
  expectedDiscrepancies: Record<string, number>
}
