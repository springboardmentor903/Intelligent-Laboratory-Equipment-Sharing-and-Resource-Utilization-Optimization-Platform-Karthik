import React from 'react'

export default function SchedulingRecommendations({
  recommendations,
  loading,
  onSelectTimeSlot,
  onSelectEquipment,
  onJoinWaitlist,
  onClose,
}) {
  if (loading) {
    return (
      <div className="card" style={{ marginTop: '16px', padding: '16px', borderLeft: '4px solid var(--primary)' }}>
        <p style={{ margin: 0, fontStyle: 'italic' }}>Searching for available alternative time slots and equipment...</p>
      </div>
    )
  }

  if (!recommendations) return null

  const {
    requestedEquipmentName,
    isAvailableAtRequestedTime,
    alternativeTimeSlots = [],
    alternativeEquipment = [],
    quietTimeSlots = [],
  } = recommendations

  if (isAvailableAtRequestedTime) {
    return (
      <div className="card" style={{ marginTop: '16px', padding: '16px', borderLeft: '4px solid #10b981' }}>
        <h4 style={{ margin: '0 0 8px 0', color: '#10b981' }}>✓ Slot is Available</h4>
        <p style={{ margin: 0 }}>This equipment is ready for booking at the requested time.</p>
      </div>
    )
  }

  const formatSlot = (isoStr) => {
    if (!isoStr) return '—'
    const d = new Date(isoStr)
    return Number.isNaN(d.getTime()) ? isoStr : d.toLocaleString()
  }

  return (
    <div
      className="card"
      style={{
        marginTop: '16px',
        padding: '20px',
        borderLeft: '4px solid #f59e0b',
        background: 'var(--surface-secondary, #fafafa)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h4 style={{ margin: '0 0 6px 0', color: '#d97706', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span>⚠</span> Requested Slot is Fully Booked
          </h4>
          <p style={{ margin: '0 0 16px 0', fontSize: '14px', color: 'var(--text-muted)' }}>
            <strong>{requestedEquipmentName || 'Equipment'}</strong> has no available capacity for the selected time window.
            Consider these AI-optimized alternative options:
          </p>
        </div>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            className="btn btn-secondary"
            style={{ padding: '4px 10px', fontSize: '12px' }}
          >
            ✕ Dismiss
          </button>
        )}
      </div>

      {/* Alternative Time Slots */}
      <div style={{ marginBottom: '16px' }}>
        <h5 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 700 }}>
          🕒 Alternative Available Time Slots on Same Equipment:
        </h5>
        {alternativeTimeSlots.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>
            No adjacent time slots found on the same day or following days.
          </p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {alternativeTimeSlots.map((slot, idx) => (
              <div
                key={idx}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  background: 'var(--surface, #fff)',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  border: '1px solid var(--border)',
                }}
              >
                <div>
                  <div style={{ fontWeight: 600, fontSize: '13px' }}>
                    {formatSlot(slot.startTime)} → {formatSlot(slot.endTime)}
                  </div>
                  {slot.reason && (
                    <div style={{ fontSize: '12px', color: '#10b981', marginTop: '2px' }}>
                      ✓ {slot.reason}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  className="btn btn-primary"
                  style={{ padding: '6px 14px', fontSize: '12px' }}
                  onClick={() => onSelectTimeSlot(slot.startTime, slot.endTime)}
                >
                  Use This Slot
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Alternative Equipment in Same Category */}
      <div style={{ marginBottom: '16px' }}>
        <h5 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 700 }}>
          🔬 Alternative Equipment in Same Category:
        </h5>
        {alternativeEquipment.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>
            No alternative equipment found in this category.
          </p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {alternativeEquipment.map((alt) => (
              <div
                key={alt.equipmentId}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  background: 'var(--surface, #fff)',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  border: '1px solid var(--border)',
                }}
              >
                <div>
                  <div style={{ fontWeight: 600, fontSize: '13px' }}>{alt.equipmentName}</div>
                  <div style={{ fontSize: '12px', color: 'var(--text-muted)' }}>
                    Tag: {alt.assetTag || 'N/A'} • Category: {alt.category || 'General'}
                  </div>
                </div>
                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ padding: '6px 14px', fontSize: '12px' }}
                  onClick={() => onSelectEquipment(alt.equipmentId)}
                >
                  Switch Equipment
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Rule 3: Quiet / Off-Peak Recommended Slots Ranked by Historical Heatmap Utilization */}
      <div style={{ marginBottom: '16px' }}>
        <h5 style={{ margin: '0 0 8px 0', fontSize: '14px', fontWeight: 700 }}>
          🌿 Quiet / Off-Peak Slots (Ranked by Historical Low Heatmap Utilization):
        </h5>
        {quietTimeSlots.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', margin: 0 }}>
            No quiet off-peak slots identified in upcoming operating windows.
          </p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {quietTimeSlots.map((slot, idx) => (
              <div
                key={`quiet-${idx}`}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  background: 'var(--surface, #fff)',
                  padding: '10px 14px',
                  borderRadius: '8px',
                  border: '1px solid #10b981',
                }}
              >
                <div>
                  <div style={{ fontWeight: 600, fontSize: '13px' }}>
                    {formatSlot(slot.startTime)} → {formatSlot(slot.endTime)}
                  </div>
                  {slot.reason && (
                    <div style={{ fontSize: '12px', color: '#059669', marginTop: '2px' }}>
                      🌿 {slot.reason}
                    </div>
                  )}
                </div>
                <button
                  type="button"
                  className="btn btn-primary"
                  style={{ padding: '6px 14px', fontSize: '12px' }}
                  onClick={() => onSelectTimeSlot(slot.startTime, slot.endTime)}
                >
                  Use This Quiet Slot
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Waitlist option */}
      {onJoinWaitlist && (
        <div
          style={{
            borderTop: '1px solid var(--border)',
            paddingTop: '12px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
            Can't change your time or equipment?
          </span>
          <button
            type="button"
            className="btn btn-warning"
            style={{
              padding: '6px 16px',
              fontSize: '13px',
              fontWeight: 600,
              background: '#f59e0b',
              color: '#fff',
              border: 'none',
              borderRadius: '6px',
            }}
            onClick={onJoinWaitlist}
          >
            📋 Join Waitlist for This Slot
          </button>
        </div>
      )}
    </div>
  )
}
