import { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import Instructions from './pages/Instructions'
import CostDashboard from './pages/CostDashboard'
import Prompts from './pages/Prompts'
import Playground from './pages/Playground'
import ApiKeySettings from './pages/ApiKeySettings'
import Login from './pages/Login'
import Register from './pages/Register'

type Tab = 'instructions' | 'cost' | 'prompts' | 'playground' | 'settings'

function ProtectedApp() {
  const { isAuthenticated, email, logout } = useAuth()
  const [tab, setTab] = useState<Tab>('instructions')

  if (!isAuthenticated) return <Navigate to="/login" replace />

  return (
    <div className="shell">
      <header className="topbar">
        <span className="topbar-logo">LLMetrics</span>
        <nav className="topbar-nav">
          <button className={`nav-btn ${tab === 'instructions' ? 'active' : ''}`} onClick={() => setTab('instructions')}>
            Integrate
          </button>
          <button className={`nav-btn ${tab === 'cost' ? 'active' : ''}`} onClick={() => setTab('cost')}>
            Cost
          </button>
          <button className={`nav-btn ${tab === 'prompts' ? 'active' : ''}`} onClick={() => setTab('prompts')}>
            Prompts
          </button>
          <button className={`nav-btn ${tab === 'playground' ? 'active' : ''}`} onClick={() => setTab('playground')}>
            Playground
          </button>
          <button className={`nav-btn ${tab === 'settings' ? 'active' : ''}`} onClick={() => setTab('settings')}>
            API Keys
          </button>
        </nav>
        <div className="topbar-user">
          <span className="topbar-email">{email}</span>
          <button className="btn btn-outline btn-sm" onClick={logout}>
            Sign out
          </button>
        </div>
      </header>
      <main className="page">
        {tab === 'instructions' && <Instructions />}
        {tab === 'cost' && <CostDashboard />}
        {tab === 'prompts' && <Prompts />}
        {tab === 'playground' && <Playground />}
        {tab === 'settings' && <ApiKeySettings />}
      </main>
    </div>
  )
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/*" element={<ProtectedApp />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
