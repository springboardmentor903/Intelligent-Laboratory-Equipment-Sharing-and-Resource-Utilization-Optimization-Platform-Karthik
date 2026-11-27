import { useState, useEffect, useCallback } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { formatAmount } from '../access/sharedFormatting.js'

function CostBarChart({ items }) {
  if (!items || items.length === 0) {
    return <p className="muted-text" style={{ padding: 16 }}>No equipment cost records for chart display.</p>
  }

  // Find max cost among totalUsageCost and maintenanceCost
  const maxCost = Math.max(
    1,
    ...items.map((i) => Math.max(Number(i.totalUsageCost || 0), Number(i.maintenanceCost || 0)))
  )

  return (
    <div style={{ marginBottom: 24, padding: 16, background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h3 style={{ fontSize: '1rem', fontWeight: 600, margin: 0 }}>Usage Cost vs. Maintenance Cost Comparison</h3>
        <div style={{ display: 'flex', gap: 16, fontSize: '0.8rem' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 12, height: 12, background: '#0284c7', borderRadius: 2 }} />
            Usage Cost
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ width: 12, height: 12, background: '#ea580c', borderRadius: 2 }} />
            Maintenance Cost (Separated)
          </span>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 16, minHeight: 200, overflowX: 'auto', padding: '16px 8px 8px' }}>
        {items.map((item) => {
          const usageHeight = Math.max(2, (Number(item.totalUsageCost || 0) / maxCost) * 160)
          const maintHeight = Math.max(2, (Number(item.maintenanceCost || 0) / maxCost) * 160)

          return (
            <div
              key={item.equipmentId}
              title={`${item.equipmentName}: Usage ${formatAmount(item.totalUsageCost)}, Maintenance ${formatAmount(item.maintenanceCost)}`}
              style={{ minWidth: 60, flex: 1, textAlign: 'center', display: 'flex', flexDirection: 'column', alignItems: 'center' }}
            >
              <div style={{ height: 160, display: 'flex', alignItems: 'flex-end', justifyContent: 'center', gap: 4, width: '100%' }}>
                <div
                  style={{
                    width: '40%',
                    height: `${usageHeight}px`,
                    background: '#0284c7',
                    borderRadius: '4px 4px 0 0',
                    transition: 'height 0.3s ease',
                  }}
                />
                <div
                  style={{
                    width: '40%',
                    height: `${maintHeight}px`,
                    background: '#ea580c',
                    borderRadius: '4px 4px 0 0',
                    transition: 'height 0.3s ease',
                  }}
                />
              </div>
              <small style={{
                marginTop: 6,
                fontSize: '0.75rem',
                color: 'var(--text-muted, #64748b)',
                maxWidth: 70,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap'
              }}>
                {item.assetTag || item.equipmentName}
              </small>
            </div>
          )
        })}
      </div>
    </div>
  )
}

