import { useState, useEffect, useCallback } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { formatAmount, formatSharedTime } from '../access/sharedFormatting.js'
import {
  CHARGE_TYPES,
  CHARGE_STATUSES,
  getChargeStatusBadge,
  getChargeTypeBadge,
} from '../access/billingWorkflow.js'

export default function Charges() {
  const { user } = useAuth()
  const isSysAdmin = user?.roles?.includes('SYSTEM_ADMIN')

  const [charges, setCharges] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  const [totalAmount, setTotalAmount] = useState(0)
  const pageSize = 15

  // Filters
  const [chargeType, setChargeType] = useState('')
  const [status, setStatus] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [institutionId, setInstitutionId] = useState('')
  const [institutions, setInstitutions] = useState([])

  // Load institutions if SYSTEM_ADMIN
  useEffect(() => {
    if (isSysAdmin) {
      api.get('/api/institutions')
        .then((res) => setInstitutions(Array.isArray(res.data) ? res.data : []))
        .catch(() => setInstitutions([]))
    }
  }, [isSysAdmin])

  const fetchCharges = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const params = {
        page,
        size: pageSize,
      }
      if (chargeType) params.chargeType = chargeType
      if (status) params.status = status
      if (fromDate) params.from = fromDate
      if (toDate) params.to = toDate
      if (institutionId) params.institutionId = institutionId

      const res = await api.get('/api/billing/charges', { params })
      const data = res.data
      setCharges(Array.isArray(data?.content) ? data.content : [])
      setTotalPages(data?.totalPages || 1)
      setTotalElements(data?.totalElements || 0)
      setTotalAmount(data?.totalAmount || 0)
    } catch (err) {
      console.error('Failed to load institutional charges', err)
      setError('Unable to load institutional charges. Please try again.')
    } finally {
      setLoading(false)
    }
  }, [page, chargeType, status, fromDate, toDate, institutionId])

  useEffect(() => {
    fetchCharges()
  }, [fetchCharges])

  const handleExportCSV = () => {
    if (!charges.length) return
    const headers = ['Charge ID', 'Equipment', 'Asset Tag', 'Booking ID', 'User ID', 'Type', 'Status', 'Duration (mins)', 'Hourly Rate', 'Amount', 'Created At']
    const rows = charges.map((c) => [
      c.id,
      `"${c.equipmentName || ''}"`,
      `"${c.assetTag || ''}"`,
      c.bookingId,
      c.userId,
      c.chargeType,
      c.status,
      c.usageMinutes,
      c.hourlyRateSnapshot,
      c.calculatedAmount,
      `"${c.createdAt || ''}"`,
    ])
    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map((e) => e.join(','))].join('\n')
    const encodedUri = encodeURI(csvContent)
    const link = document.createElement('a')
    link.setAttribute('href', encodedUri)
    link.setAttribute('download', `charges_export_${new Date().toISOString().slice(0, 10)}.csv`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  return (
    <div className="page-container" style={{ maxWidth: 1250, margin: '0 auto', padding: '24px 16px' }}>
      <header className="page-header" style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, margin: 0 }}>Institutional Charges</h1>
            <p className="muted-text" style={{ marginTop: 4, margin: 0, color: 'var(--text-muted, #64748b)' }}>
              Monitor internal, external, and unassigned equipment usage fees
            </p>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
            <button
              type="button"
              className="button-secondary"
              onClick={handleExportCSV}
              disabled={charges.length === 0}
              style={{ display: 'flex', alignItems: 'center', gap: 6 }}
            >
              📥 Export CSV
            </button>
            <div style={{
              background: 'var(--bg-card, #ffffff)',
              border: '1px solid var(--border-color, #e2e8f0)',
              borderRadius: 8,
              padding: '12px 20px',
              textAlign: 'right',
              boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
            }}>
              <small style={{ color: 'var(--text-muted, #64748b)', textTransform: 'uppercase', fontSize: '0.75rem', fontWeight: 600 }}>
                Total Filtered Charges
              </small>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--primary, #0284c7)' }}>
                {formatAmount(totalAmount)}
              </div>
            </div>
          </div>
        </div>
      </header>

      {/* Filter Toolbar */}
      <div style={{
        background: 'var(--bg-card, #ffffff)',
        border: '1px solid var(--border-color, #e2e8f0)',
        borderRadius: 8,
        padding: 16,
        marginBottom: 20,
        display: 'flex',
        flexWrap: 'wrap',
        gap: 12,
        alignItems: 'center'
      }}>
        {isSysAdmin && (
          <div style={{ minWidth: 180 }}>
            <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted, #64748b)' }}>
              Institution
            </label>
            <select
              value={institutionId}
              onChange={(e) => { setInstitutionId(e.target.value); setPage(0) }}
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

        <div>
          <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted, #64748b)' }}>
            Charge Type
          </label>
          <select
            value={chargeType}
            onChange={(e) => { setChargeType(e.target.value); setPage(0) }}
            className="input-select"
            style={{ minWidth: 140, padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
          >
            <option value="">All Types</option>
            {CHARGE_TYPES.map((type) => (
              <option key={type} value={type}>{type}</option>
            ))}
          </select>
        </div>

        <div>
          <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted, #64748b)' }}>
            Status
          </label>
          <select
            value={status}
            onChange={(e) => { setStatus(e.target.value); setPage(0) }}
            className="input-select"
            style={{ minWidth: 140, padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
          >
            <option value="">All Statuses</option>
            {CHARGE_STATUSES.map((st) => (
              <option key={st} value={st}>{st}</option>
            ))}
          </select>
        </div>

        <div>
          <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted, #64748b)' }}>
            From Date
          </label>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => { setFromDate(e.target.value); setPage(0) }}
            className="input-text"
            style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
          />
        </div>

        <div>
          <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted, #64748b)' }}>
            To Date
          </label>
          <input
            type="date"
            value={toDate}
            onChange={(e) => { setToDate(e.target.value); setPage(0) }}
            className="input-text"
            style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
          />
        </div>

        {(chargeType || status || fromDate || toDate || institutionId) && (
          <div style={{ alignSelf: 'flex-end', marginLeft: 'auto' }}>
            <button
              type="button"
              className="button-text"
              onClick={() => {
                setChargeType('')
                setStatus('')
                setFromDate('')
                setToDate('')
                setInstitutionId('')
                setPage(0)
              }}
              style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)', cursor: 'pointer' }}
            >
              Reset Filters
            </button>
          </div>
        )}
      </div>

      {error && (
        <div className="alert alert-danger" style={{ marginBottom: 20, padding: 12, borderRadius: 6, background: '#fee2e2', color: '#991b1b' }}>
          {error}
        </div>
      )}

      {loading ? (
        <div className="module-state" role="status" style={{ padding: 40, textAlign: 'center' }}>
          Loading institutional charges…
        </div>
      ) : charges.length === 0 ? (
        <div className="empty-state" style={{ padding: 48, textAlign: 'center', background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)' }}>
          <p style={{ fontSize: '1.1rem', fontWeight: 500, margin: 0 }}>No charges match the selected criteria.</p>
          <p className="muted-text" style={{ marginTop: 8, color: 'var(--text-muted, #64748b)' }}>
            Try adjusting your search filters or check back after bookings are completed.
          </p>
        </div>
      ) : (
        <div style={{ background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)', background: 'var(--table-header-bg, #f8fafc)' }}>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Charge #</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Equipment</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Booking & User</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Duration</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Rate</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Type</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Status</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right' }}>Amount</th>
                </tr>
              </thead>
              <tbody>
                {charges.map((charge) => {
                  const statusBadge = getChargeStatusBadge(charge.status)
                  const typeBadge = getChargeTypeBadge(charge.chargeType)
                  const durationHours = (Number(charge.usageMinutes || 0) / 60).toFixed(2)

                  return (
                    <tr key={charge.id} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                      <td style={{ padding: '12px 16px', fontSize: '0.85rem', color: 'var(--text-muted, #64748b)', fontWeight: 600 }}>
                        #{charge.id}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <div style={{ fontWeight: 600 }}>{charge.equipmentName || 'Equipment'}</div>
                        <small style={{ color: 'var(--text-muted, #64748b)' }}>Tag: {charge.assetTag || '—'}</small>
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.85rem' }}>
                        <div>Booking #{charge.bookingId}</div>
                        <small style={{ color: 'var(--text-muted, #64748b)' }}>
                          User #{charge.userId} {charge.departmentName ? `• ${charge.departmentName}` : ''}
                        </small>
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.85rem' }}>
                        {charge.usageMinutes}m ({durationHours}h)
                        <div style={{ fontSize: '0.75rem', color: 'var(--text-muted, #64748b)' }}>
                          {formatSharedTime(charge.createdAt)}
                        </div>
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.85rem' }}>
                        {charge.hourlyRateSnapshot != null ? `${formatAmount(charge.hourlyRateSnapshot)}/h` : '—'}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <span style={{
                          padding: '4px 8px',
                          borderRadius: 4,
                          fontSize: '0.75rem',
                          fontWeight: 600,
                          backgroundColor: typeBadge.bg,
                          color: typeBadge.color,
                        }}>
                          {typeBadge.label}
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px' }}>
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
                        {charge.invoiceNumber && (
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted, #64748b)', marginTop: 2 }}>
                            Inv: {charge.invoiceNumber}
                          </div>
                        )}
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: 700, fontSize: '0.95rem', color: 'var(--text-primary, #0f172a)' }}>
                        {formatAmount(charge.calculatedAmount)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', borderTop: '1px solid var(--border-color, #e2e8f0)' }}>
              <button
                type="button"
                className="button-secondary"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </button>
              <span style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
                Page {page + 1} of {totalPages} ({totalElements} charges)
              </span>
              <button
                type="button"
                className="button-secondary"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
