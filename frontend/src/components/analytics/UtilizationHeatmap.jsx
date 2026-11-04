import { useMemo, useState } from 'react'

const INTENSITY_COLORS = [
  'var(--surface, #1e293b)',       // 0: open unused (0%)
  'rgba(16, 185, 129, 0.35)',      // 1: 1-25%
  'rgba(16, 185, 129, 0.65)',      // 2: 26-50%
  'rgba(245, 158, 11, 0.80)',      // 3: 51-75%
  'rgba(239, 68, 68, 0.85)',       // 4: >75%
]

const CLOSED_COLOR = 'rgba(71, 85, 105, 0.25)'

const INTENSITY_LABELS = ['0% (Unused)', '1–25%', '26–50%', '51–75%', '>75%']

export default function UtilizationHeatmap({ heatmapData, loading }) {
  const [hoveredCell, setHoveredCell] = useState(null)

  const rows = useMemo(() => {
    if (!heatmapData?.matrix || heatmapData.matrix.length === 0) return []
    const byDay = {}
    for (let d = 1; d <= 7; d++) {
      byDay[d] = { dayOfWeek: d, dayName: '', hours: [] }
    }
    for (const cell of heatmapData.matrix) {
      if (byDay[cell.dayOfWeek]) {
        byDay[cell.dayOfWeek].dayName = cell.dayName
        byDay[cell.dayOfWeek].hours[cell.hour] = cell
      }
    }
    return Object.values(byDay)
  }, [heatmapData])

  if (loading) {
    return (
      <div className="analytics-heatmap-container app-surface" style={{ padding: '24px', textAlign: 'center' }}>
        <span className="utilization-spinner" />
        <p className="muted-text" style={{ marginTop: '12px' }}>Loading 7×24 utilization heatmap matrix…</p>
      </div>
    )
  }

  if (!rows.length) {
    return (
      <div className="analytics-heatmap-container app-surface" style={{ padding: '24px', textAlign: 'center' }}>
        <p className="muted-text">No heatmap data available for the selected period.</p>
      </div>
    )
  }

  return (
    <div className="analytics-heatmap-container app-surface" style={{ padding: '24px' }}>
      <div className="utilization-panel-heading">
        <div>
          <h2>7×24 Utilization Intensity Heatmap (168 Hourly Slots)</h2>
          <p className="muted-text">Matrix displays open capacity vs usage intensity. Closed slots return null and are grayed out.</p>
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', fontSize: '0.82rem' }}>
          <span className="muted-text">Average Rate (Open Hours):</span>
          <strong style={{ color: 'var(--primary, #38bdf8)' }}>{heatmapData?.averageUtilizationRate ?? 0}%</strong>
        </div>
      </div>

      <div style={{ overflowX: 'auto', paddingBottom: '8px' }}>
        <div style={{ minWidth: '760px' }}>
          {/* Hour Header */}
          <div style={{ display: 'grid', gridTemplateColumns: '60px repeat(24, 1fr)', gap: '3px', marginBottom: '4px' }}>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Day</span>
            {Array.from({ length: 24 }).map((_, h) => (
              <span key={h} style={{ fontSize: '0.68rem', textAlign: 'center', color: 'var(--text-muted)' }}>
                {h % 3 === 0 ? `${h}h` : ''}
              </span>
            ))}
          </div>

          {/* Day Rows */}
          {rows.map((row) => (
            <div
              key={row.dayOfWeek}
              style={{
                display: 'grid',
                gridTemplateColumns: '60px repeat(24, 1fr)',
                gap: '3px',
                marginBottom: '3px',
                alignItems: 'center',
              }}
            >
              <span style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text)' }}>
                {row.dayName || `Day ${row.dayOfWeek}`}
              </span>
              {row.hours.map((cell, h) => {
                const isClosed = cell ? cell.utilizationScore === null : false
                const intensity = cell && !isClosed ? Math.min(4, Math.max(0, cell.intensityLevel || 0)) : 0
                const isHovered = hoveredCell && hoveredCell.dayOfWeek === row.dayOfWeek && hoveredCell.hour === h
                const cellBg = isClosed ? CLOSED_COLOR : INTENSITY_COLORS[intensity]

                return (
                  <div
                    key={h}
                    onMouseEnter={() => setHoveredCell(cell || { dayOfWeek: row.dayOfWeek, dayName: row.dayName, hour: h, utilizationScore: null, usageMinutes: 0, bookingCount: 0 })}
                    onMouseLeave={() => setHoveredCell(null)}
                    style={{
                      aspectRatio: '1 / 1',
                      borderRadius: '4px',
                      backgroundColor: cellBg,
                      border: isHovered
                        ? '2px solid var(--primary, #38bdf8)'
                        : isClosed
                        ? '1px dashed rgba(255,255,255,0.08)'
                        : '1px solid rgba(255,255,255,0.06)',
                      cursor: 'pointer',
                      transition: 'transform 0.1s, border-color 0.1s',
                      transform: isHovered ? 'scale(1.15)' : 'none',
                    }}
                    title={
                      isClosed
                        ? `${row.dayName} ${String(h).padStart(2, '0')}:00 - Closed (Outside operating hours)`
                        : `${row.dayName} ${String(h).padStart(2, '0')}:00 - ${cell?.utilizationScore ?? 0}% (${cell?.usageMinutes ?? 0}m)`
                    }
                  />
                )
              })}
            </div>
          ))}
        </div>
      </div>

      {/* Tooltip detail bar */}
      <div style={{ marginTop: '12px', minHeight: '36px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '10px' }}>
        <div style={{ fontSize: '0.85rem' }}>
          {hoveredCell ? (
            hoveredCell.utilizationScore === null ? (
              <span>
                <strong>{hoveredCell.dayName || `Day ${hoveredCell.dayOfWeek}`} {String(hoveredCell.hour).padStart(2, '0')}:00</strong>: <span style={{ color: '#94a3b8' }}>Closed (Outside Operating Hours)</span>
              </span>
            ) : (
              <span>
                <strong>{hoveredCell.dayName || `Day ${hoveredCell.dayOfWeek}`} {String(hoveredCell.hour).padStart(2, '0')}:00</strong>: Utilization <strong>{hoveredCell.utilizationScore}%</strong> ({hoveredCell.usageMinutes} usage mins, {hoveredCell.bookingCount} bookings)
              </span>
            )
          ) : (
            <span className="muted-text">Hover over any hourly cell to inspect utilization details.</span>
          )}
        </div>

        {/* Legend */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '0.75rem', color: 'var(--text-muted)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
            <span style={{ display: 'inline-block', width: '12px', height: '12px', borderRadius: '2px', backgroundColor: CLOSED_COLOR, border: '1px dashed rgba(255,255,255,0.2)' }} />
            <span>Closed</span>
          </div>
          {INTENSITY_COLORS.map((color, idx) => (
            <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: '3px' }}>
              <span style={{ display: 'inline-block', width: '12px', height: '12px', borderRadius: '2px', backgroundColor: color, border: '1px solid rgba(255,255,255,0.1)' }} />
              <span>{INTENSITY_LABELS[idx]}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
