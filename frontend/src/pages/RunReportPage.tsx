import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { PageResponse, RunReport, SettlementLine } from '../api/types'

export default function RunReportPage() {
  const { runId } = useParams<{ runId: string }>()

  const report = useQuery({
    queryKey: ['report', runId],
    queryFn: () => api<RunReport>(`/api/v1/runs/${runId}/report`),
    refetchInterval: (query) =>
      query.state.data && ['PENDING', 'RUNNING'].includes(query.state.data.status) ? 1500 : false,
  })

  const unmatched = useQuery({
    queryKey: ['unmatched', runId],
    queryFn: () =>
      api<PageResponse<SettlementLine>>(`/api/v1/runs/${runId}/unmatched-lines`, { query: { size: 50 } }),
    enabled: report.data?.status === 'COMPLETED',
  })

  if (report.isLoading) {
    return <p className="muted">Loading report…</p>
  }
  if (report.isError || !report.data) {
    return <p className="error">Could not load this run.</p>
  }

  const data = report.data
  const accounted = data.matchedAmountMinor + data.unmatchedAmountMinor
  const accountedShare = accounted === 0 ? 0 : data.matchedAmountMinor / accounted

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>{data.filename}</h2>
          <p className="muted mono tiny">
            run {data.runId} · sha256 {data.checksumSha256.slice(0, 24)}…
          </p>
        </div>

        {data.status !== 'COMPLETED' && (
          <p className={data.status === 'FAILED' ? 'error' : 'notice'}>
            Run status: {data.status}
            {data.error ? ` — ${data.error}` : ''}
          </p>
        )}

        <div className="metrics">
          <Metric label="Auto-matched" value={`${(data.matchRate * 100).toFixed(1)}%`} emphasis />
          <Metric label="Exact matches" value={data.matchedExact.toLocaleString()} />
          <Metric label="Heuristic matches" value={data.matchedHeuristic.toLocaleString()} />
          <Metric label="Unmatched rows" value={data.unmatched.toLocaleString()} />
          <Metric label="Provider rows" value={data.totalLines.toLocaleString()} />
          <Metric label="Exceptions raised" value={data.discrepancyCount.toLocaleString()} />
        </div>

        <div className="money-bar">
          <div className="money-bar-fill" style={{ width: `${(accountedShare * 100).toFixed(2)}%` }} />
        </div>
        <div className="money-legend">
          <span>
            <strong>
              {data.matchedAmount} {data.currency}
            </strong>{' '}
            accounted for
          </span>
          <span>
            <strong>
              {data.unmatchedAmount} {data.currency}
            </strong>{' '}
            outstanding on unmatched rows
          </span>
          <span>
            <strong>
              {data.outstandingAmount} {data.currency}
            </strong>{' '}
            in question across all exceptions
          </span>
        </div>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Discrepancies by classification</h2>
          <Link to="/exceptions" className="link">
            Work the queue →
          </Link>
        </div>

        <table className="grid">
          <thead>
            <tr>
              <th>Type</th>
              <th>Severity</th>
              <th className="num">Count</th>
              <th className="num">Amount at risk</th>
            </tr>
          </thead>
          <tbody>
            {data.discrepancies.map((row) => (
              <tr key={`${row.type}-${row.severity}`}>
                <td>
                  <span className="chip">{row.type.replace(/_/g, ' ')}</span>
                </td>
                <td>
                  <span className={`chip chip-${row.severity.toLowerCase()}`}>{row.severity}</span>
                </td>
                <td className="num">{row.count}</td>
                <td className="num mono">
                  {row.amountAtRisk} {data.currency}
                </td>
              </tr>
            ))}
            {data.discrepancies.length === 0 && (
              <tr>
                <td colSpan={4} className="muted center">
                  Nothing to explain — every line reconciled.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Unmatched provider rows</h2>
          <p className="muted">Drill down to the exact bytes the provider sent.</p>
        </div>

        <table className="grid">
          <thead>
            <tr>
              <th className="num">#</th>
              <th>Balance transaction</th>
              <th>Source</th>
              <th>Category</th>
              <th className="num">Gross</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {unmatched.data?.items.map((line) => (
              <tr key={line.id}>
                <td className="num">{line.lineNumber}</td>
                <td className="mono tiny">{line.providerTxnId}</td>
                <td className="mono tiny">{line.providerRef ?? '—'}</td>
                <td>{line.txnType}</td>
                <td className="num mono">
                  {line.gross} {line.currency}
                </td>
                <td className="muted">{new Date(line.createdAtProvider).toLocaleString()}</td>
              </tr>
            ))}
            {unmatched.data?.items.length === 0 && (
              <tr>
                <td colSpan={6} className="muted center">
                  Every provider row was accounted for.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  )
}

function Metric({ label, value, emphasis }: { label: string; value: string; emphasis?: boolean }) {
  return (
    <div className={emphasis ? 'metric metric-emphasis' : 'metric'}>
      <div className="metric-value">{value}</div>
      <div className="metric-label">{label}</div>
    </div>
  )
}
