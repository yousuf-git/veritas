import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import LoginPage from './pages/LoginPage'
import FilesPage from './pages/FilesPage'
import RunReportPage from './pages/RunReportPage'
import ExceptionQueuePage from './pages/ExceptionQueuePage'

export default function App() {
  const { user, logout } = useAuth()

  if (!user) {
    return <LoginPage />
  }

  return (
    <div className="app">
      <header className="app-header">
        <div className="brand">
          <span className="brand-mark">◧</span>
          <div>
            <h1>Settlement Reconciliation</h1>
            <p className="brand-sub">Provider payouts against the internal ledger</p>
          </div>
        </div>

        <nav className="nav">
          <NavLink to="/files">Files &amp; runs</NavLink>
          <NavLink to="/exceptions">Exception queue</NavLink>
        </nav>

        <div className="session">
          <div className="session-name">{user.displayName}</div>
          <div className="session-role">{user.role.replace('_', ' ').toLowerCase()}</div>
          <button type="button" className="link-button" onClick={logout}>
            Sign out
          </button>
        </div>
      </header>

      <main className="app-main">
        <Routes>
          <Route path="/files" element={<FilesPage />} />
          <Route path="/runs/:runId" element={<RunReportPage />} />
          <Route path="/exceptions" element={<ExceptionQueuePage />} />
          <Route path="*" element={<Navigate to="/files" replace />} />
        </Routes>
      </main>
    </div>
  )
}
