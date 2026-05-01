import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { DailyBreakdown } from '../api'

interface Props {
  data: DailyBreakdown[]
}

function fmtDay(day: string) {
  const d = new Date(day + 'T00:00:00')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

function fmtCost(v: number) {
  return `$${v.toFixed(6)}`
}

export default function DailyChart({ data }: Props) {
  const mapped = data.map(d => ({ ...d, day: fmtDay(d.day) }))

  return (
    <div className="card">
      <div className="card-title">Daily Cost (USD)</div>
      {data.length === 0
        ? <p className="empty">No data for this range.</p>
        : (
          <ResponsiveContainer width="100%" height={220}>
            <AreaChart data={mapped} margin={{ top: 4, right: 8, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id="costGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#6366f1" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#2a2d3e" />
              <XAxis dataKey="day" tick={{ fill: '#8892a4', fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis tickFormatter={v => `$${Number(v).toFixed(5)}`} tick={{ fill: '#8892a4', fontSize: 10 }} axisLine={false} tickLine={false} width={72} />
              <Tooltip
                contentStyle={{ background: '#1a1d27', border: '1px solid #2a2d3e', borderRadius: 8, fontSize: 12 }}
                labelStyle={{ color: '#e2e8f0', fontWeight: 600 }}
                formatter={(v: number) => [fmtCost(v), 'Cost']}
              />
              <Area type="monotone" dataKey="costUsd" stroke="#6366f1" strokeWidth={2} fill="url(#costGrad)" />
            </AreaChart>
          </ResponsiveContainer>
        )
      }
    </div>
  )
}
