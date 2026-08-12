import { useState, type ChangeEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api, ApiError, storedToken } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { PageResponse, Run, ScenarioResponse, SettlementFile } from '../api/types'

const DEFAULT_SCENARIO = {
  transactions: 500,
  seed: 20260812,
  currency: 'USD',
  missingPayouts: 6,
  missingLedgerEntries: 4,
  amountDrifts: 5,
  fxRoundings: 8,
  duplicateCharges: 3,
  unexpectedFees: 4,
  heuristicOnly: 25,
}

export default function FilesPage() {
  const { can } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const files = useQuery({
    queryKey: ['files'],
    queryFn: () => api<PageResponse<SettlementFile>>('/api/v1/settlement-files', { query: { size: 25 } }),
  })

  const runs = useQuery({
    queryKey: ['runs'],
    queryFn: () => api<PageResponse<Run>>('/api/v1/runs', { query: { size: 15 } }),
    // Runs are asynchronous, so this list refreshes while any of them is still working.
    refetchInterval: (query) =>
      query.state.data?.items.some((run) => run.status === 'PENDING' || run.status === 'RUNNING')
        ? 1500
        : false,
  })

  function report(message: string) {
    setNotice(message)
    setError(null)
    queryClient.invalidateQueries({ queryKey: ['files'] })
    queryClient.invalidateQueries({ queryKey: ['runs'] })
  }

  function fail(cause: unknown) {
    setNotice(null)
    setError(cause instanceof ApiError ? `${cause.code}: ${cause.message}` : 'Something went wrong.')
  }

  const generate = useMutation({
    mutationFn: () =>
      api<ScenarioResponse>('/api/v1/demo/scenario', { method: 'POST', body: DEFAULT_SCENARIO }),
    onSuccess: (result) =>
      report(
        result.file.newlyIngested === false
          ? 'That scenario was already ingested; the existing file was reused.'
          : `Seeded ${result.ledgerEntriesCreated} ledger entries and a ${result.file.lineCount}-line payout file.`,
      ),
    onError: fail,
  })

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData()
      form.append('file', file)

      // FormData must not carry an explicit Content-Type, so this one call bypasses the
      // JSON helper and sets only the Authorization header.
      const response = await fetch('/api/v1/settlement-files?provider=STRIPE', {
        method: 'POST',
        headers: { Authorization: `Bearer ${storedToken() ?? ''}` },
        body: form,
      })
      const body = await response.json()
      if (!response.ok) {
        throw new ApiError(response.status, body.code ?? 'UPLOAD_FAILED', body.detail ?? 'Upload failed.')
      }
      return body as SettlementFile
    },
    onSuccess: (file) =>
      report(
        file.newlyIngested === false
          ? 'Identical file already ingested — nothing was double-booked.'
          : `Ingested ${file.lineCount} provider rows from ${file.filename}.`,
      ),
    onError: fail,
  })

  const trigger = useMutation({
    mutationFn: (fileId: string) => api<Run>('/api/v1/runs', { method: 'POST', query: { fileId } }),
    onSuccess: () => report('Reconciliation started.'),
    onError: fail,
  })

  function onFileChosen(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (file) {
      upload.mutate(file)
    }
    event.target.value = ''
  }

  return (
    <div className="stack">
      <section className="panel">
        <div className="panel-head">
          <h2>Ingest a payout file</h2>
          <p className="muted">
            Files are identified by content hash, so uploading the same bytes twice is a no-op.
          </p>
        </div>

        <div className="actions">
          <label className="button">
            Upload provider CSV
            <input type="file" accept=".csv,text/csv" onChange={onFileChosen} hidden />
          </label>

          {can('ledger:write') && (
            <button
              type="button"
              className="primary"
              onClick={() => generate.mutate()}
              disabled={generate.isPending}
            >
              {generate.isPending ? 'Generating…' : 'Generate demo scenario'}
            </button>
          )}
        </div>

        {notice && <p className="notice">{notice}</p>}
        {error && <p className="error">{error}</p>}
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Settlement files</h2>
        </div>

        {files.isLoading && <p className="muted">Loading…</p>}

        <table className="grid">
          <thead>
            <tr>
              <th>File</th>
              <th>Provider</th>
              <th className="num">Rows</th>
              <th>Status</th>
              <th>Uploaded</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {files.data?.items.map((file) => (
              <tr key={file.id}>
                <td>
                  <div className="mono">{file.filename}</div>
                  <div className="muted tiny mono">sha256 {file.checksumSha256.slice(0, 16)}…</div>
                </td>
                <td>{file.provider}</td>
                <td className="num">{file.lineCount}</td>
                <td>
                  <span className={`chip chip-${file.status.toLowerCase()}`}>{file.status}</span>
                </td>
                <td className="muted">{new Date(file.uploadedAt).toLocaleString()}</td>
                <td className="right">
                  {can('run:trigger') && file.status === 'PARSED' && (
                    <button
                      type="button"
                      onClick={() => trigger.mutate(file.id)}
                      disabled={trigger.isPending}
                    >
                      Reconcile
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {files.data?.items.length === 0 && (
              <tr>
                <td colSpan={6} className="muted center">
                  No files yet. Upload one or generate a demo scenario.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>

      <section className="panel">
        <div className="panel-head">
          <h2>Reconciliation runs</h2>
        </div>

        <table className="grid">
          <thead>
            <tr>
              <th>Started</th>
              <th>Status</th>
              <th className="num">Lines</th>
              <th className="num">Exact</th>
              <th className="num">Heuristic</th>
              <th className="num">Unmatched</th>
              <th className="num">Matched</th>
              <th className="num">Exceptions</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {runs.data?.items.map((run) => (
              <tr key={run.id}>
                <td className="muted">{new Date(run.startedAt).toLocaleString()}</td>
                <td>
                  <span className={`chip chip-${run.status.toLowerCase()}`}>{run.status}</span>
                </td>
                <td className="num">{run.totalLines}</td>
                <td className="num">{run.matchedExact}</td>
                <td className="num">{run.matchedHeuristic}</td>
                <td className="num">{run.unmatched}</td>
                <td className="num strong">{(run.matchRate * 100).toFixed(1)}%</td>
                <td className="num">{run.discrepancyCount}</td>
                <td className="right">
                  <button type="button" onClick={() => navigate(`/runs/${run.id}`)}>
                    Report
                  </button>
                </td>
              </tr>
            ))}
            {runs.data?.items.length === 0 && (
              <tr>
                <td colSpan={9} className="muted center">
                  Nothing reconciled yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </section>
    </div>
  )
}
