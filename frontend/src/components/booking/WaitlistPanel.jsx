import React, { useState, useEffect, useCallback } from 'react'
import api from '../../api/axiosInstance'
import { BOOKING_REFRESH_EVENT } from '../../access/bookingWorkflow'

export default function WaitlistPanel({ equipmentList = [], onWaitlistConverted }) {
  const [waitlistEntries, setWaitlistEntries] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [processingId, setProcessingId] = useState(null)

  // Direct join waitlist form state
  const [showJoinForm, setShowJoinForm] = useState(false)
  const [joinForm, setJoinForm] = useState({
    equipmentId: '',
    startTime: '',
    endTime: '',
    notes: '',
  })
  const [submittingJoin, setSubmittingJoin] = useState(false)

  // Claim modal state
  const [claimingEntry, setClaimingEntry] = useState(null)
  const [claimPurpose, setClaimPurpose] = useState('')

  const loadWaitlist = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await api.get('/api/waitlist/my')
      setWaitlistEntries(Array.isArray(response.data) ? response.data : [])
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load your waitlist entries.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadWaitlist()
    // Timer interval to refresh countdowns every 30 seconds
    const interval = setInterval(() => {
      setWaitlistEntries((prev) => [...prev])
    }, 30000)
    return () => clearInterval(interval)
  }, [loadWaitlist])

  const getEquipmentName = (equipmentId) => {
    const item = equipmentList.find((e) => String(e.id) === String(equipmentId))
    return item ? `${item.name} (${item.assetTag || 'ID: ' + item.id})` : `Equipment #${equipmentId}`
  }

  const handleOpenClaimModal = (entry) => {
    setClaimingEntry(entry)
    setClaimPurpose(entry.notes ? `Waitlist Claim: ${entry.notes}` : '')
    setError('')
  }

  const handleConfirmClaim = async () => {
    if (!claimingEntry) return
    if (!claimPurpose || !claimPurpose.trim()) {
      setError('Purpose is required to claim the waitlist slot.')
      return
    }
    const entryId = claimingEntry.id
    setProcessingId(entryId)
    setError('')
    setSuccess('')
    try {
      const response = await api.post(`/api/waitlist/${entryId}/claim`, {
        purpose: claimPurpose.trim(),
      })
      setSuccess(`Waitlist slot claimed successfully! New booking #${response.data.id} created.`)
      setClaimingEntry(null)
      setClaimPurpose('')
      await loadWaitlist()
      onWaitlistConverted?.()
      window.dispatchEvent(new Event(BOOKING_REFRESH_EVENT))
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to claim offered waitlist slot.')
    } finally {
      setProcessingId(null)
    }
  }

  const handleDecline = async (entryId) => {
    if (!window.confirm('Are you sure you want to decline this offered slot? It will be immediately offered to the next researcher in line.')) {
      return
    }
    setProcessingId(entryId)
    setError('')
    setSuccess('')
    try {
      await api.post(`/api/waitlist/${entryId}/decline`)
      setSuccess('Waitlist offer declined. Slot released to next researcher in queue.')
      await loadWaitlist()
      window.dispatchEvent(new Event(BOOKING_REFRESH_EVENT))
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to decline waitlist offer.')
    } finally {
      setProcessingId(null)
    }
  }

  const handleCancel = async (entryId) => {
    if (!window.confirm('Are you sure you want to cancel this waitlist entry?')) return
    setProcessingId(entryId)
    setError('')
    setSuccess('')
    try {
      await api.delete(`/api/waitlist/${entryId}`)
      setSuccess('Waitlist entry cancelled.')
      await loadWaitlist()
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to cancel waitlist entry.')
    } finally {
      setProcessingId(null)
    }
  }

  const handleJoinSubmit = async (e) => {
    e.preventDefault()
    if (!joinForm.equipmentId || !joinForm.startTime || !joinForm.endTime) {
      setError('Please select equipment and start/end times.')
      return
    }
    setSubmittingJoin(true)
    setError('')
    setSuccess('')
    try {
      await api.post('/api/waitlist', {
        equipmentId: Number(joinForm.equipmentId),
        requestedStartTime: joinForm.startTime,
        requestedEndTime: joinForm.endTime,
        notes: joinForm.notes,
      })
      setSuccess('Successfully added to the equipment waitlist!')
      setShowJoinForm(false)
      setJoinForm({ equipmentId: '', startTime: '', endTime: '', notes: '' })
      await loadWaitlist()
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to join waitlist.')
    } finally {
      setSubmittingJoin(false)
    }
  }

  const formatDate = (val) => {
    if (!val) return '—'
    const d = new Date(val)
    return Number.isNaN(d.getTime()) ? val : d.toLocaleString()
  }

  const getHoldCountdown = (w) => {
    if (w.status !== 'OFFERED') return null
    const expiryTime = w.expiresAt ? new Date(w.expiresAt) : (w.notifiedAt ? new Date(new Date(w.notifiedAt).getTime() + 60 * 60 * 1000) : null)
    if (!expiryTime) return null

    const diffMs = expiryTime.getTime() - Date.now()
    if (diffMs <= 0) {
      return <span style={{ color: '#ef4444', fontWeight: 700, fontSize: '11px' }}>⚠️ Hold Expired</span>
    }
    const mins = Math.floor(diffMs / (1000 * 60))
    const secs = Math.floor((diffMs % (1000 * 60)) / 1000)
    return (
      <div style={{ marginTop: '4px', fontSize: '11px', color: '#047857', fontWeight: 600 }}>
        ⏱️ 60-Min Hold: <strong>{mins}m {secs}s remaining</strong>
      </div>
    )
  }

  const getStatusBadge = (w) => {
    const s = String(w.status || '').toUpperCase()
    if (s === 'OFFERED') {
      return (
        <div>
          <span style={{ background: '#10b981', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 700 }}>
            🎉 OFFERED — Ready to Claim
          </span>
          {getHoldCountdown(w)}
        </div>
      )
    }
    if (s === 'WAITING') {
      return (
        <span style={{ background: '#3b82f6', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 700 }}>
          ⏳ In Queue (#{w.priority || 1})
        </span>
      )
    }
    if (s === 'CONVERTED') {
      return (
        <span style={{ background: '#6b7280', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 700 }}>
          ✓ Converted to Booking
        </span>
      )
    }
    if (s === 'DECLINED') {
      return (
        <span style={{ background: '#9ca3af', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 700 }}>
          ✕ Declined
        </span>
      )
    }
    if (s === 'EXPIRED') {
      return (
        <span style={{ background: '#f87171', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 700 }}>
          ⌛ Expired
        </span>
      )
    }
    return (
      <span style={{ background: '#9ca3af', color: '#fff', padding: '4px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 700 }}>
        {s}
      </span>
    )
  }

  return (
    <div className="card" style={{ padding: '24px', marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <div>
          <h3 style={{ margin: 0, fontSize: '18px', fontWeight: 700 }}>
            📋 My Equipment Waitlist ({waitlistEntries.length})
          </h3>
          <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: 'var(--text-muted)' }}>
            Track your queued positions. When high-demand equipment becomes available, slots are held for 60 minutes for top queued users.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setShowJoinForm(!showJoinForm)}
            style={{ fontSize: '13px' }}
          >
            {showJoinForm ? '✕ Close Form' : '+ Join Waitlist'}
          </button>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={loadWaitlist}
            disabled={loading}
            style={{ fontSize: '13px' }}
          >
            {loading ? 'Refreshing...' : '↻ Refresh'}
          </button>
        </div>
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

      {/* Claim Confirmation Modal */}
      {claimingEntry && (
        <div
          style={{
            marginBottom: '20px',
            padding: '16px',
            borderRadius: '8px',
            border: '2px solid #10b981',
            background: 'var(--surface, #f0fdf4)',
          }}
        >
          <h4 style={{ margin: '0 0 8px 0', color: '#065f46', fontSize: '16px' }}>
            🎉 Claim Offered Reservation: {getEquipmentName(claimingEntry.equipmentId)}
          </h4>
          <p style={{ margin: '0 0 10px 0', fontSize: '13px', color: 'var(--text)' }}>
            Window: <strong>{formatDate(claimingEntry.requestedStartTime)}</strong> to <strong>{formatDate(claimingEntry.requestedEndTime)}</strong>.
            Please specify your booking purpose to finalize the reservation:
          </p>
          <div style={{ marginBottom: '12px' }}>
            <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, marginBottom: '4px' }}>
              Booking Purpose (Mandatory):
            </label>
            <input
              type="text"
              className="input-field"
              value={claimPurpose}
              onChange={(e) => setClaimPurpose(e.target.value)}
              placeholder="e.g., Crystallography experiment run #3"
              style={{ width: '100%' }}
              required
            />
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="button"
              className="btn btn-primary"
              style={{ background: '#10b981', borderColor: '#10b981', color: '#fff', padding: '8px 18px', fontSize: '13px' }}
              onClick={handleConfirmClaim}
              disabled={processingId != null || !claimPurpose.trim()}
            >
              {processingId ? 'Creating Booking...' : '✓ Confirm & Create Booking'}
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              style={{ padding: '8px 14px', fontSize: '13px' }}
              onClick={() => setClaimingEntry(null)}
              disabled={processingId != null}
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Manual Join Form */}
      {showJoinForm && (
        <form
          onSubmit={handleJoinSubmit}
          style={{
            marginBottom: '20px',
            padding: '16px',
            borderRadius: '8px',
            border: '1px solid var(--border)',
            background: 'var(--surface-secondary, #fafafa)',
          }}
        >
          <h4 style={{ margin: '0 0 12px 0', fontSize: '15px' }}>Join Equipment Waitlist</h4>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '12px', marginBottom: '12px' }}>
            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, marginBottom: '4px' }}>Equipment</label>
              <select
                className="input-field"
                value={joinForm.equipmentId}
                onChange={(e) => setJoinForm({ ...joinForm, equipmentId: e.target.value })}
                required
                style={{ width: '100%' }}
              >
                <option value="">Select equipment...</option>
                {equipmentList.map((eq) => (
                  <option key={eq.id} value={eq.id}>
                    {eq.name} ({eq.assetTag || 'ID: ' + eq.id})
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, marginBottom: '4px' }}>Desired Start Time</label>
              <input
                type="datetime-local"
                className="input-field"
                value={joinForm.startTime}
                onChange={(e) => setJoinForm({ ...joinForm, startTime: e.target.value })}
                required
                style={{ width: '100%' }}
              />
            </div>
            <div>
              <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, marginBottom: '4px' }}>Desired End Time</label>
              <input
                type="datetime-local"
                className="input-field"
                value={joinForm.endTime}
                onChange={(e) => setJoinForm({ ...joinForm, endTime: e.target.value })}
                required
                style={{ width: '100%' }}
              />
            </div>
          </div>
          <div style={{ marginBottom: '12px' }}>
            <label style={{ display: 'block', fontSize: '12px', fontWeight: 600, marginBottom: '4px' }}>Notes / Project Reason</label>
            <input
              type="text"
              className="input-field"
              value={joinForm.notes}
              onChange={(e) => setJoinForm({ ...joinForm, notes: e.target.value })}
              placeholder="e.g. Critical project deadline, flexible within ±1 hour"
              style={{ width: '100%' }}
            />
          </div>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={submittingJoin}
            style={{ padding: '8px 18px', fontSize: '13px' }}
          >
            {submittingJoin ? 'Submitting...' : 'Confirm Join Waitlist'}
          </button>
        </form>
      )}

      {loading ? (
        <p style={{ fontStyle: 'italic', color: 'var(--text-muted)' }}>Loading your waitlist entries...</p>
      ) : waitlistEntries.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '36px 16px', color: 'var(--text-muted)' }}>
          <div style={{ fontSize: '32px', marginBottom: '8px' }}>📋</div>
          <div style={{ fontWeight: 600 }}>No active waitlist entries.</div>
          <div style={{ fontSize: '13px', marginTop: '4px' }}>
            When equipment is busy, you can join the queue to be automatically offered slots when available.
          </div>
        </div>
      ) : (
        <div className="table-responsive">
          <table className="booking-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border)', textAlign: 'left' }}>
                <th style={{ padding: '10px 12px' }}>Queue Pos</th>
                <th style={{ padding: '10px 12px' }}>Equipment</th>
                <th style={{ padding: '10px 12px' }}>Desired Window</th>
                <th style={{ padding: '10px 12px' }}>Status</th>
                <th style={{ padding: '10px 12px' }}>Notes</th>
                <th style={{ padding: '10px 12px' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {waitlistEntries.map((w) => (
                <tr
                  key={w.id}
                  style={{
                    borderBottom: '1px solid var(--border)',
                    background: w.status === 'OFFERED' ? 'rgba(16, 185, 129, 0.08)' : 'transparent',
                  }}
                >
                  <td style={{ padding: '10px 12px', fontWeight: 700, fontSize: '15px' }}>
                    #{w.priority || 1}
                  </td>
                  <td style={{ padding: '10px 12px' }}>{getEquipmentName(w.equipmentId)}</td>
                  <td style={{ padding: '10px 12px', fontSize: '13px' }}>
                    <div><strong>From:</strong> {formatDate(w.requestedStartTime)}</div>
                    <div><strong>To:</strong> {formatDate(w.requestedEndTime)}</div>
                  </td>
                  <td style={{ padding: '10px 12px' }}>{getStatusBadge(w)}</td>
                  <td style={{ padding: '10px 12px', fontSize: '13px', maxWidth: '180px' }}>
                    {w.notes || '—'}
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                      {w.status === 'OFFERED' && (
                        <>
                          <button
                            type="button"
                            className="btn btn-primary"
                            style={{
                              padding: '6px 14px',
                              fontSize: '12px',
                              background: '#10b981',
                              borderColor: '#10b981',
                              color: '#fff',
                              fontWeight: 700,
                            }}
                            disabled={processingId === w.id}
                            onClick={() => handleOpenClaimModal(w)}
                          >
                            {processingId === w.id ? 'Claiming...' : '🎉 Claim Slot'}
                          </button>
                          <button
                            type="button"
                            className="btn btn-secondary"
                            style={{ padding: '6px 12px', fontSize: '12px', color: '#ef4444' }}
                            disabled={processingId === w.id}
                            onClick={() => handleDecline(w.id)}
                          >
                            Decline
                          </button>
                        </>
                      )}
                      {['WAITING', 'OFFERED'].includes(w.status) && (
                        <button
                          type="button"
                          className="btn btn-secondary"
                          style={{ padding: '6px 12px', fontSize: '12px' }}
                          disabled={processingId === w.id}
                          onClick={() => handleCancel(w.id)}
                        >
                          Cancel
                        </button>
                      )}
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
