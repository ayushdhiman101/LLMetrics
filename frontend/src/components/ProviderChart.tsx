import { BarChart, Bar, Cell, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import type { ProviderBreakdown } from '../api'

const COLORS: Record<string, string> = {
  openai: '#10b981',
  gemini: '#6366f1',
  anthropic: '#f59e0b',
}

function providerColor(name: string) {
  return COLORS[name] ?? '#8892a4'
}

interface Props {
  data: ProviderBreakdown[]
}

export default function ProviderChart({ data }: Props) {
  const mapped = data.map(d => ({ ...d, fill: providerColor(d.provider) }))

  return (
    <div className="card">
      <div className="card-title">Cost by Provider</div>
      {data.length === 0
        ? <p className="empty">No data.</p>
        : (
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={mapped} layout="vertical" margin={{ top: 4, right: 16, left: 16, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#2a2d3e" horizontal={false} />
              <XAxis type="number" tickFormatter={v => `$${Number(v).toFixed(5)}`} tick={{ fill: '#8892a4', fontSize: 10 }} axisLine={false} tickLine={false} />
              <YAxis type="category" dataKey="provider" tick={{ fill: '#e2e8f0', fontSize: 12, fontWeight: 600 }} axisLine={false} tickLine={false} width={70} />
              <Tooltip
                contentStyle={{ background: '#1a1d27', border: '1px solid #2a2d3e', borderRadius: 8, fontSize: 12 }}
                formatter={(v: number) => [`$${v.toFixed(6)}`, 'Cost USD']}
              />
              <Bar dataKey="costUsd" radius={[0, 4, 4, 0]}>
                {mapped.map((entry, i) => (
                  <Cell key={i} fill={entry.fill} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        )
      }
    </div>
  )
}
