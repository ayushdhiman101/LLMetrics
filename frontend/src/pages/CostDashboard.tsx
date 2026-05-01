import { useState, useEffect, useCallback } from 'react'
import { api, type UsageSummaryResponse } from '../api'
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

export default function CostDashboard() {
  const [from, setFrom] = useState(daysAgo(30))
  const [to, setTo] = useState(today())
  const [data, setData] = useState<UsageSummaryResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await api.fetchSummary(from, to))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load')
    } finally {
      setLoading(false)
    }
  }, [from, to])

  useEffect(() => { load() }, [load])

  return (
    <>
      <div className="section-title">Cost Dashboard</div>
      <div className="section-sub">Token spend and request volume across providers.</div>

      <div className="form-row">
        <div className="form-group">
          <label>From</label>
          <input className="input" type="date" value={from} onChange={e => setFrom(e.target.value)} />
        </div>
        <div className="form-group">
          <label>To</label>
          <input className="input" type="date" value={to} onChange={e => setTo(e.target.value)} />
        </div>
        <button className="btn btn-primary" onClick={load} disabled={loading}>
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
              sub={`${from} → ${to}`}
            />
            <StatCard
              label="Providers"
              value={String(data.byProvider.length)}
              sub={data.byProvider.map(p => p.provider).join(', ') || '—'}
            />
            <StatCard
              label="Models"
              value={String(data.byModel.length)}
            />
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
