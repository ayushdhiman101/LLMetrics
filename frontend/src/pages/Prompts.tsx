import { useState, useEffect } from 'react'
import { api, type PromptResponse, type PromptVersionResponse } from '../api'

export default function Prompts() {
  const [prompts, setPrompts] = useState<PromptResponse[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [versions, setVersions] = useState<PromptVersionResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [loadingVersions, setLoadingVersions] = useState(false)

  // Create prompt form
  const [newName, setNewName] = useState('')
  const [creating, setCreating] = useState(false)

  // Create version form
  const [showVersionForm, setShowVersionForm] = useState(false)
  const [vForm, setVForm] = useState({ version: '', template: '', description: '', isActive: true })
  const [savingVersion, setSavingVersion] = useState(false)

  async function loadPrompts() {
    try {
      setPrompts(await api.listPrompts())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load prompts')
    }
  }

  async function loadVersions(name: string) {
    setLoadingVersions(true)
    try {
      setVersions(await api.listVersions(name))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load versions')
    } finally {
      setLoadingVersions(false)
    }
  }

  useEffect(() => { loadPrompts() }, [])

  function selectPrompt(name: string) {
    setSelected(name)
    setVersions([])
    setShowVersionForm(false)
    loadVersions(name)
  }

  async function handleCreatePrompt(e: React.FormEvent) {
    e.preventDefault()
    if (!newName.trim()) return
    setCreating(true)
    setError(null)
    try {
      await api.createPrompt(newName.trim())
      setNewName('')
      await loadPrompts()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create prompt')
    } finally {
      setCreating(false)
    }
  }

  async function handleCreateVersion(e: React.FormEvent) {
    e.preventDefault()
    if (!selected || !vForm.version.trim() || !vForm.template.trim()) return
    setSavingVersion(true)
    setError(null)
    try {
      await api.createVersion(selected, {
        version: vForm.version.trim(),
        template: vForm.template.trim(),
        description: vForm.description.trim() || undefined,
        isActive: vForm.isActive,
      })
      setVForm({ version: '', template: '', description: '', isActive: true })
      setShowVersionForm(false)
      await loadVersions(selected)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create version')
    } finally {
      setSavingVersion(false)
    }
  }

  async function handleSetActive(version: string) {
    if (!selected) return
    setError(null)
    try {
      await api.setActiveVersion(selected, version)
      await loadVersions(selected)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to set active version')
    }
  }

  return (
    <>
      <div className="section-title">Prompt Manager</div>
      <div className="section-sub">Versioned prompt templates with variable interpolation.</div>

      {error && <div className="error-banner">{error}</div>}

      <form className="form-row" onSubmit={handleCreatePrompt} style={{ marginBottom: 20 }}>
        <div className="form-group">
          <label>New Prompt Name</label>
          <input
            className="input"
            placeholder="e.g. summarize"
            value={newName}
            onChange={e => setNewName(e.target.value)}
          />
        </div>
        <button className="btn btn-primary" type="submit" disabled={creating} style={{ alignSelf: 'flex-end' }}>
          {creating ? 'Creating…' : 'Create Prompt'}
        </button>
      </form>

      <div className="prompts-layout">
        {/* Left: prompt list */}
        <div className="card" style={{ alignSelf: 'start' }}>
          <div className="card-title">Prompts</div>
          {prompts.length === 0
            ? <p className="empty">No prompts yet.</p>
            : (
              <ul className="prompt-list">
                {prompts.map(p => (
                  <li
                    key={p.id}
                    className={`prompt-item ${selected === p.name ? 'selected' : ''}`}
                    onClick={() => selectPrompt(p.name)}
                  >
                    {p.name}
                  </li>
                ))}
              </ul>
            )
          }
        </div>

        {/* Right: versions */}
        <div>
          {selected ? (
            <div className="card">
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 }}>
                <div className="card-title" style={{ marginBottom: 0 }}>
                  {selected} — versions
                </div>
                <button
                  className="btn btn-outline btn-sm"
                  onClick={() => setShowVersionForm(v => !v)}
                >
                  {showVersionForm ? 'Cancel' : '+ Add Version'}
                </button>
              </div>

              {showVersionForm && (
                <form className="expand-form" onSubmit={handleCreateVersion} style={{ marginBottom: 20 }}>
                  <div className="form-row" style={{ marginBottom: 0 }}>
                    <div className="form-group">
                      <label>Version</label>
                      <input
                        className="input"
                        placeholder="v2"
                        value={vForm.version}
                        onChange={e => setVForm(f => ({ ...f, version: e.target.value }))}
                      />
                    </div>
                    <div className="form-group">
                      <label>Description</label>
                      <input
                        className="input"
                        placeholder="optional"
                        value={vForm.description}
                        onChange={e => setVForm(f => ({ ...f, description: e.target.value }))}
                      />
                    </div>
                    <div className="form-group" style={{ justifyContent: 'flex-end' }}>
                      <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                        <input
                          type="checkbox"
                          checked={vForm.isActive}
                          onChange={e => setVForm(f => ({ ...f, isActive: e.target.checked }))}
                        />
                        Set active
                      </label>
                    </div>
                  </div>
                  <div className="form-group">
                    <label>Template</label>
                    <textarea
                      className="textarea"
                      placeholder={`Summarize the following in one sentence: {{content}}`}
                      value={vForm.template}
                      onChange={e => setVForm(f => ({ ...f, template: e.target.value }))}
                      rows={3}
                    />
                  </div>
                  <div>
                    <button className="btn btn-primary btn-sm" type="submit" disabled={savingVersion}>
                      {savingVersion ? 'Saving…' : 'Save Version'}
                    </button>
                  </div>
                </form>
              )}

              {loadingVersions
                ? <p className="loading">Loading versions…</p>
                : versions.length === 0
                  ? <p className="empty">No versions yet. Add one above.</p>
                  : (
                    <div className="version-list">
                      {versions.map(v => (
                        <div key={v.id} className="version-card">
                          <div className="version-header">
                            <span className="version-tag">{v.version}</span>
                            {v.isActive
                              ? <span className="badge badge-green">active</span>
                              : <span className="badge badge-muted">inactive</span>
                            }
                            {!v.isActive && (
                              <button
                                className="btn btn-outline btn-sm"
                                style={{ marginLeft: 'auto' }}
                                onClick={() => handleSetActive(v.version)}
                              >
                                Set active
                              </button>
                            )}
                          </div>
                          {v.description && (
                            <div style={{ fontSize: 12, color: 'var(--text-muted)', marginBottom: 6 }}>{v.description}</div>
                          )}
                          <div className="version-template">{v.template}</div>
                          <div className="version-meta">
                            Created {new Date(v.createdAt).toLocaleString()}
                          </div>
                        </div>
                      ))}
                    </div>
                  )
              }
            </div>
          ) : (
            <div className="card">
              <p className="empty">Select a prompt on the left to see its versions.</p>
            </div>
          )}
        </div>
      </div>
    </>
  )
}
