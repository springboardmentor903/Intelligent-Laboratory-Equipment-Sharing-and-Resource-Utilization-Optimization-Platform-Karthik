export default function DepartmentUtilizationTable({ departments, loading }) {
  if (loading) {
    return (
      <div className="app-surface" style={{ padding: '24px', textAlign: 'center' }}>
        <span className="utilization-spinner" />
        <p className="muted-text" style={{ marginTop: '12px' }}>Loading department utilization breakdown…</p>
      </div>
    )
  }

  if (!departments || !departments.length) {
    return null
  }

  return (
    <div className="app-surface" style={{ padding: '24px' }}>
      <div className="utilization-panel-heading">
        <div>
          <h2>Department Utilization Aggregations</h2>
          <p className="muted-text">Aggregated utilization rate and capacity metrics by department.</p>
        </div>
      </div>

      <div className="utilization-table-wrapper">
        <table className="utilization-table">
          <thead>
            <tr>
              <th>Department</th>
              <th>Equipment Units</th>
              <th>Capacity Hours</th>
              <th>Usage Hours</th>
              <th>Exclusive / Shared</th>
              <th>Average Rate</th>
            </tr>
          </thead>
          <tbody>
            {departments.map((dept, idx) => {
              const rate = dept.averageUtilizationRate ?? 0
              return (
                <tr key={dept.departmentId ?? idx}>
                  <td><strong>{dept.departmentName}</strong></td>
                  <td>{dept.equipmentCount}</td>
                  <td>{dept.totalCapacityHours}h</td>
                  <td><strong>{dept.actualUsageHours}h</strong></td>
                  <td>{dept.exclusiveUsageHours}h / {dept.sharedUsageHours}h</td>
                  <td>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <div style={{ flex: 1, height: '6px', borderRadius: '3px', background: 'rgba(255,255,255,0.08)', minWidth: '60px' }}>
                        <div
                          style={{
                            height: '100%',
                            borderRadius: '3px',
                            width: `${Math.min(100, rate)}%`,
                            backgroundColor: rate > 75 ? '#f59e0b' : '#38bdf8',
                          }}
                        />
                      </div>
                      <span style={{ fontWeight: 600, minWidth: '42px' }}>{rate}%</span>
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
