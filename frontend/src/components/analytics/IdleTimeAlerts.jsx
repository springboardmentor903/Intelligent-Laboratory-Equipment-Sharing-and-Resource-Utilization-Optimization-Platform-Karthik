export default function IdleTimeAlerts({ idleReports, loading }) {
  if (loading) {
    return (
      <div className="app-surface" style={{ padding: '24px', textAlign: 'center' }}>
        <span className="utilization-spinner" />
        <p className="muted-text" style={{ marginTop: '12px' }}>Evaluating equipment idle time…</p>
      </div>
    )
  }

  if (!idleReports || !idleReports.length) {
    return (
      <div className="app-surface" style={{ padding: '24px', textAlign: 'center' }}>
        <p className="muted-text">No equipment idle reports available.</p>
      </div>
    )
  }

  const alertItems = idleReports.filter((r) => r.idleAlert)

  return (
    <div className="app-surface" style={{ padding: '24px' }}>
      <div className="utilization-panel-heading">
        <div>
          <h2>Idle Time Detection & Alerts</h2>
          <p className="muted-text">
            Evaluated over the last 7 completed days ending yesterday in platform timezone ({alertItems.length} active alert{alertItems.length === 1 ? '' : 's'}).
          </p>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {idleReports.map((report) => {
          const isCritical = report.alertLevel === 'CRITICAL'
          const isWarning = report.alertLevel === 'WARNING'
          const badgeBg = isCritical ? 'rgba(239, 68, 68, 0.15)' : isWarning ? 'rgba(245, 158, 11, 0.15)' : 'rgba(16, 185, 129, 0.15)'
          const badgeColor = isCritical ? '#f87171' : isWarning ? '#fbbf24' : '#34d399'

          return (
            <div
              key={report.equipmentId}
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '12px 16px',
                borderRadius: '8px',
                background: 'rgba(255,255,255,0.02)',
                border: '1px solid var(--border)',
                flexWrap: 'wrap',
                gap: '10px',
              }}
            >
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <strong style={{ color: 'var(--text)' }}>{report.equipmentName}</strong>
                  {report.assetTag && (
                    <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>({report.assetTag})</span>
                  )}
                  <span
                    style={{
                      padding: '2px 8px',
                      borderRadius: '4px',
                      fontSize: '0.72rem',
                      fontWeight: 600,
                      backgroundColor: badgeBg,
                      color: badgeColor,
                    }}
                  >
                    {report.alertLevel}
                  </span>
                </div>
                <p style={{ margin: '4px 0 0', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  {report.alertMessage}
                </p>
              </div>

              <div style={{ display: 'flex', alignItems: 'center', gap: '16px', fontSize: '0.82rem' }}>
                <div>
                  <span className="muted-text">Idle: </span>
                  <strong>{report.idleHours} hrs</strong> ({report.idlePercentage}%)
                </div>
                <div>
                  <span className="muted-text">Used: </span>
                  <span>{report.actualUsageHours} hrs</span>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
