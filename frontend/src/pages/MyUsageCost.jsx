import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'
import { formatAmount, formatSharedTime } from '../access/sharedFormatting.js'
import { getChargeStatusBadge, getChargeTypeBadge } from '../access/billingWorkflow.js'

export default function MyUsageCost() {
  const [charges, setCharges] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  const [totalAmount, setTotalAmount] = useState(0)
  const pageSize = 15

  const fetchCharges = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const res = await api.get('/api/billing/charges/my', {
        params: { page, size: pageSize },
      })
      const data = res.data
      setCharges(Array.isArray(data?.content) ? data.content : [])
      setTotalPages(data?.totalPages || 1)
      setTotalElements(data?.totalElements || 0)
      setTotalAmount(data?.totalAmount || 0)
    } catch (err) {
      console.error('Failed to load personal usage charges', err)
      setError('Unable to load your usage costs. Please try again.')
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    fetchCharges()
  }, [fetchCharges])

  return (
    <div className="page-container" style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 16px' }}>
      <header className="page-header" style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, margin: 0 }}>My Usage Costs</h1>
            <p className="muted-text" style={{ marginTop: 4, margin: 0, color: 'var(--text-muted, #64748b)' }}>
              Itemized charges for your completed equipment bookings
            </p>
          </div>
          <div style={{
            background: 'var(--bg-card, #ffffff)',
            border: '1px solid var(--border-color, #e2e8f0)',
            borderRadius: 8,
            padding: '12px 20px',
            textAlign: 'right',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)'
          }}>
            <small style={{ color: 'var(--text-muted, #64748b)', textTransform: 'uppercase', fontSize: '0.75rem', fontWeight: 600 }}>
              Total Accrued Charges
            </small>
            <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--primary, #0284c7)' }}>
              {formatAmount(totalAmount)}
            </div>
          </div>
        </div>
      </header>

      {error && (
        <div className="alert alert-danger" style={{ marginBottom: 20, padding: 12, borderRadius: 6, background: '#fee2e2', color: '#991b1b' }}>
          {error}
        </div>
      )}

      {loading ? (
        <div className="module-state" role="status" style={{ padding: 40, textAlign: 'center' }}>
          Loading usage costs…
        </div>
      ) : charges.length === 0 ? (
        <div className="empty-state" style={{ padding: 48, textAlign: 'center', background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)' }}>
          <p style={{ fontSize: '1.1rem', fontWeight: 500, margin: 0 }}>No usage charges recorded.</p>
          <p className="muted-text" style={{ marginTop: 8, color: 'var(--text-muted, #64748b)' }}>
            When you complete an equipment booking, usage fees will be automatically calculated and displayed here.
          </p>
          <Link to="/bookings" className="button-primary" style={{ display: 'inline-block', marginTop: 16 }}>
            View Bookings
          </Link>
        </div>
      ) : (
        <div style={{ background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)', background: 'var(--table-header-bg, #f8fafc)' }}>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Equipment</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Date / Window</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Duration</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Hourly Rate</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Type</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Status</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right' }}>Calculated Amount</th>
                </tr>
              </thead>
              <tbody>
                {charges.map((charge) => {
                  const statusBadge = getChargeStatusBadge(charge.status)
                  const typeBadge = getChargeTypeBadge(charge.chargeType)
                  const durationHours = (Number(charge.usageMinutes || 0) / 60).toFixed(2)

                  return (
                    <tr key={charge.id} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                      <td style={{ padding: '12px 16px' }}>
                        <div style={{ fontWeight: 600 }}>{charge.equipmentName || 'Equipment'}</div>
                        <small style={{ color: 'var(--text-muted, #64748b)' }}>Tag: {charge.assetTag || '—'}</small>
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.9rem' }}>
                        <div>{formatSharedTime(charge.createdAt)}</div>
                        <small style={{ color: 'var(--text-muted, #64748b)' }}>Booking #{charge.bookingId}</small>
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.9rem' }}>
                        {charge.usageMinutes} mins ({durationHours} h)
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.9rem' }}>
                        {charge.hourlyRateSnapshot != null ? `${formatAmount(charge.hourlyRateSnapshot)} / hr` : '—'}
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
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: 700, fontSize: '1rem', color: 'var(--text-primary, #0f172a)' }}>
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
