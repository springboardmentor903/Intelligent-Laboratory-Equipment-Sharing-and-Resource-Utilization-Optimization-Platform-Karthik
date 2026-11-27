import { useEffect, useState, useCallback } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api from '../../api/axiosInstance'
import { useAuth } from '../../context/AuthContext'

export default function InstitutionAdminDashboard() {
  const { user } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()

  const isSysAdmin = user?.roles?.includes('SYSTEM_ADMIN')
  const [institutions, setInstitutions] = useState([])
  const [institutionId, setInstitutionId] = useState(
    searchParams.get('institutionId') || (user?.institutionId ? String(user.institutionId) : '')
  )

  const activeTab = searchParams.get('tab') || 'OVERVIEW'

  // Tab 1 state
  const [overview, setOverview] = useState(null)
  const [loadingOverview, setLoadingOverview] = useState(false)
  const [overviewError, setOverviewError] = useState('')

  // Tab 2 state
  const [procurementList, setProcurementList] = useState([])
  const [loadingProcurement, setLoadingProcurement] = useState(false)
  const [procurementFilter, setProcurementFilter] = useState('ALL')

  // Tab 3 state (Lifecycle & ROI)
  const [lifecyclePage, setLifecyclePage] = useState(0)
  const [lifecycleData, setLifecycleData] = useState(null)
  const [loadingLifecycle, setLoadingLifecycle] = useState(false)

  // Tab 4 state (System & Users)
  const [systemData, setSystemData] = useState(null)
  const [loadingSystem, setLoadingSystem] = useState(false)
  const [userSearch, setUserSearch] = useState('')
  const [userRoleFilter, setUserRoleFilter] = useState('')
  const [actionMessage, setActionMessage] = useState('')

  // Fetch institutions for system admin
  useEffect(() => {
    if (isSysAdmin) {
      api.get('/api/institutions')
        .then((res) => {
          const list = Array.isArray(res.data) ? res.data : []
          setInstitutions(list)
          if (!institutionId && list.length > 0) {
            setInstitutionId(String(list[0].id))
          }
        })
        .catch(() => {})
    }
  }, [isSysAdmin, institutionId])

  const handleTabChange = (tab) => {
    const next = new URLSearchParams(searchParams)
    next.set('tab', tab)
    setSearchParams(next)
  }

  const handleInstitutionChange = (newInstId) => {
    setInstitutionId(newInstId)
    const next = new URLSearchParams(searchParams)
    if (newInstId) {
      next.set('institutionId', newInstId)
    } else {
      next.delete('institutionId')
    }
    setSearchParams(next)
  }

  // Load Tab 1: Overview
  const loadOverview = useCallback(async () => {
    setLoadingOverview(true)
    setOverviewError('')
    try {
      const q = new URLSearchParams()
      if (institutionId) q.append('institutionId', institutionId)
      q.append('days', '30')
      const res = await api.get(`/api/dashboard/institution?${q.toString()}`)
      setOverview(res.data)
    } catch (err) {
      setOverviewError(err.response?.data?.message || err.message || 'Failed to load institution overview.')
    } finally {
      setLoadingOverview(false)
    }
  }, [institutionId])

  // Load Tab 2: Procurement
  const loadProcurement = useCallback(async () => {
    setLoadingProcurement(true)
    try {
      const q = new URLSearchParams()
      if (institutionId) q.append('institutionId', institutionId)
      const res = await api.get(`/api/dashboard/institution/procurement?${q.toString()}`)
      setProcurementList(Array.isArray(res.data) ? res.data : [])
    } catch {
      // Ignored
    } finally {
      setLoadingProcurement(false)
    }
  }, [institutionId])

  // Load Tab 3: Lifecycle & ROI
  const loadLifecycle = useCallback(async (page = 0) => {
    setLoadingLifecycle(true)
    try {
      const q = new URLSearchParams()
      if (institutionId) q.append('institutionId', institutionId)
      q.append('page', String(page))
      q.append('size', '10')
      const res = await api.get(`/api/dashboard/institution/lifecycle?${q.toString()}`)
      setLifecycleData(res.data)
      setLifecyclePage(page)
    } catch {
      // Ignored
    } finally {
      setLoadingLifecycle(false)
    }
  }, [institutionId])

  // Load Tab 4: System Monitoring
  const loadSystem = useCallback(async () => {
    setLoadingSystem(true)
    try {
      const q = new URLSearchParams()
      if (institutionId) q.append('institutionId', institutionId)
      const res = await api.get(`/api/dashboard/institution/system?${q.toString()}`)
      setSystemData(res.data)
    } catch {
      // Ignored
    } finally {
      setLoadingSystem(false)
    }
  }, [institutionId])

  useEffect(() => {
    if (activeTab === 'OVERVIEW') loadOverview()
    if (activeTab === 'PROCUREMENT') loadProcurement()
    if (activeTab === 'LIFECYCLE') loadLifecycle(0)
    if (activeTab === 'SYSTEM') loadSystem()
  }, [activeTab, loadOverview, loadProcurement, loadLifecycle, loadSystem])

  // Approve Department Head
  const handleApproveDeptHead = async (userId) => {
    try {
      await api.patch(`/api/users/${userId}/membership/approve`)
      setActionMessage('Department Head registration approved.')
      loadSystem()
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to approve Department Head.')
    }
  }

  // Reject Department Head
  const handleRejectDeptHead = async (userId) => {
    const reason = prompt('Enter rejection reason:')
    if (!reason) return
    try {
      await api.patch(`/api/users/${userId}/membership/reject`, { reason })
      setActionMessage('Department Head registration rejected.')
      loadSystem()
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to reject Department Head.')
    }
  }

  // Deactivate User
  const handleDeactivateUser = async (userId, userEmail) => {
    if (!window.confirm(`Are you sure you want to deactivate user ${userEmail}?`)) return
    try {
      await api.patch(`/api/users/${userId}/deactivate`)
      setActionMessage(`User ${userEmail} deactivated successfully.`)
      loadSystem()
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to deactivate user.')
    }
  }

  return (
    <div style={{ maxWidth: 1300, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* 1. Header with Institution Switcher */}
      <section style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 12,
        padding: '20px 24px',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 16
      }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
            <span style={{
              background: '#ccfbf1',
              color: '#0f766e',
              fontSize: '0.75rem',
              fontWeight: 700,
              padding: '2px 8px',
              borderRadius: 6
            }}>
              {isSysAdmin ? 'SYSTEM ADMINISTRATOR' : 'INSTITUTION ADMINISTRATOR'}
            </span>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Executive Platform Console</span>
          </div>
          <h1 style={{ fontSize: '1.65rem', fontWeight: 800, margin: 0, color: 'var(--text)' }}>
            Institution Overview & Governance
          </h1>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          {isSysAdmin && institutions.length > 0 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text-muted)' }}>Institution:</span>
              <select
                value={institutionId}
                onChange={(e) => handleInstitutionChange(e.target.value)}
                style={{
                  padding: '6px 12px',
                  borderRadius: 6,
                  border: '1px solid var(--border)',
                  background: 'var(--surface)',
                  color: 'var(--text)',
                  fontSize: '0.85rem',
                  fontWeight: 600
                }}
              >
                {institutions.map((inst) => (
                  <option key={inst.id} value={inst.id}>{inst.name}</option>
                ))}
              </select>
            </div>
          )}

          <button
            type="button"
            onClick={() => {
              if (activeTab === 'OVERVIEW') loadOverview()
              if (activeTab === 'PROCUREMENT') loadProcurement()
              if (activeTab === 'LIFECYCLE') loadLifecycle(lifecyclePage)
              if (activeTab === 'SYSTEM') loadSystem()
            }}
            style={{
              background: 'var(--surface-secondary)',
              border: '1px solid var(--border)',
              padding: '6px 14px',
              borderRadius: 6,
              fontSize: '0.85rem',
              cursor: 'pointer',
              color: 'var(--text)'
            }}
          >
            ↻ Refresh
          </button>
        </div>
      </section>

      {/* Action toast message */}
      {actionMessage && (
        <div style={{
          padding: '10px 16px',
          background: '#dcfce7',
          color: '#15803d',
          borderRadius: 8,
          border: '1px solid #86efac',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: '0.9rem'
        }}>
          <span>{actionMessage}</span>
          <button type="button" onClick={() => setActionMessage('')} style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#15803d' }}>✕</button>
        </div>
      )}

      {/* 2. Top Navigation Tabs */}
      <div style={{
        display: 'flex',
        gap: 8,
        borderBottom: '2px solid var(--border)',
        paddingBottom: 2,
        overflowX: 'auto'
      }}>
        {[
          { key: 'OVERVIEW', label: '📊 Overview & Sharing', icon: '📊' },
          { key: 'PROCUREMENT', label: '💡 Procurement Recommendations', icon: '💡' },
          { key: 'LIFECYCLE', label: '🔄 Equipment Lifecycle & ROI', icon: '🔄' },
          { key: 'SYSTEM', label: '🛡️ System & User Governance', icon: '🛡️' },
        ].map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => handleTabChange(tab.key)}
            style={{
              padding: '10px 18px',
              border: 'none',
              background: activeTab === tab.key ? 'var(--primary, #0f766e)' : 'transparent',
              color: activeTab === tab.key ? '#ffffff' : 'var(--text-muted)',
              borderRadius: '8px 8px 0 0',
              fontWeight: 600,
              fontSize: '0.9rem',
              cursor: 'pointer',
              whiteSpace: 'nowrap'
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* TAB 1: OVERVIEW */}
      {activeTab === 'OVERVIEW' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {loadingOverview && (
            <div style={{ textAlign: 'center', padding: '30px 0', color: 'var(--text-muted)' }}>
              Loading institution overview…
            </div>
          )}

          {overviewError && (
            <div style={{ padding: 14, background: '#fee2e2', color: '#991b1b', borderRadius: 8 }}>
              {overviewError}
            </div>
          )}

          {overview && (
            <>
              {/* Cost & Budget Summary Cards */}
              <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 14 }}>
                <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 18 }}>
                  <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>TOTAL USAGE REVENUE / COST</div>
                  <div style={{ fontSize: '1.9rem', fontWeight: 800, color: '#0f766e', marginTop: 4 }}>
                    ₹{Number(overview.costAndBudgetSummary?.totalUsageCost || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 4 }}>
                    Across all institution bookings
                  </div>
                </div>

                <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 18 }}>
                  <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>BUDGET ALLOCATED</div>
                  <div style={{ fontSize: '1.9rem', fontWeight: 800, color: 'var(--text)', marginTop: 4 }}>
                    ₹{Number(overview.costAndBudgetSummary?.totalBudgetAllocated || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 4 }}>
                    Department budgets for current fiscal year
                  </div>
                </div>

                <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 18 }}>
                  <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>BUDGET SPENT</div>
                  <div style={{ fontSize: '1.9rem', fontWeight: 800, color: '#0284c7', marginTop: 4 }}>
                    ₹{Number(overview.costAndBudgetSummary?.totalBudgetSpent || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 4 }}>
                    Spent amount from internal charges
                  </div>
                </div>

                <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 18 }}>
                  <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted)' }}>BUDGET UTILIZATION</div>
                  <div style={{ fontSize: '1.9rem', fontWeight: 800, color: (overview.costAndBudgetSummary?.budgetUtilizationPercentage || 0) > 90 ? '#dc2626' : '#16a34a', marginTop: 4 }}>
                    {Number(overview.costAndBudgetSummary?.budgetUtilizationPercentage || 0).toFixed(1)}%
                  </div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: 4 }}>
                    Overall fiscal consumption rate
                  </div>
                </div>
              </section>

              {/* Department Utilization Comparison vs Target (70%) */}
              <section style={{
                background: 'var(--surface)',
                border: '1px solid var(--border)',
                borderRadius: 12,
                padding: 22
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                  <div>
                    <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
                      🏢 Department Utilization vs Target (70%)
                    </h2>
                    <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      Comparison of actual department instrument usage rates against institutional 70% threshold
                    </p>
                  </div>
                </div>

                {(!overview.departmentUtilization || overview.departmentUtilization.length === 0) ? (
                  <div style={{ padding: '20px 0', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
                    No department utilization data recorded yet.
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                    {overview.departmentUtilization.map((d) => {
                      const rate = d.utilizationPercentage || 0.0
                      const isHigh = rate >= 70.0
                      const isMid = rate >= 40.0 && rate < 70.0
                      const barColor = isHigh ? '#16a34a' : isMid ? '#d97706' : '#dc2626'
                      return (
                        <div key={d.departmentId} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                            <strong style={{ color: 'var(--text)' }}>{d.departmentName}</strong>
                            <span style={{ fontWeight: 700, color: barColor }}>
                              {rate.toFixed(1)}% {rate >= 70.0 ? '(Target Met)' : ''}
                            </span>
                          </div>
                          <div style={{ height: 10, background: '#e2e8f0', borderRadius: 5, overflow: 'hidden', position: 'relative' }}>
                            <div style={{ width: `${Math.min(100, rate)}%`, height: '100%', background: barColor }} />
                            <div style={{ position: 'absolute', top: 0, left: '70%', width: 2, height: '100%', background: '#0f172a' }} title="Target 70%" />
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
              </section>

              {/* Monthly Trends & Idle Equipment Alerts */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 20 }}>
                {/* Monthly Trends Line Chart representation */}
                <section style={{
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: 12,
                  padding: 20
                }}>
                  <h2 style={{ fontSize: '1.1rem', fontWeight: 700, margin: '0 0 12px', color: 'var(--text)' }}>
                    📈 Monthly Utilization Trends
                  </h2>
                  {(!overview.monthlyUtilizationTrends || overview.monthlyUtilizationTrends.length === 0) ? (
                    <div style={{ padding: '24px 0', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                      Historical monthly snapshot trends will appear once snapshots are captured.
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                      {overview.monthlyUtilizationTrends.map((t) => (
                        <div key={t.snapshotDate} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid var(--border)', paddingBottom: 6 }}>
                          <span style={{ fontSize: '0.85rem', color: 'var(--text)' }}>{t.snapshotDate}</span>
                          <span style={{ fontWeight: 700, color: '#0284c7', fontSize: '0.85rem' }}>
                            {t.averageUtilizationPercentage?.toFixed(1) || '0.0'}% avg utilization
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </section>

                {/* Idle Equipment Alerts */}
                <section style={{
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: 12,
                  padding: 20
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                    <h2 style={{ fontSize: '1.1rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
                      ⚠️ Idle Equipment Alerts
                    </h2>
                    <span style={{ fontSize: '0.75rem', color: '#b45309', background: '#fef3c7', padding: '2px 8px', borderRadius: 6, fontWeight: 700 }}>
                      0 bookings in 30 days
                    </span>
                  </div>

                  {(!overview.idleEquipmentAlerts || overview.idleEquipmentAlerts.length === 0) ? (
                    <div style={{ padding: '24px 0', color: '#16a34a', fontSize: '0.85rem' }}>
                      ✓ Excellent! No idle equipment detected across the institution.
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {overview.idleEquipmentAlerts.slice(0, 5).map((item) => (
                        <div
                          key={item.equipmentId}
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            background: '#fffbeb',
                            border: '1px solid #fde68a',
                            borderRadius: 8,
                            padding: '8px 12px'
                          }}
                        >
                          <div>
                            <strong style={{ fontSize: '0.85rem', color: '#78350f' }}>{item.equipmentName}</strong>
                            <div style={{ fontSize: '0.75rem', color: '#92400e' }}>
                              {item.departmentName || 'No Department'} • {item.category || 'General'}
                            </div>
                          </div>
                          <Link
                            to="/equipment"
                            style={{
                              fontSize: '0.75rem',
                              fontWeight: 600,
                              color: '#b45309',
                              textDecoration: 'underline'
                            }}
                          >
                            Inspect →
                          </Link>
                        </div>
                      ))}
                    </div>
                  )}
                </section>
              </div>

              {/* Inter-Institution Sharing & Cross-Department Sharing */}
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: 20 }}>
                {/* Inter-Institution Sharing Analytics */}
                <section style={{
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: 12,
                  padding: 20
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                    <h2 style={{ fontSize: '1.1rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
                      🤝 Inter-Institution Sharing
                    </h2>
                    <Link to="/sharing-analytics" style={{ fontSize: '0.85rem', color: 'var(--accent-ocean)', fontWeight: 600 }}>
                      Full Analytics →
                    </Link>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 10 }}>
                    <div style={{ background: 'var(--surface-secondary)', border: '1px solid var(--border)', borderRadius: 8, padding: 12 }}>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>ACTIVE AGREEMENTS</span>
                      <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#0f766e', marginTop: 2 }}>
                        {overview.sharingAnalytics?.activeAgreementsCount ?? 0}
                      </div>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        of {overview.sharingAnalytics?.totalAgreementsCount ?? 0} total
                      </span>
                    </div>

                    <div style={{ background: 'var(--surface-secondary)', border: '1px solid var(--border)', borderRadius: 8, padding: 12 }}>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', fontWeight: 600 }}>SHARED BOOKING HOURS</span>
                      <div style={{ fontSize: '1.6rem', fontWeight: 800, color: '#0284c7', marginTop: 2 }}>
                        {overview.sharingAnalytics?.totalSharedBookingHours != null ? Number(overview.sharingAnalytics.totalSharedBookingHours).toFixed(1) : '0.0'}h
                      </div>
                      <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>External usage</span>
                    </div>

                    <div style={{ gridColumn: 'span 2', background: '#f0fdf4', border: '1px solid #bbf7d0', borderRadius: 8, padding: 12 }}>
                      <span style={{ fontSize: '0.75rem', color: '#166534', fontWeight: 600 }}>ACCRUED SHARING REVENUE</span>
                      <div style={{ fontSize: '1.7rem', fontWeight: 800, color: '#15803d', marginTop: 2 }}>
                        ₹{Number(overview.sharingAnalytics?.accruedSharingRevenue || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                      </div>
                      <span style={{ fontSize: '0.75rem', color: '#166534' }}>
                        Partner billing receivables
                      </span>
                    </div>
                  </div>
                </section>

                {/* Cross-Department Sharing */}
                <section style={{
                  background: 'var(--surface)',
                  border: '1px solid var(--border)',
                  borderRadius: 12,
                  padding: 20
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                    <h2 style={{ fontSize: '1.1rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
                      🔄 Cross-Department Collaboration
                    </h2>
                    <span style={{ fontSize: '0.8rem', fontWeight: 700, color: '#0284c7' }}>
                      {overview.crossDepartmentSharing?.totalCrossDepartmentHours?.toFixed(1) || '0.0'} total hours
                    </span>
                  </div>

                  {(!overview.crossDepartmentSharing?.topDepartmentPairs || overview.crossDepartmentSharing.topDepartmentPairs.length === 0) ? (
                    <div style={{ padding: '24px 0', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                      No cross-department bookings recorded in this period.
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                      {overview.crossDepartmentSharing.topDepartmentPairs.map((pair, idx) => (
                        <div
                          key={idx}
                          style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'center',
                            background: 'var(--surface-secondary)',
                            border: '1px solid var(--border)',
                            borderRadius: 8,
                            padding: '8px 12px',
                            fontSize: '0.85rem'
                          }}
                        >
                          <div>
                            <span style={{ fontWeight: 700, color: 'var(--text)' }}>{pair.equipmentDepartmentName}</span>
                            <span style={{ color: 'var(--text-muted)', margin: '0 6px' }}>↔</span>
                            <span style={{ fontWeight: 600, color: '#0f766e' }}>{pair.userDepartmentName}</span>
                          </div>
                          <div style={{ textAlign: 'right' }}>
                            <strong style={{ color: '#0284c7' }}>{pair.sharedHours?.toFixed(1)}h</strong>
                            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginLeft: 6 }}>
                              ({pair.bookingCount} bookings)
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </section>
              </div>
            </>
          )}
        </div>
      )}

      {/* TAB 2: PROCUREMENT RECOMMENDATIONS */}
      {activeTab === 'PROCUREMENT' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Filter Pills */}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {['ALL', 'HIGH_DEMAND', 'LOW_UTILIZATION', 'HIGH_MAINTENANCE'].map((f) => (
              <button
                key={f}
                type="button"
                onClick={() => setProcurementFilter(f)}
                style={{
                  padding: '6px 14px',
                  borderRadius: 6,
                  border: '1px solid var(--border)',
                  background: procurementFilter === f ? 'var(--primary, #0f766e)' : 'var(--surface)',
                  color: procurementFilter === f ? '#ffffff' : 'var(--text)',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: 'pointer'
                }}
              >
                {f.replaceAll('_', ' ')}
              </button>
            ))}
          </div>

          {loadingProcurement ? (
            <div style={{ textAlign: 'center', padding: '30px 0', color: 'var(--text-muted)' }}>
              Calculating automated procurement and lifecycle recommendations…
            </div>
          ) : procurementList.length === 0 ? (
            <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 30, textAlign: 'center', color: 'var(--text-muted)' }}>
              No procurement recommendations triggered. All laboratory equipment operating within normal limits.
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 14 }}>
              {procurementList
                .filter((r) => procurementFilter === 'ALL' || r.reasonCode === procurementFilter)
                .map((rec) => {
                  const badgeColor = rec.reasonCode === 'HIGH_DEMAND'
                    ? '#b45309'
                    : rec.reasonCode === 'HIGH_MAINTENANCE'
                      ? '#dc2626'
                      : '#0284c7'
                  const badgeBg = rec.reasonCode === 'HIGH_DEMAND'
                    ? '#fef3c7'
                    : rec.reasonCode === 'HIGH_MAINTENANCE'
                      ? '#fee2e2'
                      : '#e0f2fe'
                  return (
                    <div
                      key={rec.equipmentId}
                      style={{
                        background: 'var(--surface)',
                        border: '1px solid var(--border)',
                        borderRadius: 10,
                        padding: 18,
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'space-between',
                        gap: 12
                      }}
                    >
                      <div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8 }}>
                          <span style={{
                            background: badgeBg,
                            color: badgeColor,
                            fontSize: '0.75rem',
                            fontWeight: 700,
                            padding: '3px 8px',
                            borderRadius: 6
                          }}>
                            {rec.reasonCode?.replaceAll('_', ' ')}
                          </span>
                          <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                            {rec.departmentName || 'General'}
                          </span>
                        </div>
                        <strong style={{ display: 'block', fontSize: '1.05rem', color: 'var(--text)', marginTop: 8 }}>
                          {rec.equipmentName}
                        </strong>
                        <p style={{ margin: '8px 0 0', fontSize: '0.85rem', color: 'var(--text)' }}>
                          {rec.recommendation}
                        </p>
                      </div>

                      <div style={{
                        borderTop: '1px solid var(--border)',
                        paddingTop: 10,
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        fontSize: '0.8rem',
                        color: 'var(--text-muted)'
                      }}>
                        {rec.bookingRatePercentage != null && (
                          <span>Booking Rate: <strong>{rec.bookingRatePercentage.toFixed(1)}%</strong></span>
                        )}
                        {rec.waitlistCount != null && (
                          <span>Waitlist: <strong>{rec.waitlistCount}</strong></span>
                        )}
                        {rec.maintenanceCostPercentage != null && (
                          <span>Maint/Cost: <strong>{rec.maintenanceCostPercentage.toFixed(1)}%</strong></span>
                        )}
                      </div>
                    </div>
                  )
                })}
            </div>
          )}
        </div>
      )}

      {/* TAB 3: LIFECYCLE & ROI */}
      {activeTab === 'LIFECYCLE' && (
        <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 12, padding: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
            <div>
              <h2 style={{ fontSize: '1.15rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
                Equipment Lifecycle & ROI Analysis
              </h2>
              <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                ROI formula: ((Usage Charges − Maintenance Cost) / Purchase Cost) × 100
              </p>
            </div>
          </div>

          {loadingLifecycle ? (
            <div style={{ textAlign: 'center', padding: '30px 0', color: 'var(--text-muted)' }}>
              Loading equipment lifecycle and ROI ledger…
            </div>
          ) : (!lifecycleData?.content || lifecycleData.content.length === 0) ? (
            <div style={{ textAlign: 'center', padding: '24px 0', color: 'var(--text-muted)' }}>
              No equipment lifecycle records found.
            </div>
          ) : (
            <>
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                  <thead>
                    <tr style={{ borderBottom: '2px solid var(--border)', color: 'var(--text-muted)' }}>
                      <th style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>Equipment & Tag</th>
                      <th style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>Age</th>
                      <th style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>Status</th>
                      <th style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>Calibration</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>Work Orders</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>Downtime (h)</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>Purchase Cost</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>Maint Cost</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>Usage Revenue</th>
                      <th style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>ROI %</th>
                    </tr>
                  </thead>
                  <tbody>
                    {lifecycleData.content.map((item) => {
                      const roi = item.roiPercentage
                      const roiColor = roi == null ? 'var(--text-muted)' : roi >= 0 ? '#16a34a' : '#dc2626'
                      const roiBg = roi == null ? 'var(--surface-secondary)' : roi >= 0 ? '#dcfce7' : '#fee2e2'
                      return (
                        <tr key={item.equipmentId} style={{ borderBottom: '1px solid var(--border)' }}>
                          <td style={{ padding: '10px 12px' }}>
                            <div style={{ fontWeight: 600, color: 'var(--text)' }}>{item.equipmentName}</div>
                            <small style={{ color: 'var(--text-muted)' }}>{item.assetTag || 'Tag N/A'}</small>
                          </td>
                          <td style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>
                            {item.ageMonths != null ? `${item.ageMonths} mo` : '—'}
                          </td>
                          <td style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>
                            <span style={{
                              fontSize: '0.75rem',
                              fontWeight: 700,
                              padding: '2px 6px',
                              borderRadius: 4,
                              background: item.status === 'AVAILABLE' ? '#dcfce7' : '#fef3c7',
                              color: item.status === 'AVAILABLE' ? '#15803d' : '#b45309'
                            }}>
                              {item.status}
                            </span>
                          </td>
                          <td style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>
                            <span style={{
                              fontSize: '0.75rem',
                              fontWeight: 700,
                              padding: '2px 6px',
                              borderRadius: 4,
                              background: item.latestCalibrationState === 'VALID' ? '#dcfce7' : item.latestCalibrationState === 'DUE_SOON' ? '#fef3c7' : '#fee2e2',
                              color: item.latestCalibrationState === 'VALID' ? '#15803d' : item.latestCalibrationState === 'DUE_SOON' ? '#b45309' : '#dc2626'
                            }}>
                              {item.latestCalibrationState || 'NONE'}
                            </span>
                          </td>
                          <td style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                            {item.workOrderCount ?? 0}
                          </td>
                          <td style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                            {item.downtimeHours != null ? Number(item.downtimeHours).toFixed(1) : '0.0'}
                          </td>
                          <td style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                            {item.purchaseCost != null ? `₹${Number(item.purchaseCost).toLocaleString('en-IN', { minimumFractionDigits: 2 })}` : '—'}
                          </td>
                          <td style={{ padding: '10px 12px', textAlign: 'right', color: '#ea580c', whiteSpace: 'nowrap' }}>
                            ₹{Number(item.maintenanceCost || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                          </td>
                          <td style={{ padding: '10px 12px', textAlign: 'right', color: '#0284c7', fontWeight: 600, whiteSpace: 'nowrap' }}>
                            ₹{Number(item.usageCharges || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                          </td>
                          <td style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                            <span style={{
                              fontWeight: 700,
                              padding: '2px 8px',
                              borderRadius: 6,
                              background: roiBg,
                              color: roiColor
                            }}>
                              {roi != null ? `${roi.toFixed(1)}%` : 'N/A'}
                            </span>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>

              {/* Pagination controls */}
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 16 }}>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                  Page {lifecyclePage + 1} of {lifecycleData.totalPages || 1} ({lifecycleData.totalElements || 0} total)
                </span>
                <div style={{ display: 'flex', gap: 8 }}>
                  <button
                    type="button"
                    disabled={lifecyclePage <= 0}
                    onClick={() => loadLifecycle(lifecyclePage - 1)}
                    style={{
                      padding: '4px 12px',
                      borderRadius: 6,
                      border: '1px solid var(--border)',
                      background: 'var(--surface-secondary)',
                      cursor: lifecyclePage <= 0 ? 'not-allowed' : 'pointer'
                    }}
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    disabled={lifecyclePage >= (lifecycleData.totalPages || 1) - 1}
                    onClick={() => loadLifecycle(lifecyclePage + 1)}
                    style={{
                      padding: '4px 12px',
                      borderRadius: 6,
                      border: '1px solid var(--border)',
                      background: 'var(--surface-secondary)',
                      cursor: lifecyclePage >= (lifecycleData.totalPages || 1) - 1 ? 'not-allowed' : 'pointer'
                    }}
                  >
                    Next
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      )}

      {/* TAB 4: SYSTEM MONITORING & USER MANAGEMENT */}
      {activeTab === 'SYSTEM' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
          {loadingSystem ? (
            <div style={{ textAlign: 'center', padding: '30px 0', color: 'var(--text-muted)' }}>
              Loading platform telemetry and governance records…
            </div>
          ) : systemData && (
            <>
              {/* Telemetry metrics */}
              <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 14 }}>
                <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>REGISTERED USERS</span>
                  <div style={{ fontSize: '1.8rem', fontWeight: 800, color: 'var(--text)', marginTop: 4 }}>
                    {systemData.totalUsers ?? 0}
                  </div>
                </div>

                <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>ACTIVE EQUIPMENT</span>
                  <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#16a34a', marginTop: 4 }}>
                    {systemData.activeEquipmentCount ?? 0}
                  </div>
                </div>

                <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>UNDER MAINTENANCE</span>
                  <div style={{ fontSize: '1.8rem', fontWeight: 800, color: '#d97706', marginTop: 4 }}>
                    {systemData.equipmentUnderMaintenanceCount ?? 0}
                  </div>
                </div>

                <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>FAILED NOTIFICATIONS (7D)</span>
                  <div style={{ fontSize: '1.8rem', fontWeight: 800, color: (systemData.failedNotificationsLast7Days || 0) > 0 ? '#dc2626' : '#16a34a', marginTop: 4 }}>
                    {systemData.failedNotificationsLast7Days ?? 0}
                  </div>
                  {(systemData.failedNotificationsLast7Days || 0) > 0 && (
                    <small style={{ color: '#dc2626' }}>Delivery warnings detected</small>
                  )}
                </div>

                <div style={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10, padding: 16 }}>
                  <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 600 }}>LAST SNAPSHOT DATE</span>
                  <div style={{ fontSize: '1.2rem', fontWeight: 700, color: 'var(--text)', marginTop: 8 }}>
                    {systemData.lastUtilizationSnapshotDate || 'None captured'}
                  </div>
                </div>
              </section>

              {/* Pending Department Heads Approvals */}
              {systemData.pendingDepartmentHeads && systemData.pendingDepartmentHeads.length > 0 && (
                <section style={{
                  background: '#fffbeb',
                  border: '1px solid #fde68a',
                  borderRadius: 12,
                  padding: 20
                }}>
                  <h3 style={{ fontSize: '1.05rem', fontWeight: 700, margin: '0 0 10px', color: '#78350f' }}>
                    ⏳ Pending Department Head Registrations
                  </h3>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {systemData.pendingDepartmentHeads.map((u) => (
                      <div
                        key={u.id}
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          background: '#ffffff',
                          padding: '10px 14px',
                          borderRadius: 8,
                          border: '1px solid #fef3c7'
                        }}
                      >
                        <div>
                          <strong>{u.fullName || u.email}</strong>
                          <span style={{ color: '#78350f', marginLeft: 8, fontSize: '0.85rem' }}>
                            ({u.email})
                          </span>
                        </div>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button
                            type="button"
                            onClick={() => handleApproveDeptHead(u.id)}
                            style={{
                              background: '#16a34a',
                              color: '#ffffff',
                              border: 'none',
                              padding: '6px 12px',
                              borderRadius: 6,
                              fontSize: '0.8rem',
                              fontWeight: 600,
                              cursor: 'pointer'
                            }}
                          >
                            Approve
                          </button>
                          <button
                            type="button"
                            onClick={() => handleRejectDeptHead(u.id)}
                            style={{
                              background: '#dc2626',
                              color: '#ffffff',
                              border: 'none',
                              padding: '6px 12px',
                              borderRadius: 6,
                              fontSize: '0.8rem',
                              fontWeight: 600,
                              cursor: 'pointer'
                            }}
                          >
                            Reject
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {/* Active Users Table & Deactivation */}
              <section style={{
                background: 'var(--surface)',
                border: '1px solid var(--border)',
                borderRadius: 12,
                padding: 20
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12, marginBottom: 14 }}>
                  <div>
                    <h3 style={{ fontSize: '1.1rem', fontWeight: 700, margin: 0, color: 'var(--text)' }}>
                      Institution Users & Account Controls
                    </h3>
                    <p style={{ margin: '4px 0 0', fontSize: '0.85rem', color: 'var(--text-muted)' }}>
                      Manage staff and student permissions. Only non-admin users may be deactivated.
                    </p>
                  </div>

                  <div style={{ display: 'flex', gap: 10 }}>
                    <input
                      type="text"
                      placeholder="Search users…"
                      value={userSearch}
                      onChange={(e) => setUserSearch(e.target.value)}
                      style={{
                        padding: '6px 12px',
                        borderRadius: 6,
                        border: '1px solid var(--border)',
                        background: 'var(--surface)',
                        color: 'var(--text)',
                        fontSize: '0.85rem'
                      }}
                    />

                    <select
                      value={userRoleFilter}
                      onChange={(e) => setUserRoleFilter(e.target.value)}
                      style={{
                        padding: '6px 12px',
                        borderRadius: 6,
                        border: '1px solid var(--border)',
                        background: 'var(--surface)',
                        color: 'var(--text)',
                        fontSize: '0.85rem'
                      }}
                    >
                      <option value="">All Roles</option>
                      <option value="RESEARCHER_STUDENT">Researcher / Student</option>
                      <option value="LAB_TECHNICIAN">Lab Technician</option>
                      <option value="LAB_MANAGER">Lab Manager</option>
                      <option value="DEPARTMENT_HEAD">Department Head</option>
                      <option value="INSTITUTION_ADMIN">Institution Admin</option>
                    </select>
                  </div>
                </div>

                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                    <thead>
                      <tr style={{ borderBottom: '2px solid var(--border)', color: 'var(--text-muted)' }}>
                        <th style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>Name & Email</th>
                        <th style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>Roles</th>
                        <th style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>Membership Status</th>
                        <th style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(systemData.users || [])
                        .filter((u) => {
                          const matchesSearch = !userSearch ||
                            (u.fullName && u.fullName.toLowerCase().includes(userSearch.toLowerCase())) ||
                            (u.email && u.email.toLowerCase().includes(userSearch.toLowerCase()))
                          const matchesRole = !userRoleFilter || (u.roles && u.roles.includes(userRoleFilter))
                          return matchesSearch && matchesRole
                        })
                        .map((u) => {
                          const isAdmin = (u.roles || []).some((r) => ['INSTITUTION_ADMIN', 'SYSTEM_ADMIN'].includes(r))
                          const isDeactivated = u.membershipStatus === 'DEACTIVATED'
                          return (
                            <tr key={u.id} style={{ borderBottom: '1px solid var(--border)' }}>
                              <td style={{ padding: '10px 12px' }}>
                                <div style={{ fontWeight: 600, color: 'var(--text)' }}>{u.fullName || '—'}</div>
                                <div style={{ color: 'var(--text-muted)', fontSize: '0.8rem' }}>{u.email}</div>
                              </td>
                              <td style={{ padding: '10px 12px' }}>
                                {(u.roles || []).map((r) => (
                                  <span
                                    key={r}
                                    style={{
                                      display: 'inline-block',
                                      background: 'var(--surface-secondary)',
                                      color: 'var(--text)',
                                      padding: '2px 6px',
                                      borderRadius: 4,
                                      fontSize: '0.75rem',
                                      fontWeight: 600,
                                      marginRight: 4,
                                      whiteSpace: 'nowrap'
                                    }}
                                  >
                                    {r.replaceAll('_', ' ')}
                                  </span>
                                ))}
                              </td>
                              <td style={{ padding: '10px 12px', whiteSpace: 'nowrap' }}>
                                <span style={{
                                  fontSize: '0.75rem',
                                  fontWeight: 700,
                                  padding: '2px 8px',
                                  borderRadius: 6,
                                  background: u.membershipStatus === 'APPROVED' ? '#dcfce7' : isDeactivated ? '#fee2e2' : '#fef3c7',
                                  color: u.membershipStatus === 'APPROVED' ? '#15803d' : isDeactivated ? '#dc2626' : '#b45309'
                                }}>
                                  {u.membershipStatus}
                                </span>
                              </td>
                              <td style={{ padding: '10px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                                {!isAdmin && !isDeactivated && (
                                  <button
                                    type="button"
                                    onClick={() => handleDeactivateUser(u.id, u.email)}
                                    style={{
                                      background: 'none',
                                      border: '1px solid #fecaca',
                                      color: '#dc2626',
                                      padding: '4px 8px',
                                      borderRadius: 6,
                                      fontSize: '0.75rem',
                                      fontWeight: 600,
                                      cursor: 'pointer'
                                    }}
                                  >
                                    Deactivate
                                  </button>
                                )}
                              </td>
                            </tr>
                          )
                        })}
                    </tbody>
                  </table>
                </div>
              </section>
            </>
          )}
        </div>
      )}
    </div>
  )
}
