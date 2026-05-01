import { useState, useEffect } from 'react'
import { api, type ProviderKeyResponse } from '../api'

const PROVIDERS = [
  { id: 'openai', label: 'OpenAI', placeholder: 'sk-...' },
  { id: 'gemini', label: 'Google Gemini', placeholder: 'AIza...' },
  { id: 'anthropic', label: 'Anthropic', placeholder: 'sk-ant-...' },
]

interface RowState {
  value: string
  saving: boolean
  deleting: boolean
  error: string | null
  success: boolean
}

function emptyRow(): RowState {
  return { value: '', saving: false, deleting: false, error: null, success: false }
}

export default function ApiKeySettings() {
  const [configured, setConfigured] = useState<Record<string, ProviderKeyResponse>>({})
  const [rows, setRows] = useState<Record<string, RowState>>(() =>
    Object.fromEntries(PROVIDERS.map(p => [p.id, emptyRow()]))
  )
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.listProviderKeys()
      .then(keys => {
        const map: Record<string, ProviderKeyResponse> = {}
        keys.forEach(k => { map[k.provider] = k })
        setConfigured(map)
      })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  function setRow(provider: string, patch: Partial<RowState>) {
    setRows(prev => ({ ...prev, [provider]: { ...prev[provider], ...patch } }))
  }

  async function handleSave(provider: string) {
    const key = rows[provider].value.trim()
    if (!key) return
    setRow(provider, { saving: true, error: null, success: false })
    try {
      await api.upsertProviderKey(provider, key)
      setRow(provider, { saving: false, success: true, value: '' })
      setConfigured(prev => ({
        ...prev,
        [provider]: { provider, configured: true, updatedAt: new Date().toISOString() },
      }))
      setTimeout(() => setRow(provider, { success: false }), 2000)
    } catch (err) {
      setRow(provider, { saving: false, error: err instanceof Error ? err.message : 'Save failed' })
    }
  }

  async function handleDelete(provider: string) {
    setRow(provider, { deleting: true, error: null })
    try {
      await api.deleteProviderKey(provider)
      setRow(provider, { deleting: false })
      setConfigured(prev => {
        const next = { ...prev }
        delete next[provider]
        return next
      })
    } catch (err) {
      setRow(provider, { deleting: false, error: err instanceof Error ? err.message : 'Delete failed' })
    }
  }

  if (loading) return <div className="page-loading">Loading…</div>

  return (
    <div className="settings-page">
      <h2 className="settings-title">Provider API Keys</h2>
      <p className="settings-desc">
        Add your own API keys for each provider. Keys are encrypted at rest.
        If a key is not set, the gateway falls back to the server-configured default.
      </p>
      <div className="provider-key-list">
        {PROVIDERS.map(p => {
          const row = rows[p.id]
          const isConfigured = !!configured[p.id]
          return (
            <div key={p.id} className="provider-key-row">
              <div className="provider-key-header">
                <span className="provider-name">{p.label}</span>
                <span className={`key-status ${isConfigured ? 'key-status-set' : 'key-status-unset'}`}>
                  {isConfigured ? 'Configured' : 'Not set'}
                </span>
              </div>
              {isConfigured && (
                <p className="key-updated">
                  Last updated: {new Date(configured[p.id].updatedAt).toLocaleString()}
                </p>
              )}
              {row.error && <div className="auth-error">{row.error}</div>}
              {row.success && <div className="auth-success">Saved!</div>}
              <div className="provider-key-actions">
                <input
                  type="password"
                  className="input"
                  placeholder={isConfigured ? '••••••••  (enter new key to replace)' : p.placeholder}
                  value={row.value}
                  onChange={e => setRow(p.id, { value: e.target.value, success: false, error: null })}
                  style={{ flex: 1 }}
                />
                <button
                  className="btn btn-primary"
                  onClick={() => handleSave(p.id)}
                  disabled={row.saving || !row.value.trim()}
                >
                  {row.saving ? 'Saving…' : 'Save'}
                </button>
                {isConfigured && (
                  <button
                    className="btn btn-outline btn-danger"
                    onClick={() => handleDelete(p.id)}
                    disabled={row.deleting}
                  >
                    {row.deleting ? 'Removing…' : 'Remove'}
                  </button>
                )}
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
