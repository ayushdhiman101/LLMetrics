import type { ModelBreakdown } from '../api'

interface Props {
  data: ModelBreakdown[]
}

function fmtCost(v: number) {
  return `$${v.toFixed(6)}`
}

const PROVIDER_COLORS: Record<string, string> = {
  openai: 'badge-green',
  gemini: 'badge-accent',
  anthropic: 'badge',
}

export default function ModelTable({ data }: Props) {
  if (data.length === 0) return <p className="empty">No model data.</p>

  return (
    <div className="card">
      <div className="card-title">Cost by Model</div>
      <table>
        <thead>
          <tr>
            <th>Model</th>
            <th>Provider</th>
            <th>Requests</th>
            <th>Input Tokens</th>
            <th>Output Tokens</th>
            <th>Cost (USD)</th>
          </tr>
        </thead>
        <tbody>
          {data.map(row => (
            <tr key={row.model}>
              <td style={{ fontFamily: 'monospace', fontSize: 12 }}>{row.model}</td>
              <td>
                <span className={`badge ${PROVIDER_COLORS[row.provider] ?? 'badge-muted'}`}>
                  {row.provider}
                </span>
              </td>
              <td>{row.requests.toLocaleString()}</td>
              <td>{row.inputTokens.toLocaleString()}</td>
              <td>{row.outputTokens.toLocaleString()}</td>
              <td style={{ fontVariantNumeric: 'tabular-nums' }}>{fmtCost(row.costUsd)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
