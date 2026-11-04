import React, { useState, useEffect, useCallback } from 'react'
import api from '../../api/axiosInstance'
import { BOOKING_REFRESH_EVENT } from '../../access/bookingWorkflow'

export default function PendingApprovalsQueue({ onBookingProcessed, equipmentList = [] }) {
  const [pendingBookings, setPendingBookings] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [processingId, setProcessingId] = useState(null)
  const [rejectingBooking, setRejectingBooking] = useState(null)
  const [rejectReason, setRejectReason] = useState('')

  const loadPendingBookings = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await api.get('/api/bookings/pending')
      setPendingBookings(Array.isArray(response.data) ? response.data : [])
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load pending approval requests.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadPendingBookings()
  }, [loadPendingBookings])

  const getEquipmentName = (equipmentId) => {
    const item = equipmentList.find((e) => String(e.id) === String(equipmentId))
    return item ? `${item.name} (${item.assetTag || 'ID: ' + item.id})` : `Equipment #${equipmentId}`
  }

  const handleApprove = async (bookingId) => {
    setProcessingId(bookingId)
    setError('')
    setSuccess('')
    try {
      await api.post(`/api/bookings/${bookingId}/approve`)
      setSuccess(`Booking #${bookingId} approved successfully.`)
      await loadPendingBookings()
      onBookingProcessed?.()
      window.dispatchEvent(new Event(BOOKING_REFRESH_EVENT))
    } catch (err) {
      setError(err.response?.data?.message || `Failed to approve booking #${bookingId}.`)
    } finally {
      setProcessingId(null)
    }
  }

  const handleRejectConfirm = async () => {
    if (!rejectingBooking) return
    if (!rejectReason || !rejectReason.trim()) {
      setError('Rejection reason is mandatory.')
      return
    }
    const bookingId = rejectingBooking.id
    setProcessingId(bookingId)
    setError('')
    setSuccess('')
    try {
      await api.post(`/api/bookings/${bookingId}/reject`, { reason: rejectReason })
      setSuccess(`Booking #${bookingId} rejected.`)
      setRejectingBooking(null)
      setRejectReason('')
      await loadPendingBookings()
      onBookingProcessed?.()
      window.dispatchEvent(new Event(BOOKING_REFRESH_EVENT))
    } catch (err) {
      setError(err.response?.data?.message || `Failed to reject booking #${bookingId}.`)
    } finally {
      setProcessingId(null)
    }
  }

  const formatDate = (val) => {
    if (!val) return '—'
    const d = new Date(val)
    return Number.isNaN(d.getTime()) ? val : d.toLocaleString()
  }

  return (
    <div className="card" style={{ padding: '24px', marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 700 }}>
            ⏳ Pending Booking Approvals ({pendingBookings.length})
          </h3>
          <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: 'var(--text-muted)' }}>
            Review, approve, or reject booking requests submitted by lab researchers and students.
          </p>
        </div>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={loadPendingBookings}
          disabled={loading}
          style={{ fontSize: '13px' }}
        >
          {loading ? 'Refreshing...' : '↻ Refresh Queue'}
        </button>
      </div>

      {error && (
        <div className="alert alert-error" style={{ marginBottom: '16px', padding: '10px 14px', borderRadius: '6px', background: '#fee2e2', color: '#991b1b' }}>
          {error}
        </div>
      )}

      {success && (
        <div className="alert alert-success" style={{ marginBottom: '16px', padding: '10px 14px', borderRadius: '6px', background: '#d1fae5', color: '#065f46' }}>
          {success}
        </div>
      )}

      {/* Reject reason modal */}
      {rejectingBooking && (
        <div
          style={{
            marginBottom: '20px',
            padding: '16px',
            borderRadius: '8px',
            border: '1px solid #ef4444',
            background: '#fff1f2',
          }}
        >
          <h4 style={{ margin: '0 0 8px 0', color: '#b91c1c' }}>
            Reject Booking #{rejectingBooking.id}
          </h4>
          <p style={{ margin: '0 0 10px 0', fontSize: '13px' }}>
            Provide a mandatory rejection reason for the researcher:
          </p>
          <input
            type="text"
            className="input-field"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            placeholder="e.g., Scheduled maintenance during this slot, or insufficient training"
            style={{ width: '100%', marginBottom: '12px' }}
            required
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="button"
              className="btn btn-danger"
              style={{ background: '#ef4444', color: '#fff', border: 'none', padding: '6px 14px', borderRadius: '6px' }}
              onClick={handleRejectConfirm}
              disabled={processingId != null || !rejectReason.trim()}
            >
              {processingId ? 'Rejecting...' : 'Confirm Rejection'}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => { setRejectingBooking(null); setRejectReason(''); }}
              style={{ padding: '6px 14px' }}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <p style={{ fontStyle: 'italic', color: 'var(--text-muted)' }}>Loading pending approvals...</p>
      ) : pendingBookings.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '36px 16px', color: 'var(--text-muted)' }}>
          <div style={{ fontSize: '32px', marginBottom: '8px' }}>✓</div>
          <div style={{ fontWeight: 600 }}>All caught up! No pending approvals.</div>
          <div style={{ fontSize: '13px', marginTop: '4px' }}>All booking requests have been reviewed and decided.</div>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="booking-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                <th style={{ padding: '10px 12px' }}>ID</th>
                <th style={{ padding: '10px 12px' }}>Equipment</th>
                <th style={{ padding: '10px 12px' }}>Requester</th>
                <th style={{ padding: '10px 12px' }}>Time Slot</th>
                <th style={{ padding: '10px 12px' }}>Purpose</th>
                <th style={{ padding: '10px 12px' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {pendingBookings.map((b) => (
                <tr key={b.id} style={{ borderBottom: '1px solid var(--border)' }}>
                  <td style={{ padding: '10px 12px', fontWeight: 600 }}>#{b.id}</td>
                  <td style={{ padding: '10px 12px' }}>{getEquipmentName(b.equipmentId)}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ fontWeight: 600 }}>{b.userName || `User #${b.userId}`}</div>
                    <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{b.userEmail || ''}</div>
                  </td>
                  <td style={{ padding: '10px 12px', fontSize: '13px' }}>
                    <div><strong>From:</strong> {formatDate(b.startTime)}</div>
                    <div><strong>To:</strong> {formatDate(b.endTime)}</div>
                  </td>
                  <td style={{ padding: '10px 12px', fontSize: '13px', maxWidth: '200px' }}>
                    {b.purpose || '—'}
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <button
                        type="button"
                        className="btn btn-primary"
                        style={{ padding: '6px 12px', fontSize: '12px' }}
                        disabled={processingId === b.id}
                        onClick={() => handleApprove(b.id)}
                      >
                        {processingId === b.id ? 'Processing...' : '✓ Approve'}
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger"
                        style={{
                          padding: '6px 12px',
                          fontSize: '12px',
                          background: '#ef4444',
                          color: '#fff',
                          border: 'none',
                          borderRadius: '6px',
                        }}
                        disabled={processingId === b.id}
                        onClick={() => setRejectingBooking(b)}
                      >
                        ✕ Reject
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
