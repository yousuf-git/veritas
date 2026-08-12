import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type {
  DiscrepancyStatus,
  DiscrepancyType,
  ExceptionDetail,
  ExceptionSummary,
  PageResponse,
  ResolutionAction,
  Severity,
} from '../api/types'

const TYPES: DiscrepancyType[] = [
  'MISSING_PAYOUT',
  'MISSING_LEDGER_ENTRY',
  'AMOUNT_DRIFT',
  'DUPLICATE_CHARGE',
  'UNEXPECTED_FEE',
  'FX_ROUNDING',
]

const STATUSES: DiscrepancyStatus[] = ['OPEN', 'IN_REVIEW', 'ESCALATED', 'RESOLVED']
const SEVERITIES: Severity[] = ['HIGH', 'MEDIUM', 'LOW']

export default function ExceptionQueuePage() {
  const queryClient = useQueryClient()
  const { can } = useAuth()

  const [status, setStatus] = useState<DiscrepancyStatus | ''>('OPEN')
  const [type, setType] = useState<DiscrepancyType | ''>('')
  const [severity, setSeverity] = useState<Severity | ''>('')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const queue = useQuery({
    queryKey: ['exceptions', status, type, severity],
    queryFn: () =>
      api<PageResponse<ExceptionSummary>>('/api/v1/exceptions', {
        query: { status, type, severity, size: 50 },
      }),
  })

  const detail = useQuery({
    queryKey: ['exception', selectedId],
    queryFn: () => api<ExceptionDetail>(`/api/v1/exceptions/${selectedId}`),
    enabled: selectedId !== null,
  })

  const resolve = useMutation({
    mutationFn: (input: { id: string; action: ResolutionAction; note: string; version: number }) =>
      api<ExceptionSummary>(`/api/v1/exceptions/${input.id}/resolve`, {
        method: 'POST',
        body: { action: input.action, note: input.note, version: input.version },
      }),
    onSuccess: () => {
      setError(null)
      setSelectedId(null)
      queryClient.invalidateQueries({ queryKey: ['exceptions'] })
    },
    onError: (cause) =>
      setError(cause instanceof ApiError ? `${cause.code}: ${cause.message}` : 'Could not resolve.'),
  })

  return (
    <div className="split">
      <section className="panel">
        <div className="panel-head">
          <h2>Exception queue</h2>
          <p className="muted">
            {queue.data ? `${queue.data.totalItems} item(s)` : 'Loading…'} — oldest first
          </p>
        </div>

        <div className="filters">
          <Select label="Status" value={status} options={STATUSES} onChange={setStatus} />
          <Select label="Type" value={type} options={TYPES} onChange={setType} />
          <Select label="Severity" value={severity} options={SEVERITIES} onChange={setSeverity} />
        </div>

        <table className="grid">
          <thead>
            <tr>
              <th>Classification</th>
              <th>Severity</th>
              <th className="num">Impact</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {queue.data?.items.map((item) => (
              <tr
                key={item.id}
                className={selectedId === item.id ? 'row-selected clickable' : 'clickable'}
                onClick={() => setSelectedId(item.id)}
              >
                <td>
                  <div>{item.type.replace(/_/g, ' ')}</div>
                  <div className="muted tiny clamp">{item.detail}</div>
                </td>
                <td>
                  <span className={`chip chip-${item.severity.toLowerCase()}`}>{item.severity}</span>
                </td>
                <td className="num mono">
                  {item.amountImpact} {item.currency}
                </td>
                <td>
                  <span className={`chip chip-${item.status.toLowerCase()}`}>{item.status}</span>
                </td>
              </tr>
            ))}
            {queue.data?.items.length === 0 && (
              <tr>
                <td colSpan={4} className="muted center">
                  Nothing matches these filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      <section className="panel">
        {!selectedId && <p className="muted">Select an exception to see both sides of the money.</p>}

        {detail.data && (
          <ExceptionDetailView
            detail={detail.data}
            error={error}
            canWriteOff={can('exception:write_off')}
            busy={resolve.isPending}
            onResolve={(action, note) =>
              resolve.mutate({
                id: detail.data.exception.id,
                action,
                note,
                version: detail.data.exception.version,
              })
            }
          />
        )}
      </section>
    </div>
  )
}

function ExceptionDetailView({
  detail,
  error,
  canWriteOff,
  busy,
  onResolve,
}: {
  detail: ExceptionDetail
  error: string | null
  canWriteOff: boolean
  busy: boolean
  onResolve: (action: ResolutionAction, note: string) => void
}) {
  const [note, setNote] = useState('')
  const { exception, settlementLine, ledgerEntry, history } = detail
  const closed = exception.status === 'RESOLVED'

  const actions: ResolutionAction[] = ['ACCEPT_FEE', 'ESCALATE', 'REJECT']
  if (canWriteOff) {
    actions.push('WRITE_OFF')
  }

  return (
    <>
      <div className="panel-head">
        <h2>{exception.type.replace(/_/g, ' ')}</h2>
        <p className="muted">{exception.detail}</p>
      </div>

      <div className="compare">
        <div className="compare-side">
          <h3>Provider says</h3>
          {settlementLine ? (
            <dl>
              <Row label="Balance txn" value={settlementLine.providerTxnId} mono />
              <Row label="Source" value={settlementLine.providerRef ?? '—'} mono />
              <Row label="Category" value={settlementLine.txnType} />
              <Row label="Gross" value={`${settlementLine.gross} ${settlementLine.currency}`} mono />
              <Row label="Fee" value={`${settlementLine.fee} ${settlementLine.currency}`} mono />
              <Row label="Net" value={`${settlementLine.net} ${settlementLine.currency}`} mono />
              <Row label="Created" value={new Date(settlementLine.createdAtProvider).toLocaleString()} />
            </dl>
          ) : (
            <p className="muted">No provider row — this is money the provider never settled.</p>
          )}
        </div>

        <div className="compare-side">
          <h3>Ledger says</h3>
          {ledgerEntry ? (
            <dl>
              <Row label="Reference" value={ledgerEntry.externalRef} mono />
              <Row label="Provider ref" value={ledgerEntry.providerRef ?? '—'} mono />
              <Row label="Type" value={ledgerEntry.entryType} />
              <Row label="Amount" value={`${ledgerEntry.amount} ${ledgerEntry.currency}`} mono />
              <Row label="Occurred" value={new Date(ledgerEntry.occurredAt).toLocaleString()} />
              <Row label="Description" value={ledgerEntry.description ?? '—'} />
            </dl>
          ) : (
            <p className="muted">No ledger entry — we have no record of this money.</p>
          )}
        </div>
      </div>

      {history.length > 0 && (
        <div className="history">
          <h3>Decision history</h3>
          {history.map((record) => (
            <div key={record.id} className="history-item">
              <strong>{record.action.replace(/_/g, ' ')}</strong> by {record.resolvedByUsername} on{' '}
              {new Date(record.resolvedAt).toLocaleString()}
              <div className="muted">{record.note}</div>
            </div>
          ))}
        </div>
      )}

      {closed ? (
        <p className="notice">This exception is resolved. The trail above is permanent.</p>
      ) : (
        <div className="resolve">
          <label htmlFor="note">Why are you doing this?</label>
          <textarea
            id="note"
            rows={3}
            value={note}
            placeholder="Recorded against your name in the audit trail."
            onChange={(event) => setNote(event.target.value)}
          />

          {error && <p className="error">{error}</p>}

          <div className="actions">
            {actions.map((action) => (
              <button
                key={action}
                type="button"
                className={action === 'WRITE_OFF' ? 'danger' : ''}
                disabled={busy || note.trim().length === 0}
                onClick={() => onResolve(action, note)}
              >
                {action.replace(/_/g, ' ')}
              </button>
            ))}
          </div>
          {note.trim().length === 0 && <p className="hint">A decision needs a reason before it can be saved.</p>}
        </div>
      )}
    </>
  )
}

function Row({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="dl-row">
      <dt>{label}</dt>
      <dd className={mono ? 'mono' : undefined}>{value}</dd>
    </div>
  )
}

function Select<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string
  value: T | ''
  options: T[]
  onChange: (value: T | '') => void
}) {
  return (
    <label className="filter">
      <span>{label}</span>
      <select value={value} onChange={(event) => onChange(event.target.value as T | '')}>
        <option value="">Any</option>
        {options.map((option) => (
          <option key={option} value={option}>
            {option.replace(/_/g, ' ')}
          </option>
        ))}
      </select>
    </label>
  )
}
