import { useState, useEffect, useCallback } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { formatAmount } from '../access/sharedFormatting.js'
import {
  canManageBudgets,
  getBudgetStatusBadge,
  getFiscalYearOptions,
  validateBudgetForm,
} from '../access/billingWorkflow.js'

export default function Budgets() {
  const { user } = useAuth()
  const roles = user?.roles || []
  const canEdit = canManageBudgets(roles)
  const isSysAdmin = roles.includes('SYSTEM_ADMIN')

  const currentYear = new Date().getFullYear()
  const [fiscalYear, setFiscalYear] = useState(currentYear)
  const [institutionId, setInstitutionId] = useState('')
  const [institutions, setInstitutions] = useState([])
  const [departments, setDepartments] = useState([])

  const [budgets, setBudgets] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // Set Budget Modal State
  const [modalOpen, setModalOpen] = useState(false)
  const [modalDeptId, setModalDeptId] = useState('')
  const [modalYear, setModalYear] = useState(currentYear)
  const [modalAmount, setModalAmount] = useState('')
  const [modalSubmitting, setModalSubmitting] = useState(false)
  const [formErrors, setFormErrors] = useState({})

  const yearOptions = getFiscalYearOptions()

  // Load institutions if SYSTEM_ADMIN
  useEffect(() => {
    if (isSysAdmin) {
      api.get('/api/institutions')
        .then((res) => setInstitutions(Array.isArray(res.data) ? res.data : []))
        .catch(() => setInstitutions([]))
    }
  }, [isSysAdmin])

  // Load departments
  useEffect(() => {
    api.get('/api/departments')
      .then((res) => setDepartments(Array.isArray(res.data) ? res.data : []))
      .catch(() => setDepartments([]))
  }, [])

  const fetchBudgets = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const params = { fiscalYear }
      if (institutionId) params.institutionId = institutionId

      const res = await api.get('/api/billing/budgets', { params })
      setBudgets(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      console.error('Failed to load department budgets', err)
      setError('Unable to load department budgets.')
    } finally {
      setLoading(false)
    }
  }, [fiscalYear, institutionId])

  useEffect(() => {
    fetchBudgets()
  }, [fetchBudgets])

  const handleOpenModal = (deptId = '', existingAmount = '') => {
    setModalDeptId(deptId ? String(deptId) : (departments[0]?.id ? String(departments[0].id) : ''))
    setModalYear(fiscalYear)
    setModalAmount(existingAmount ? String(existingAmount) : '')
    setFormErrors({})
    setModalOpen(true)
  }

  const handleSaveBudget = async (e) => {
    e.preventDefault()
    const { isValid, errors } = validateBudgetForm({
      departmentId: modalDeptId,
      fiscalYear: modalYear,
      budgetAmount: modalAmount,
    })

    if (!isValid) {
      setFormErrors(errors)
      return
    }

    setModalSubmitting(true)
    setError('')
    try {
      await api.post('/api/billing/budgets', {
        departmentId: Number(modalDeptId),
        fiscalYear: Number(modalYear),
        budgetAmount: Number(modalAmount),
      })
      setNotice('Department budget saved successfully.')
      setModalOpen(false)
      fetchBudgets()
      setTimeout(() => setNotice(''), 4000)
    } catch (err) {
      console.error('Failed to save budget', err)
      setError(err?.response?.data?.message || 'Failed to save department budget.')
    } finally {
      setModalSubmitting(false)
    }
  }

  return (
    <div className="page-container" style={{ maxWidth: 1250, margin: '0 auto', padding: '24px 16px' }}>
      <header className="page-header" style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, margin: 0 }}>Department Budgets</h1>
            <p className="muted-text" style={{ marginTop: 4, margin: 0, color: 'var(--text-muted, #64748b)' }}>
              Track fiscal spending allocations and manage department budget thresholds
            </p>
          </div>
          {canEdit && (
            <button
              type="button"
              className="button-primary"
              onClick={() => handleOpenModal()}
              style={{ display: 'flex', alignItems: 'center', gap: 8 }}
            >
              ➕ Set / Update Budget
            </button>
          )}
        </div>
      </header>

      {notice && (
        <div className="alert alert-success" style={{ marginBottom: 20, padding: 12, borderRadius: 6, background: '#dcfce7', color: '#166534' }}>
          {notice}
        </div>
      )}

      {error && (
        <div className="alert alert-danger" style={{ marginBottom: 20, padding: 12, borderRadius: 6, background: '#fee2e2', color: '#991b1b' }}>
          {error}
        </div>
      )}

      {/* Toolbar */}
      <div style={{
        background: 'var(--bg-card, #ffffff)',
        border: '1px solid var(--border-color, #e2e8f0)',
        borderRadius: 8,
        padding: 16,
        marginBottom: 20,
        display: 'flex',
        flexWrap: 'wrap',
        gap: 16,
        alignItems: 'center'
      }}>
        <div>
          <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted, #64748b)' }}>
            Fiscal Year
          </label>
          <select
            value={fiscalYear}
            onChange={(e) => setFiscalYear(Number(e.target.value))}
            className="input-select"
            style={{ minWidth: 140, padding: '6px 12px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)', fontWeight: 600 }}
          >
            {yearOptions.map((yr) => (
              <option key={yr} value={yr}>FY {yr}</option>
            ))}
          </select>
        </div>

        {isSysAdmin && (
          <div style={{ minWidth: 180 }}>
            <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted, #64748b)' }}>
              Institution
            </label>
            <select
              value={institutionId}
              onChange={(e) => setInstitutionId(e.target.value)}
              className="input-select"
              style={{ width: '100%', padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
            >
              <option value="">All My Institutions</option>
              {institutions.map((inst) => (
                <option key={inst.id} value={inst.id}>{inst.name}</option>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Budgets Table */}
      {loading ? (
        <div className="module-state" role="status" style={{ padding: 40, textAlign: 'center' }}>
          Loading department budgets…
        </div>
      ) : budgets.length === 0 ? (
        <div className="empty-state" style={{ padding: 40, textAlign: 'center', background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)' }}>
          <p className="muted-text">No departments found.</p>
        </div>
      ) : (
        <div style={{ background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)', background: 'var(--table-header-bg, #f8fafc)' }}>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', whiteSpace: 'nowrap' }}>Department</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', whiteSpace: 'nowrap' }}>Fiscal Year</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>Allocated Budget</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>Current Spend</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>Remaining</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', whiteSpace: 'nowrap' }}>Usage %</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', whiteSpace: 'nowrap' }}>Status</th>
                  {canEdit && (
                    <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>Action</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {budgets.map((b) => {
                  const statusBadge = getBudgetStatusBadge(b.status)
                  const usedPct = Number(b.usagePercentage || 0)
                  const hasBudget = b.budgetAmount != null

                  return (
                    <tr key={b.departmentId} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                      <td style={{ padding: '12px 16px', fontWeight: 600, whiteSpace: 'nowrap' }}>
                        {b.departmentName}
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.9rem', whiteSpace: 'nowrap' }}>
                        FY {b.fiscalYear}
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: 600, whiteSpace: 'nowrap' }}>
                        {hasBudget ? formatAmount(b.budgetAmount) : '—'}
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right', fontSize: '0.9rem', whiteSpace: 'nowrap' }}>
                        {formatAmount(b.currentSpend)}
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right', fontSize: '0.9rem', color: Number(b.remainingBudget || 0) < 0 ? '#dc2626' : 'inherit', fontWeight: Number(b.remainingBudget || 0) < 0 ? 600 : 'normal', whiteSpace: 'nowrap' }}>
                        {hasBudget ? formatAmount(b.remainingBudget) : '—'}
                      </td>
                      <td style={{ padding: '12px 16px', minWidth: 130 }}>
                        {hasBudget ? (
                          <div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.75rem', marginBottom: 4 }}>
                              <span>{usedPct.toFixed(1)}%</span>
                            </div>
                            <div style={{ width: '100%', height: 6, background: '#e2e8f0', borderRadius: 3, overflow: 'hidden' }}>
                              <div
                                style={{
                                  width: `${Math.min(100, usedPct)}%`,
                                  height: '100%',
                                  background: usedPct > 100 ? '#dc2626' : usedPct >= 80 ? '#d97706' : '#16a34a',
                                }}
                              />
                            </div>
                          </div>
                        ) : (
                          <span style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.85rem' }}>—</span>
                        )}
                      </td>
                      <td style={{ padding: '12px 16px', whiteSpace: 'nowrap' }}>
                        <span style={{
                          padding: '4px 8px',
                          borderRadius: 4,
                          fontSize: '0.75rem',
                          fontWeight: 600,
                          backgroundColor: statusBadge.bg,
                          color: statusBadge.color,
                        }}>
                          {statusBadge.label}
                        </span>
                      </td>
                      {canEdit && (
                        <td style={{ padding: '12px 16px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                          <button
                            type="button"
                            className="button-secondary"
                            onClick={() => handleOpenModal(b.departmentId, b.budgetAmount)}
                            style={{ padding: '4px 10px', fontSize: '0.8rem' }}
                          >
                            {hasBudget ? 'Edit' : 'Set'}
                          </button>
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Set Budget Modal */}
      {modalOpen && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000,
          padding: 16
        }}>
          <div style={{
            background: 'var(--bg-card, #ffffff)',
            borderRadius: 8,
            padding: 24,
            maxWidth: 450,
            width: '100%',
            maxHeight: '90vh',
            overflowY: 'auto',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
          }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700, margin: '0 0 16px 0' }}>
              Set Department Budget
            </h2>

            <form onSubmit={handleSaveBudget}>
              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 4 }}>
                  Department
                </label>
                <select
                  value={modalDeptId}
                  onChange={(e) => setModalDeptId(e.target.value)}
                  className="input-select"
                  style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
                >
                  {departments.map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
                {formErrors.departmentId && (
                  <small style={{ color: '#dc2626' }}>{formErrors.departmentId}</small>
                )}
              </div>

              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 4 }}>
                  Fiscal Year
                </label>
                <select
                  value={modalYear}
                  onChange={(e) => setModalYear(Number(e.target.value))}
                  className="input-select"
                  style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
                >
                  {yearOptions.map((yr) => (
                    <option key={yr} value={yr}>FY {yr}</option>
                  ))}
                </select>
                {formErrors.fiscalYear && (
                  <small style={{ color: '#dc2626' }}>{formErrors.fiscalYear}</small>
                )}
              </div>

              <div style={{ marginBottom: 20 }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 4 }}>
                  Allocated Budget Amount (INR)
                </label>
                <input
                  type="number"
                  step="0.01"
                  min="1"
                  placeholder="e.g. 500000"
                  value={modalAmount}
                  onChange={(e) => setModalAmount(e.target.value)}
                  className="input-text"
                  style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
                />
                {formErrors.budgetAmount && (
                  <small style={{ color: '#dc2626' }}>{formErrors.budgetAmount}</small>
                )}
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
                <button
                  type="button"
                  className="button-secondary"
                  onClick={() => setModalOpen(false)}
                  disabled={modalSubmitting}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="button-primary"
                  disabled={modalSubmitting}
                >
                  {modalSubmitting ? 'Saving…' : 'Save Budget'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
