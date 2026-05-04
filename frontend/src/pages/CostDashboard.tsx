import { useState, useEffect, useCallback, useRef } from 'react'
import { api, type UsageSummaryResponse, type Session } from '../api'
import StatCard from '../components/StatCard'
import DailyChart from '../components/DailyChart'
import ProviderChart from '../components/ProviderChart'
import ModelTable from '../components/ModelTable'

function today() {
  return new Date().toISOString().slice(0, 10)
}

function daysAgo(n: number) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return d.toISOString().slice(0, 10)
}

function fmtCost(v: number) {
  return v < 0.01 ? `$${v.toFixed(6)}` : `$${v.toFixed(4)}`
}

function fmtDateShort(iso: string) {
  return iso.slice(0, 10)
}

export default function CostDashboard() {
  const [from, setFrom] = useState(daysAgo(30))
  const [to, setTo] = useState(today())
  const [data, setData] = useState<UsageSummaryResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [sessions, setSessions] = useState<Session[]>([])
  const [activeSession, setActiveSession] = useState<Session | null>(null)
  const [selectedSessionId, setSelectedSessionId] = useState<string | null>(null)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [showNewInput, setShowNewInput] = useState(false)
  const [newName, setNewName] = useState('')
  const [renamingId, setRenamingId] = useState<string | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const dropdownRef = useRef<HTMLDivElement>(null)

  const selectedSession = sessions.find(s => s.id === selectedSessionId) ?? null
  const isOverall = selectedSessionId === null

  const displayFrom = selectedSession ? fmtDateShort(selectedSession.startedAt) : from
  const displayTo = selectedSession
    ? (selectedSession.endedAt ? fmtDateShort(selectedSession.endedAt) : today())
    : to

  const loadSessions = useCallback(async () => {
    try {
      const [list, active] = await Promise.all([
        api.listSessions(),
        api.getActiveSession(),
      ])
      setSessions(list)
      setActiveSession(active)
    } catch { /* non-fatal */ }
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await api.fetchSummary(displayFrom, displayTo, selectedSessionId ?? undefined))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load')
    } finally {
      setLoading(false)
    }
  }, [displayFrom, displayTo, selectedSessionId])

  useEffect(() => { loadSessions() }, [loadSessions])
  useEffect(() => { load() }, [load])

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const selectSession = (id: string | null) => {
    setSelectedSessionId(id)
    setDropdownOpen(false)
  }

  const handleStart = async () => {
    if (!newName.trim()) return
    try {
      const session = await api.startSession(newName.trim())
      setShowNewInput(false)
      setNewName('')
      await loadSessions()
      selectSession(session.id)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to start session')
    }
  }

  const handleStop = async () => {
    if (!activeSession) return
    try {
      await api.stopSession(activeSession.id)
      await loadSessions()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to stop session')
    }
  }

  const handleRenameSubmit = async (id: string) => {
    if (!renameValue.trim()) { setRenamingId(null); return }
    try {
      await api.renameSession(id, renameValue.trim())
      setRenamingId(null)
      await loadSessions()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to rename session')
    }
  }

  const startRename = (s: Session) => {
    setRenamingId(s.id)
    setRenameValue(s.name)
    setDropdownOpen(true)
  }

  return (
    <>
      <div className="section-title">Cost Dashboard</div>
      <div className="section-sub">Token spend and request volume across providers.</div>

      {/* Session bar */}
      <div className="session-bar">
        <div className="session-selector" ref={dropdownRef}>
          <button
            className="session-selector-btn"
            onClick={() => setDropdownOpen(o => !o)}
          >
            <span className="session-selector-label">
              {selectedSession ? selectedSession.name : 'Overall'}
            </span>
            {selectedSession?.active && (
              <span className="badge badge-green session-live-badge">Live</span>
            )}
            <span className="session-selector-arrow">{dropdownOpen ? '▴' : '▾'}</span>
          </button>

          {dropdownOpen && (
            <div className="session-dropdown">
              <div
                className={`session-option${!selectedSessionId ? ' session-option-active' : ''}`}
                onClick={() => selectSession(null)}
              >
                <span className="session-option-name">Overall</span>
                <span className="session-option-meta">All time</span>
              </div>

              {sessions.map(s => (
                <div
                  key={s.id}
                  className={`session-option${selectedSessionId === s.id ? ' session-option-active' : ''}`}
                >
                  {renamingId === s.id ? (
                    <input
                      className="input session-rename-input"
                      value={renameValue}
                      autoFocus
                      onChange={e => setRenameValue(e.target.value)}
                      onKeyDown={e => {
                        if (e.key === 'Enter') handleRenameSubmit(s.id)
                        if (e.key === 'Escape') setRenamingId(null)
                      }}
                      onBlur={() => handleRenameSubmit(s.id)}
                      onClick={e => e.stopPropagation()}
                    />
                  ) : (
                    <>
                      <span className="session-option-name" onClick={() => selectSession(s.id)}>
                        {s.name}
                      </span>
                      <span className="session-option-meta" onClick={() => selectSession(s.id)}>
                        {fmtDateShort(s.startedAt)}
                        {s.endedAt ? ` – ${fmtDateShort(s.endedAt)}` : ''}
                      </span>
                      {s.active && <span className="badge badge-green">Live</span>}
                      <button
                        className="session-rename-btn"
                        title="Rename"
                        onClick={e => { e.stopPropagation(); startRename(s) }}
                      >
                        ✏
                      </button>
                    </>
                  )}
                </div>
              ))}

              {sessions.length === 0 && (
                <div className="session-option" style={{ pointerEvents: 'none' }}>
                  <span className="session-option-meta">No sessions yet</span>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="session-bar-actions">
          {!showNewInput ? (
            <button className="btn btn-outline btn-sm" onClick={() => setShowNewInput(true)}>
              + New Session
            </button>
          ) : (
            <div className="session-new-form">
              <input
                className="input session-name-input"
                placeholder="Session name"
                value={newName}
                autoFocus
                onChange={e => setNewName(e.target.value)}
                onKeyDown={e => {
                  if (e.key === 'Enter') handleStart()
                  if (e.key === 'Escape') { setShowNewInput(false); setNewName('') }
                }}
              />
              <button className="btn btn-primary btn-sm" onClick={handleStart} disabled={!newName.trim()}>
                Start
              </button>
              <button
                className="btn btn-outline btn-sm"
                onClick={() => { setShowNewInput(false); setNewName('') }}
              >
                Cancel
              </button>
            </div>
          )}

          {activeSession && (
            <button className="btn btn-sm btn-stop" onClick={handleStop}>
              ■ Stop &ldquo;{activeSession.name}&rdquo;
            </button>
          )}
        </div>
      </div>

      {/* Date range */}
      <div className="form-row">
        <div className="form-group">
          <label>From</label>
          <input
            className="input"
            type="date"
            value={displayFrom}
            disabled={!isOverall}
            onChange={e => setFrom(e.target.value)}
          />
        </div>
        <div className="form-group">
          <label>To</label>
          <input
            className="input"
            type="date"
            value={displayTo}
            disabled={!isOverall}
            onChange={e => setTo(e.target.value)}
          />
        </div>
        <button
          className={`btn ${isOverall ? 'btn-primary' : 'btn-outline'}`}
          onClick={load}
          disabled={loading}
        >
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {data && (
        <>
          <div className="stat-grid">
            <StatCard label="Total Requests" value={data.totalRequests.toLocaleString()} />
            <StatCard
              label="Total Cost"
              value={fmtCost(data.totalCostUsd)}
              sub={`${displayFrom} → ${displayTo}`}
            />
            <StatCard
              label="Providers"
              value={String(data.byProvider.length)}
              sub={data.byProvider.map(p => p.provider).join(', ') || '—'}
            />
            <StatCard label="Models" value={String(data.byModel.length)} />
          </div>

          <div className="chart-grid">
            <DailyChart data={data.byDay} />
            <ProviderChart data={data.byProvider} />
          </div>

          <ModelTable data={data.byModel} />
        </>
      )}

      {!data && !loading && !error && (
        <p className="empty">No usage data yet. Send a completion from the Playground to get started.</p>
      )}
    </>
  )
}
