export default function PeakUsageChart({ peakData, loading }) {
  if (loading) {
    return (
      <div className="app-surface" style={{ padding: '24px', textAlign: 'center' }}>
        <span className="utilization-spinner" />
        <p className="muted-text" style={{ marginTop: '12px' }}>Analyzing peak usage patterns…</p>
      </div>
    )
  }

  if (!peakData || !peakData.timeSlots?.length) {
    return (
      <div className="app-surface" style={{ padding: '24px', textAlign: 'center' }}>
        <p className="muted-text">No peak concurrency data available for this range.</p>
      </div>
    )
  }

  const maxPeak = Math.max(1, peakData.maxPeakConcurrency || 1)

  return (
    <div className="app-surface" style={{ padding: '24px' }}>
      <div className="utilization-panel-heading">
        <div>
          <h2>Peak Concurrency & Usage Distribution</h2>
          <p className="muted-text">Hourly concurrent usage levels and capacity saturation analysis.</p>
        </div>
      </div>

      {/* KPI highlight row */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '14px', marginBottom: '20px' }}>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Busiest Day</span>
          <h3 style={{ margin: '4px 0 0', color: 'var(--primary)' }}>{peakData.busiestDayOfWeek || 'None'}</h3>
        </div>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Peak Hour</span>
          <h3 style={{ margin: '4px 0 0', color: 'var(--text)' }}>{String(peakData.busiestHourOfDay ?? 0).padStart(2, '0')}:00</h3>
        </div>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Max Concurrency</span>
          <h3 style={{ margin: '4px 0 0', color: 'var(--text)' }}>{peakData.maxPeakConcurrency ?? 0} units</h3>
        </div>
        <div style={{ padding: '12px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px', border: '1px solid var(--border)' }}>
          <span className="muted-text" style={{ fontSize: '0.78rem' }}>Capacity Saturation</span>
          <h3 style={{ margin: '4px 0 0', color: peakData.capacitySaturationRate > 80 ? 'var(--danger, #f43f5e)' : 'var(--text)' }}>
            {peakData.capacitySaturationRate ?? 0}%
          </h3>
        </div>
      </div>

      {/* Bar Chart 24 Hours */}
      <div style={{ overflowX: 'auto', paddingBottom: '8px' }}>
        <div style={{ minWidth: '600px', height: '140px', display: 'flex', alignItems: 'flex-end', gap: '6px', paddingTop: '10px' }}>
          {peakData.timeSlots.map((slot) => {
            const heightPercent = Math.max(8, (slot.peakConcurrency / maxPeak) * 100)
            const isBusiest = slot.hour === peakData.busiestHourOfDay
            return (
              <div
                key={slot.hour}
                style={{
                  flex: 1,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  height: '100%',
                  justifyContent: 'flex-end',
                }}
                title={`${slot.hourLabel}: ${slot.peakConcurrency} peak concurrent units (${slot.totalUsageHours} hrs)`}
              >
                <span style={{ fontSize: '0.65rem', color: isBusiest ? 'var(--primary)' : 'var(--text-muted)', marginBottom: '3px' }}>
                  {slot.peakConcurrency > 0 ? slot.peakConcurrency : ''}
                </span>
                <div
                  style={{
                    width: '100%',
                    height: `${heightPercent}%`,
                    borderRadius: '4px 4px 0 0',
                    backgroundColor: isBusiest ? 'var(--primary, #38bdf8)' : 'rgba(56, 189, 248, 0.4)',
                    transition: 'height 0.3s ease',
                  }}
                />
                <span style={{ fontSize: '0.65rem', color: 'var(--text-muted)', marginTop: '4px' }}>
                  {slot.hour % 4 === 0 ? `${slot.hour}h` : ''}
                </span>
              </div>
            )
          })}
        </div>
      </div>

      {/* Top 5 Peak Hours */}
      {peakData.topFivePeakSlots && peakData.topFivePeakSlots.length > 0 && (
        <div style={{ marginTop: '16px', paddingTop: '12px', borderTop: '1px solid var(--border)' }}>
          <div style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--text-muted)', marginBottom: '8px' }}>
            Top 5 Peak Hours (ranked by usage volume, peak concurrency & tie-breaker):
          </div>
          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
            {peakData.topFivePeakSlots.map((slot, idx) => (
              <div
                key={slot.hour}
                style={{
                  padding: '4px 10px',
                  borderRadius: '6px',
                  background: idx === 0 ? 'rgba(56, 189, 248, 0.15)' : 'rgba(255, 255, 255, 0.03)',
                  border: idx === 0 ? '1px solid var(--primary)' : '1px solid var(--border)',
                  fontSize: '0.75rem',
                }}
              >
                <strong>#{idx + 1} {slot.hourLabel}</strong>: {slot.totalUsageHours}h ({slot.peakConcurrency} peak units)
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
