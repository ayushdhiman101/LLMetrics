import { useState, useEffect, useRef } from 'react'
import { api, streamCompletion, type StatusEvent, type PromptResponse, type PromptVersionResponse } from '../api'

const MODELS = [
  { id: 'gpt-4o',                    label: 'GPT-4o',           provider: 'OpenAI' },
  { id: 'gpt-4o-mini',               label: 'GPT-4o Mini',      provider: 'OpenAI' },
  { id: 'claude-haiku-4-5-20251001', label: 'Haiku 4.5',        provider: 'Anthropic' },
  { id: 'claude-sonnet-4-6',         label: 'Sonnet 4.6',       provider: 'Anthropic' },
  { id: 'claude-opus-4-7',           label: 'Opus 4.7',         provider: 'Anthropic' },
  { id: 'gemini-flash-latest',       label: 'Gemini Flash',     provider: 'Google' },
  { id: 'gemini-2.5-flash',          label: 'Gemini 2.5 Flash', provider: 'Google' },
]

type Mode = 'prompt' | 'raw'

interface StatusEntry {
  type: 'attempting' | 'fallback' | 'done'
  data: string
}

export default function Playground() {
  const [mode, setMode] = useState<Mode>('raw')

  const [prompts, setPrompts] = useState<PromptResponse[]>([])
  const [selectedPrompt, setSelectedPrompt] = useState('')
  const [versions, setVersions] = useState<PromptVersionResponse[]>([])
  const [selectedVersion, setSelectedVersion] = useState('')
  const [variables, setVariables] = useState<{ key: string; value: string }[]>([])

  const [message, setMessage] = useState('')
  const [selectedModels, setSelectedModels] = useState<string[]>(['gemini-flash-latest'])

  const [output, setOutput] = useState('')
  const [statusLog, setStatusLog] = useState<StatusEntry[]>([])
  const [finalModel, setFinalModel] = useState<string | null>(null)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [elapsed, setElapsed] = useState<number | null>(null)

  const abortRef = useRef<AbortController | null>(null)
  const outputRef = useRef<HTMLPreElement>(null)

  useEffect(() => {
    api.listPrompts().then(setPrompts).catch(() => {})
  }, [])

  useEffect(() => {
    if (!selectedPrompt) { setVersions([]); setSelectedVersion(''); return }
    api.listVersions(selectedPrompt).then(vs => {
      setVersions(vs)
      const active = vs.find(v => v.isActive)
      setSelectedVersion(active?.version ?? vs[0]?.version ?? '')
    }).catch(() => {})
  }, [selectedPrompt])

  useEffect(() => {
    if (outputRef.current) outputRef.current.scrollTop = outputRef.current.scrollHeight
  }, [output])

  function toggleModel(id: string) {
    setSelectedModels(prev =>
      prev.includes(id) ? prev.filter(m => m !== id) : [...prev, id]
    )
  }

  function addVariable() { setVariables(v => [...v, { key: '', value: '' }]) }
  function updateVariable(i: number, field: 'key' | 'value', val: string) {
    setVariables(v => v.map((e, idx) => idx === i ? { ...e, [field]: val } : e))
  }
  function removeVariable(i: number) { setVariables(v => v.filter((_, idx) => idx !== i)) }

  async function run() {
    if (selectedModels.length === 0) { setError('Select at least one model.'); return }
    if (mode === 'prompt' && !selectedPrompt) { setError('Select a prompt.'); return }
    if (mode === 'raw' && !message.trim()) { setError('Enter a message.'); return }

    setError(null)
    setOutput('')
    setStatusLog([])
    setFinalModel(null)
    setRunning(true)
    setElapsed(null)
    const t0 = Date.now()

    abortRef.current = new AbortController()
    const vars = Object.fromEntries(variables.filter(v => v.key).map(v => [v.key, v.value]))

    try {
      await streamCompletion(
        mode === 'prompt'
          ? { promptName: selectedPrompt, version: selectedVersion || undefined, variables: vars, models: selectedModels }
          : { message: message.trim(), models: selectedModels },
        token => setOutput(prev => prev + token),
        (e: StatusEvent) => {
          setStatusLog(prev => [...prev, e])
          if (e.type === 'done') setFinalModel(e.data)
        },
        abortRef.current.signal
      )
      setElapsed(Date.now() - t0)
    } catch (e: unknown) {
      if (e instanceof Error && e.name === 'AbortError') {
        setElapsed(Date.now() - t0)
      } else {
        setError(e instanceof Error ? e.message : 'Request failed')
      }
    } finally {
      setRunning(false)
    }
  }

  function stop() { abortRef.current?.abort() }

  function clear() { setOutput(''); setStatusLog([]); setFinalModel(null); setElapsed(null) }

  return (
    <>
      <div className="section-title">Playground</div>
      <div className="section-sub">Test prompts and models directly from the portal.</div>

      {error && <div className="error-banner">{error}</div>}

      <div className="pg-layout">
        {/* ── Left: config ── */}
        <div className="pg-config">
          <div className="pg-mode-bar">
            <button className={`pg-mode-btn${mode === 'raw' ? ' active' : ''}`} onClick={() => setMode('raw')}>Raw Message</button>
            <button className={`pg-mode-btn${mode === 'prompt' ? ' active' : ''}`} onClick={() => setMode('prompt')}>Prompt</button>
          </div>

          {mode === 'raw' ? (
            <div className="card" style={{ marginBottom: 14 }}>
              <div className="card-title">Message</div>
              <textarea className="textarea" placeholder="Type your message…" value={message}
                onChange={e => setMessage(e.target.value)} rows={5} />
            </div>
          ) : (
            <div className="card" style={{ marginBottom: 14 }}>
              <div className="card-title">Prompt</div>
              <div className="form-group" style={{ marginBottom: 10 }}>
                <label>Prompt</label>
                <select className="input" value={selectedPrompt} onChange={e => setSelectedPrompt(e.target.value)}>
                  <option value="">— select —</option>
                  {prompts.map(p => <option key={p.id} value={p.name}>{p.name}</option>)}
                </select>
              </div>
              {versions.length > 0 && (
                <div className="form-group" style={{ marginBottom: 10 }}>
                  <label>Version</label>
                  <select className="input" value={selectedVersion} onChange={e => setSelectedVersion(e.target.value)}>
                    {versions.map(v => <option key={v.id} value={v.version}>{v.version}{v.isActive ? ' (active)' : ''}</option>)}
                  </select>
                </div>
              )}
              {selectedPrompt && (
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                    <label style={{ fontSize: 12, color: 'var(--text-muted)', fontWeight: 500 }}>Variables</label>
                    <button className="btn btn-outline btn-sm" onClick={addVariable}>+ Add</button>
                  </div>
                  {variables.length === 0
                    ? <p className="empty" style={{ padding: '4px 0' }}>No variables — add if template uses {'{{placeholders}}'}.</p>
                    : variables.map((v, i) => (
                      <div key={i} className="pg-var-row">
                        <input className="input pg-var-input" placeholder="key" value={v.key} onChange={e => updateVariable(i, 'key', e.target.value)} />
                        <input className="input pg-var-input" placeholder="value" value={v.value} onChange={e => updateVariable(i, 'value', e.target.value)} />
                        <button className="btn btn-outline btn-sm pg-var-del" onClick={() => removeVariable(i)}>✕</button>
                      </div>
                    ))
                  }
                </div>
              )}
            </div>
          )}

          <div className="card" style={{ marginBottom: 14 }}>
            <div className="card-title">Models <span style={{ fontWeight: 400, textTransform: 'none', letterSpacing: 0 }}>(fallback order)</span></div>
            <div className="pg-model-grid">
              {MODELS.map(m => (
                <label key={m.id} className={`pg-model-chip${selectedModels.includes(m.id) ? ' selected' : ''}`}>
                  <input type="checkbox" checked={selectedModels.includes(m.id)} onChange={() => toggleModel(m.id)} style={{ display: 'none' }} />
                  <span className="pg-chip-label">{m.label}</span>
                  <span className="pg-chip-provider">{m.provider}</span>
                </label>
              ))}
            </div>
            {selectedModels.length > 1 && (
              <p style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 8 }}>
                {selectedModels.join(' → ')} — falls back on error
              </p>
            )}
          </div>

          <div style={{ display: 'flex', gap: 8 }}>
            {running
              ? <button className="btn btn-primary" style={{ flex: 1 }} onClick={stop}>■ Stop</button>
              : <button className="btn btn-primary" style={{ flex: 1 }} onClick={run}>▶ Run</button>
            }
            {(output || statusLog.length > 0) && !running && (
              <button className="btn btn-outline" onClick={clear}>Clear</button>
            )}
          </div>
        </div>

        {/* ── Right: output ── */}
        <div className="pg-output-panel">
          {/* Loading bar */}
          {running && <div className="pg-loader-bar"><div className="pg-loader-fill" /></div>}

          <div className="card pg-output-card">
            <div className="pg-output-header">
              <div className="card-title" style={{ marginBottom: 0 }}>Output</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                {finalModel && (
                  <span className="pg-final-badge">{finalModel}</span>
                )}
                {elapsed != null && (
                  <span style={{ fontSize: 11, color: 'var(--text-muted)' }}>{(elapsed / 1000).toFixed(2)}s</span>
                )}
                {running && <span className="pg-pulse-dot" />}
              </div>
            </div>
            <pre ref={outputRef} className="pg-output">
              {output || <span style={{ color: 'var(--text-muted)' }}>Response will stream here…</span>}
            </pre>
          </div>

          {/* Status log — below output */}
          {statusLog.length > 0 && (
            <div className="pg-status-log">
              {statusLog.map((s, i) => (
                <div key={i} className={`pg-status-item pg-status-${s.type}`}>
                  {s.type === 'attempting' && <span className="pg-status-icon">⟳</span>}
                  {s.type === 'fallback'   && <span className="pg-status-icon">↷</span>}
                  {s.type === 'done'       && <span className="pg-status-icon">✓</span>}
                  <span className="pg-status-text">
                    {s.type === 'attempting' && `Trying ${s.data}…`}
                    {s.type === 'fallback'   && `Fell back from ${s.data}`}
                    {s.type === 'done'       && `Responded via ${s.data}`}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  )
}