export default function CostOverview() {
  const { user } = useAuth()
  const isSysAdmin = user?.roles?.includes('SYSTEM_ADMIN')

  const [activeTab, setActiveTab] = useState('EQUIPMENT') // 'EQUIPMENT' or 'DEPARTMENT'
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [institutionId, setInstitutionId] = useState('')
  const [institutions, setInstitutions] = useState([])

  // Equipment Cost data
  const [equipmentCosts, setEquipmentCosts] = useState([])
  const [loadingEquipment, setLoadingEquipment] = useState(true)

  // Department Allocation data
  const [departmentData, setDepartmentData] = useState({ allocations: [], totalInstitutionalCost: 0, unallocatedCost: 0 })
  const [loadingDepartment, setLoadingDepartment] = useState(true)

  const [error, setError] = useState('')

  // Load institutions if SYSTEM_ADMIN
  useEffect(() => {
    if (isSysAdmin) {
      api.get('/api/institutions')
        .then((res) => setInstitutions(Array.isArray(res.data) ? res.data : []))
        .catch(() => setInstitutions([]))
    }
  }, [isSysAdmin])

  const fetchEquipmentCosts = useCallback(async () => {
    setLoadingEquipment(true)
    setError('')
    try {
      const params = {}
      if (fromDate) params.from = fromDate
      if (toDate) params.to = toDate
      if (institutionId) params.institutionId = institutionId

      const res = await api.get('/api/billing/costs/equipment', { params })
      setEquipmentCosts(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      console.error('Failed to load equipment costs', err)
      setError('Unable to load equipment costs.')
    } finally {
      setLoadingEquipment(false)
    }
  }, [fromDate, toDate, institutionId])

  const fetchDepartmentAllocations = useCallback(async () => {
    setLoadingDepartment(true)
    setError('')
    try {
      const params = {}
      if (fromDate) params.from = fromDate
      if (toDate) params.to = toDate
      if (institutionId) params.institutionId = institutionId

      const res = await api.get('/api/billing/allocations/department', { params })
      setDepartmentData(res.data || { allocations: [], totalInstitutionalCost: 0, unallocatedCost: 0 })
    } catch (err) {
      console.error('Failed to load department allocations', err)
      setError('Unable to load department allocations.')
    } finally {
      setLoadingDepartment(false)
    }
  }, [fromDate, toDate, institutionId])

  useEffect(() => {
    fetchEquipmentCosts()
    fetchDepartmentAllocations()
  }, [fetchEquipmentCosts, fetchDepartmentAllocations])

  return (
    <div className="page-container" style={{ maxWidth: 1250, margin: '0 auto', padding: '24px 16px' }}>
      <header className="page-header" style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 700, margin: 0 }}>Cost Overview & Analytics</h1>
        <p className="muted-text" style={{ marginTop: 4, margin: 0, color: 'var(--text-muted, #64748b)' }}>
          Analyze equipment usage expenditures, maintenance investments, and departmental cost allocations
        </p>
      </header>

      {/* Date & Institution Filter Bar */}
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
              onChange={(e) => setInstitutionId(e.target.value)}
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
            From Date
          </label>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => setFromDate(e.target.value)}
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
            onChange={(e) => setToDate(e.target.value)}
            className="input-text"
            style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
          />
        </div>

        {(fromDate || toDate || institutionId) && (
          <div style={{ alignSelf: 'flex-end', marginLeft: 'auto' }}>
            <button
              type="button"
              className="button-text"
              onClick={() => { setFromDate(''); setToDate(''); setInstitutionId('') }}
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

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 8, borderBottom: '1px solid var(--border-color, #e2e8f0)', marginBottom: 20 }}>
        <button
          type="button"
          onClick={() => setActiveTab('EQUIPMENT')}
          style={{
            padding: '10px 18px',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            fontWeight: 600,
            fontSize: '0.95rem',
            borderBottom: activeTab === 'EQUIPMENT' ? '2px solid var(--primary, #0284c7)' : '2px solid transparent',
            color: activeTab === 'EQUIPMENT' ? 'var(--primary, #0284c7)' : 'var(--text-muted, #64748b)',
          }}
        >
          🔬 Equipment Costs & Maintenance
        </button>
        <button
          type="button"
          onClick={() => setActiveTab('DEPARTMENT')}
          style={{
            padding: '10px 18px',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            fontWeight: 600,
            fontSize: '0.95rem',
            borderBottom: activeTab === 'DEPARTMENT' ? '2px solid var(--primary, #0284c7)' : '2px solid transparent',
            color: activeTab === 'DEPARTMENT' ? 'var(--primary, #0284c7)' : 'var(--text-muted, #64748b)',
          }}
        >
          🏢 Department Cost Allocations
        </button>
      </div>

      {/* TAB 1: Equipment Costs */}
      {activeTab === 'EQUIPMENT' && (
        <div>
          {/* Chart */}
          <CostBarChart items={equipmentCosts} />

          {/* Table */}
          {loadingEquipment ? (
            <div className="module-state" role="status" style={{ padding: 40, textAlign: 'center' }}>
              Loading equipment costs…
            </div>
          ) : equipmentCosts.length === 0 ? (
            <div className="empty-state" style={{ padding: 40, textAlign: 'center', background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)' }}>
              <p className="muted-text">No equipment cost data found for the selected period.</p>
            </div>
          ) : (
            <div style={{ background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', overflow: 'hidden' }}>
              <div style={{ overflowX: 'auto' }}>
                <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)', background: 'var(--table-header-bg, #f8fafc)' }}>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', whiteSpace: 'nowrap' }}>Equipment</th>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', whiteSpace: 'nowrap' }}>Usage Duration</th>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>Internal Usage</th>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>External Usage</th>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>Total Usage Cost</th>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', color: '#ea580c', whiteSpace: 'nowrap' }}>
                        Maintenance Cost
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {equipmentCosts.map((item) => (
                      <tr key={item.equipmentId} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                        <td style={{ padding: '12px 16px' }}>
                          <div style={{ fontWeight: 600 }}>{item.equipmentName}</div>
                          <small style={{ color: 'var(--text-muted, #64748b)' }}>Tag: {item.assetTag || '—'}</small>
                        </td>
                        <td style={{ padding: '12px 16px', fontSize: '0.9rem', whiteSpace: 'nowrap' }}>
                          {item.usageHours != null ? `${Number(item.usageHours).toFixed(2)} hrs` : '0.00 hrs'}
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted, #64748b)' }}>
                            ({item.usageMinutes || 0} mins)
                          </div>
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'right', fontSize: '0.9rem', whiteSpace: 'nowrap' }}>
                          {formatAmount(item.internalUsageCost)}
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'right', fontSize: '0.9rem', whiteSpace: 'nowrap' }}>
                          {formatAmount(item.externalUsageCost)}
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: 700, color: '#0284c7', whiteSpace: 'nowrap' }}>
                          {formatAmount(item.totalUsageCost)}
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: 600, color: '#ea580c', whiteSpace: 'nowrap' }}>
                          {formatAmount(item.maintenanceCost)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}

      {/* TAB 2: Department Allocations */}
      {activeTab === 'DEPARTMENT' && (
        <div>
          {/* Unallocated charges warning if any */}
          {Number(departmentData?.unallocatedCost || 0) > 0 && (
            <div style={{
              background: '#fff7ed',
              border: '1px solid #fdba74',
              borderRadius: 8,
              padding: '12px 16px',
              marginBottom: 16,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between'
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <span style={{ fontSize: '1.25rem' }}>⚠️</span>
                <div>
                  <div style={{ fontWeight: 600, color: '#9a3412', fontSize: '0.9rem' }}>
                    Unallocated Charges Detected: {formatAmount(departmentData.unallocatedCost)}
                  </div>
                  <small style={{ color: '#c2410c' }}>
                    Some usage charges belong to users without an assigned department.
                  </small>
                </div>
              </div>
            </div>
          )}

          {/* Institutional Total summary card */}
          <div style={{
            background: 'var(--bg-card, #ffffff)',
            border: '1px solid var(--border-color, #e2e8f0)',
            borderRadius: 8,
            padding: 16,
            marginBottom: 20,
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}>
            <div>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)', fontWeight: 600, textTransform: 'uppercase' }}>
                Total Institutional Spend
              </div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a', marginTop: 4 }}>
                {formatAmount(departmentData.totalInstitutionalCost)}
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>Active Departments</div>
              <div style={{ fontSize: '1.25rem', fontWeight: 600 }}>{departmentData.allocations?.length || 0}</div>
            </div>
          </div>

          {loadingDepartment ? (
            <div className="module-state" role="status" style={{ padding: 40, textAlign: 'center' }}>
              Loading department allocations…
            </div>
          ) : departmentData.allocations?.length === 0 ? (
            <div className="empty-state" style={{ padding: 40, textAlign: 'center', background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)' }}>
              <p className="muted-text">No department allocations recorded for this period.</p>
            </div>
          ) : (
            <div style={{ background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', overflow: 'hidden' }}>
              <div style={{ overflowX: 'auto' }}>
                <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
                  <thead>
                    <tr style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)', background: 'var(--table-header-bg, #f8fafc)' }}>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', whiteSpace: 'nowrap' }}>Department</th>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>Internal Usage</th>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>External Sharing</th>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>Total Spend</th>
                      <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right', whiteSpace: 'nowrap' }}>Share of Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {departmentData.allocations.map((alloc) => (
                      <tr key={alloc.departmentId} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                        <td style={{ padding: '12px 16px', fontWeight: 600, whiteSpace: 'nowrap' }}>
                          {alloc.departmentName}
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'right', fontSize: '0.9rem', whiteSpace: 'nowrap' }}>
                          {formatAmount(alloc.internalCost)}
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'right', fontSize: '0.9rem', whiteSpace: 'nowrap' }}>
                          {formatAmount(alloc.externalCost)}
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: 700, color: 'var(--primary, #0284c7)', whiteSpace: 'nowrap' }}>
                          {formatAmount(alloc.totalCost)}
                        </td>
                        <td style={{ padding: '12px 16px', textAlign: 'right', whiteSpace: 'nowrap' }}>
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 8 }}>
                            <div style={{ width: 60, height: 6, background: '#e2e8f0', borderRadius: 3, overflow: 'hidden' }}>
                              <div
                                style={{
                                  width: `${Math.min(100, Number(alloc.percentageShare || 0))}%`,
                                  height: '100%',
                                  background: '#0284c7',
                                }}
                              />
                            </div>
                            <span style={{ fontSize: '0.85rem', fontWeight: 600, minWidth: 45 }}>
                              {Number(alloc.percentageShare || 0).toFixed(1)}%
                            </span>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
