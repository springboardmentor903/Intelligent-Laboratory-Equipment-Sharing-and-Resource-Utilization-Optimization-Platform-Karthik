export default function DemandAnalysisView({ demandData, loading }) {
  if (loading) {
    return (
      <div className="app-surface" style={{ padding: '24px', textAlign: 'center' }}>
        <span className="utilization-spinner" />
        <p className="muted-text" style={{ marginTop: '12px' }}>Analyzing booking demand…</p>
      </div>
    )
  }

  if (!demandData) {
    return (
      <div className="app-surface" style={{ padding: '24px', textAlign: 'center' }}>
        <p className="muted-text">No booking demand data available.</p>
      </div>
    )
  }

  const fulfilledPct = demandData.fulfillmentRate ?? 0
  const rejectedPct = demandData.rejectionRate ?? 0
  const noShowPct = demandData.noShowRate ?? 0
  const cancelledPct = demandData.cancellationRate ?? 0
  const isHighDemand = demandData.isHighDemand || (demandData.bookingRate != null && demandData.bookingRate >= 80.0)

  return (
    <div className="app-surface" style={{ padding: '24px' }}>
      <div className="utilization-panel-heading">
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
          <div>
            <h2>Booking Demand & Capacity Analysis</h2>
            <p className="muted-text">Fulfillment ratio, rejection patterns, and unmet demand analysis.</p>
          </div>
          {isHighDemand && (
            <span
              style={{
                padding: '4px 10px',
                borderRadius: '6px',
                fontSize: '0.75rem',
                fontWeight: 700,
                background: 'rgba(239, 68, 68, 0.15)',
                color: '#ef4444',
                border: '1px solid rgba(239, 68, 68, 0.3)',
              }}
            >
              🔥 HIGH-DEMAND RESOURCE (≥80% Booked)
            </span>
          )}
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '14px', marginBottom: '20px' }}>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Booking Rate</span>
          <h3 style={{ margin: '4px 0 0', color: isHighDemand ? '#ef4444' : 'var(--text)' }}>
            {demandData.bookingRate != null ? `${demandData.bookingRate}%` : '—'}
          </h3>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>of operating hours</span>
        </div>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Fulfillment Rate</span>
          <h3 style={{ margin: '4px 0 0', color: '#34d399' }}>{demandData.fulfillmentRate}%</h3>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{demandData.fulfilledBookings} fulfilled</span>
        </div>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Rejection Rate</span>
          <h3 style={{ margin: '4px 0 0', color: demandData.rejectionRate > 20 ? '#f87171' : 'var(--text)' }}>
            {demandData.rejectionRate}%
          </h3>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{demandData.rejectedBookings} rejected</span>
        </div>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Cancellation Rate</span>
          <h3 style={{ margin: '4px 0 0', color: cancelledPct > 15 ? '#fbbf24' : 'var(--text)' }}>
            {cancelledPct}%
          </h3>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Cancelled bookings</span>
        </div>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Unmet Demand</span>
          <h3 style={{ margin: '4px 0 0', color: demandData.unmetDemandHours > 0 ? '#fbbf24' : 'var(--text)' }}>
            {demandData.unmetDemandHours} hrs
          </h3>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Lost capacity</span>
        </div>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Peak Contention</span>
          <h3 style={{ margin: '4px 0 0', color: 'var(--primary)' }}>{demandData.peakContentionRatio}x</h3>
          <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Demand vs Units</span>
        </div>
      </div>

      {/* Stacked fulfillment bar */}
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.78rem', marginBottom: '6px' }}>
          <span>Demand Fulfillment Distribution</span>
          <span className="muted-text">{demandData.totalRequests} Total Requests</span>
        </div>
        <div style={{ display: 'flex', height: '14px', borderRadius: '7px', overflow: 'hidden', background: 'rgba(255,255,255,0.08)' }}>
          {fulfilledPct > 0 && <div style={{ width: `${fulfilledPct}%`, backgroundColor: '#10b981' }} title={`Fulfilled: ${fulfilledPct}%`} />}
          {rejectedPct > 0 && <div style={{ width: `${rejectedPct}%`, backgroundColor: '#ef4444' }} title={`Rejected: ${rejectedPct}%`} />}
          {noShowPct > 0 && <div style={{ width: `${noShowPct}%`, backgroundColor: '#f59e0b' }} title={`No-Show: ${noShowPct}%`} />}
          {cancelledPct > 0 && <div style={{ width: `${cancelledPct}%`, backgroundColor: '#64748b' }} title={`Cancelled: ${cancelledPct}%`} />}
        </div>
        <div style={{ display: 'flex', gap: '14px', marginTop: '8px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#10b981' }} /> Fulfilled ({fulfilledPct}%)
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#ef4444' }} /> Rejected ({rejectedPct}%)
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#f59e0b' }} /> No-Show ({noShowPct}%)
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
            <span style={{ width: '8px', height: '8px', borderRadius: '50%', backgroundColor: '#64748b' }} /> Cancelled ({cancelledPct}%)
          </span>
        </div>
      </div>
    </div>
  )
}
