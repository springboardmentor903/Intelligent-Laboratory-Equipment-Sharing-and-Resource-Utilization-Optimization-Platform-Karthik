import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import ResearcherDashboard from '../components/dashboard/ResearcherDashboard'
import ManagerDashboard from '../components/dashboard/ManagerDashboard'
import InstitutionAdminDashboard from '../components/dashboard/InstitutionAdminDashboard'

export default function Dashboard() {
  const { user } = useAuth()
  const roles = user?.roles || []

  const isAdmin = roles.includes('INSTITUTION_ADMIN') || roles.includes('SYSTEM_ADMIN')
  const isManager = roles.includes('LAB_MANAGER') || roles.includes('DEPARTMENT_HEAD')

  const defaultView = isAdmin ? 'ADMIN' : isManager ? 'MANAGER' : 'RESEARCHER'
  const [activeView, setActiveView] = useState(defaultView)

  return (
    <div className="dashboard-page" style={{ padding: '24px 16px', maxWidth: 1300, margin: '0 auto' }}>
      {/* Perspective Switcher for Admins and Managers */}
      {(isAdmin || isManager) && (
        <div style={{
          display: 'flex',
          justifyContent: 'flex-end',
          alignItems: 'center',
          gap: 8,
          marginBottom: 16
        }}>
          <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>
            Perspective:
          </span>
          <div style={{
            display: 'flex',
            background: 'var(--surface-secondary, #f1f5f9)',
            border: '1px solid var(--border)',
            borderRadius: 8,
            padding: 3
          }}>
            {isAdmin && (
              <button
                type="button"
                onClick={() => setActiveView('ADMIN')}
                style={{
                  border: 'none',
                  padding: '4px 10px',
                  borderRadius: 6,
                  fontSize: '0.8rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  background: activeView === 'ADMIN' ? 'var(--primary, #0f766e)' : 'transparent',
                  color: activeView === 'ADMIN' ? '#ffffff' : 'var(--text-muted)',
                }}
              >
                Executive
              </button>
            )}

            <button
              type="button"
              onClick={() => setActiveView('MANAGER')}
              style={{
                border: 'none',
                padding: '4px 10px',
                borderRadius: 6,
                fontSize: '0.8rem',
                fontWeight: 600,
                cursor: 'pointer',
                background: activeView === 'MANAGER' ? 'var(--primary, #0f766e)' : 'transparent',
                color: activeView === 'MANAGER' ? '#ffffff' : 'var(--text-muted)',
              }}
            >
              Manager
            </button>

            <button
              type="button"
              onClick={() => setActiveView('RESEARCHER')}
              style={{
                border: 'none',
                padding: '4px 10px',
                borderRadius: 6,
                fontSize: '0.8rem',
                fontWeight: 600,
                cursor: 'pointer',
                background: activeView === 'RESEARCHER' ? 'var(--primary, #0f766e)' : 'transparent',
                color: activeView === 'RESEARCHER' ? '#ffffff' : 'var(--text-muted)',
              }}
            >
              Researcher
            </button>
          </div>
        </div>
      )}

      {/* Render Active View */}
      {activeView === 'ADMIN' && <InstitutionAdminDashboard />}
      {activeView === 'MANAGER' && <ManagerDashboard />}
      {activeView === 'RESEARCHER' && <ResearcherDashboard />}
    </div>
  )
}
