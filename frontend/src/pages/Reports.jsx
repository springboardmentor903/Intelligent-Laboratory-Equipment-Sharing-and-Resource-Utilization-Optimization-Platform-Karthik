import { useState, useEffect, useCallback } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import {
  getAllowedReportTypes,
  formatReportCellValue,
  downloadReportFile,
} from '../access/reportWorkflow.js'

export default function Reports() {
  const { user } = useAuth()
  const roles = user?.roles || []
  const isSysAdmin = roles.includes('SYSTEM_ADMIN')

  const allowedReportTypes = getAllowedReportTypes(roles)
  const defaultReportType = allowedReportTypes[0]?.type || 'UTILIZATION'

  const [selectedType, setSelectedType] = useState(defaultReportType)
  const [institutions, setInstitutions] = useState([])
  const [institutionId, setInstitutionId] = useState(user?.institutionId ? String(user.institutionId) : '')
  const [departments, setDepartments] = useState([])
  const [departmentId, setDepartmentId] = useState('')

  // Default date range: last 30 days
  const todayStr = new Date().toISOString().split('T')[0]
  const thirtyDaysAgo = new Date(Date.now() - 30 * 86400000).toISOString().split('T')[0]
  const [startDate, setStartDate] = useState(thirtyDaysAgo)
  const [endDate, setEndDate] = useState(todayStr)

  const [reportResult, setReportResult] = useState(null)
  const [loadingPreview, setLoadingPreview] = useState(false)
  const [exportingFormat, setExportingFormat] = useState(null)
  const [error, setError] = useState('')

  // Sort state
  const [sortField, setSortField] = useState(null)
  const [sortDirection, setSortDirection] = useState('asc')

  // Fetch institutions if sysadmin
  useEffect(() => {
    if (isSysAdmin) {
      api.get('/api/institutions')
        .then((res) => {
          const list = Array.isArray(res.data) ? res.data : []
          setInstitutions(list)
          if (!institutionId && list.length > 0) {
            setInstitutionId(String(list[0].id))
          }
        })
        .catch(() => {})
    }
  }, [isSysAdmin, institutionId])

  // Fetch departments
  useEffect(() => {
    api.get('/api/departments')
      .then((res) => {
        setDepartments(Array.isArray(res.data) ? res.data : [])
      })
      .catch(() => {})
  }, [])

  const fetchReportPreview = useCallback(async () => {
    setLoadingPreview(true)
    setError('')
    try {
      const q = new URLSearchParams()
      if (institutionId) q.append('institutionId', institutionId)
      if (departmentId) q.append('departmentId', departmentId)
      if (startDate) q.append('startDate', startDate)
      if (endDate) q.append('endDate', endDate)

      const res = await api.get(`/api/reports/${selectedType}?${q.toString()}`)
      setReportResult(res.data)
      setSortField(null)
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to generate report preview.'
      setError(msg)
      setReportResult(null)
    } finally {
      setLoadingPreview(false)
    }
  }, [selectedType, institutionId, departmentId, startDate, endDate])

  useEffect(() => {
    fetchReportPreview()
  }, [fetchReportPreview])

  const handleExport = async (format) => {
    setExportingFormat(format)
    try {
      await downloadReportFile(api, selectedType, format, {
        institutionId,
        departmentId,
        startDate,
        endDate,
      })
    } catch (err) {
      alert(err.response?.data?.message || `Failed to export ${format.toUpperCase()} report.`)
    } finally {
      setExportingFormat(null)
    }
  }

  // Handle column sorting
  const handleSort = (field) => {
    if (sortField === field) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc')
    } else {
      setSortField(field)
      setSortDirection('asc')
    }
  }

  const sortedRows = (reportResult?.rows || []).slice().sort((a, b) => {
    if (!sortField) return 0
    const valA = a[sortField]
    const valB = b[sortField]
    if (valA === valB) return 0
    if (valA === null || valA === undefined) return 1
    if (valB === null || valB === undefined) return -1
    const comparison = valA > valB ? 1 : -1
    return sortDirection === 'asc' ? comparison : -comparison
  })

  const currentReportMeta = allowedReportTypes.find((r) => r.type === selectedType)

  return (
    <div style={{ maxWidth: 1300, margin: '0 auto', padding: '24px 16px', display: 'flex', flexDirection: 'column', gap: 20 }}>
      {/* Header */}
      <header>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
          <span style={{
            background: 'var(--primary-light, #ccfbf1)',
            color: 'var(--primary, #0f766e)',
            fontSize: '0.75rem',
            fontWeight: 700,
            padding: '2px 8px',
            borderRadius: 6
          }}>
            ANALYTICS & AUDIT
          </span>
          <span style={{ color: 'var(--text-muted)', fontSize: '0.85rem' }}>Enterprise Export Hub</span>
        </div>
        <h1 style={{ fontSize: '1.75rem', fontWeight: 800, margin: 0, color: 'var(--text)' }}>
          Reports & Export Center
        </h1>
        <p style={{ margin: '4px 0 0', color: 'var(--text-muted)', fontSize: '0.95rem' }}>
          Generate, preview, and download presentation-ready PDF reports and OpenXML Excel workbooks.
        </p>
      </header>

      {/* Report Types Selector Cards */}
      <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 12 }}>
        {allowedReportTypes.map((r) => {
          const isSelected = r.type === selectedType
          return (
            <button
              key={r.type}
              type="button"
              onClick={() => setSelectedType(r.type)}
              style={{
                textAlign: 'left',
                border: isSelected ? '2px solid var(--primary, #0f766e)' : '1px solid var(--border)',
                background: isSelected ? 'var(--surface)' : 'var(--surface-secondary)',
                borderRadius: 10,
                padding: '14px 16px',
                cursor: 'pointer',
                display: 'flex',
                flexDirection: 'column',
                gap: 6,
                boxShadow: isSelected ? '0 4px 12px rgba(15, 118, 110, 0.12)' : 'none',
                transition: 'all 0.15s ease'
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontSize: '1.25rem' }}>{r.icon}</span>
                <strong style={{ fontSize: '0.9rem', color: isSelected ? 'var(--primary, #0f766e)' : 'var(--text)' }}>
                  {r.title}
                </strong>
              </div>
              <p style={{ margin: 0, fontSize: '0.78rem', color: 'var(--text-muted)', lineHeight: 1.3 }}>
                {r.description}
              </p>
            </button>
          )
        })}
      </section>

      {/* Filter & Action Toolbar */}
      <section style={{
        background: 'var(--surface)',
        border: '1px solid var(--border)',
        borderRadius: 12,
        padding: '16px 20px',
        display: 'flex',
        flexWrap: 'wrap',
        gap: 14,
        alignItems: 'center',
        justifyContent: 'space-between'
      }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center' }}>
          {isSysAdmin && institutions.length > 0 && (
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted)' }}>
                Institution
              </label>
              <select
                value={institutionId}
                onChange={(e) => setInstitutionId(e.target.value)}
                style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: '0.85rem' }}
              >
                {institutions.map((inst) => (
                  <option key={inst.id} value={inst.id}>{inst.name}</option>
                ))}
              </select>
            </div>
          )}

          {departments.length > 0 && (
            <div>
              <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted)' }}>
                Department
              </label>
              <select
                value={departmentId}
                onChange={(e) => setDepartmentId(e.target.value)}
                style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: '0.85rem' }}
              >
                <option value="">All Departments</option>
                {departments.map((d) => (
                  <option key={d.id} value={d.id}>{d.name}</option>
                ))}
              </select>
            </div>
          )}

          <div>
            <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted)' }}>
              Start Date
            </label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: '0.85rem' }}
            />
          </div>

          <div>
            <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted)' }}>
              End Date
            </label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border)', fontSize: '0.85rem' }}
            />
          </div>

          <div style={{ alignSelf: 'flex-end' }}>
            <button
              type="button"
              onClick={fetchReportPreview}
              disabled={loadingPreview}
              style={{
                background: 'var(--primary, #0f766e)',
                color: '#ffffff',
                border: 'none',
                padding: '7px 16px',
                borderRadius: 6,
                fontWeight: 600,
                fontSize: '0.85rem',
                cursor: loadingPreview ? 'not-allowed' : 'pointer'
              }}
            >
              {loadingPreview ? 'Loading…' : '↻ Apply Filters'}
            </button>
          </div>
        </div>

        {/* Export Buttons */}
        <div style={{ display: 'flex', gap: 10, alignSelf: 'flex-end' }}>
          <button
            type="button"
            onClick={() => handleExport('pdf')}
            disabled={exportingFormat != null || loadingPreview || !reportResult}
            style={{
              background: '#b91c1c',
              color: '#ffffff',
              border: 'none',
              padding: '8px 16px',
              borderRadius: 6,
              fontWeight: 700,
              fontSize: '0.85rem',
              cursor: exportingFormat != null || !reportResult ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }}
          >
            {exportingFormat === 'pdf' ? 'Generating PDF…' : '📄 Export PDF'}
          </button>

          <button
            type="button"
            onClick={() => handleExport('xlsx')}
            disabled={exportingFormat != null || loadingPreview || !reportResult}
            style={{
              background: '#15803d',
              color: '#ffffff',
              border: 'none',
              padding: '8px 16px',
              borderRadius: 6,
              fontWeight: 700,
              fontSize: '0.85rem',
              cursor: exportingFormat != null || !reportResult ? 'not-allowed' : 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }}
          >
            {exportingFormat === 'xlsx' ? 'Generating Excel…' : '📊 Export Excel'}
          </button>
        </div>
      </section>

      {/* Error / 10,000 Row Guard alert */}
      {error && (
        <div style={{
          padding: '14px 18px',
          background: '#fee2e2',
          border: '1px solid #fecaca',
          color: '#991b1b',
          borderRadius: 8,
          fontSize: '0.9rem'
        }}>
          <strong>Notice:</strong> {error}
        </div>
      )}

      {/* Report Preview Container */}
      {reportResult && (
        <section style={{
          background: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 12,
          padding: 24,
          display: 'flex',
          flexDirection: 'column',
          gap: 20
        }}>
          {/* Metadata Header */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', borderBottom: '1px solid var(--border)', paddingBottom: 16 }}>
            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 800, margin: 0, color: 'var(--text)' }}>
                {reportResult.title || currentReportMeta?.title}
              </h2>
              <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginTop: 4 }}>
                Generated on: {reportResult.generatedAt ? new Date(reportResult.generatedAt).toLocaleString() : new Date().toLocaleString()}
                {' '}| Total Records: <strong>{reportResult.rows?.length || 0}</strong>
              </div>
            </div>
            <div style={{ textAlign: 'right', fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Active Period: <strong>{startDate || 'All'}</strong> to <strong>{endDate || 'All'}</strong>
            </div>
          </div>

          {/* Chart Preview Section (SVG Bar Chart) */}
          {reportResult.chartData && reportResult.chartData.length > 0 && (
            <div style={{
              background: 'var(--surface-secondary)',
              border: '1px solid var(--border)',
              borderRadius: 10,
              padding: 16
            }}>
              <h3 style={{ fontSize: '0.95rem', fontWeight: 700, margin: '0 0 12px', color: 'var(--text)' }}>
                📊 Visual Trend & Breakdown
              </h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {reportResult.chartData.slice(0, 10).map((pt, idx) => {
                  const maxVal = Math.max(...reportResult.chartData.map((p) => p.value || 0), 1)
                  const pct = Math.min(100, Math.max(5, ((pt.value || 0) / maxVal) * 100))
                  return (
                    <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: '0.85rem' }}>
                      <span style={{ width: 140, fontWeight: 600, color: 'var(--text)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        {pt.label}
                      </span>
                      <div style={{ flex: 1, height: 18, background: '#e2e8f0', borderRadius: 4, overflow: 'hidden' }}>
                        <div style={{ width: `${pct}%`, height: '100%', background: 'var(--primary, #0f766e)', borderRadius: 4 }} />
                      </div>
                      <span style={{ width: 70, textAlign: 'right', fontWeight: 700, color: 'var(--text)' }}>
                        {typeof pt.value === 'number' ? pt.value.toFixed(1) : pt.value}
                      </span>
                    </div>
                  )
                })}
              </div>
            </div>
          )}

          {/* Data Table */}
          {(!reportResult.rows || reportResult.rows.length === 0) ? (
            <div style={{ textAlign: 'center', padding: '30px 0', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
              No records found matching the specified report filters.
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.85rem' }}>
                <thead>
                  <tr style={{ borderBottom: '2px solid var(--border)', background: 'var(--surface-secondary)' }}>
                    {(reportResult.columns || []).map((col) => {
                      const isSorted = sortField === col.name
                      return (
                        <th
                          key={col.name}
                          onClick={() => handleSort(col.name)}
                          style={{
                            padding: '10px 12px',
                            fontWeight: 700,
                            color: isSorted ? 'var(--primary, #0f766e)' : 'var(--text)',
                            cursor: 'pointer',
                            userSelect: 'none',
                            textAlign: (col.type === 'NUMBER' || col.type === 'CURRENCY' || col.type === 'PERCENTAGE') ? 'right' : 'left'
                          }}
                        >
                          {col.header}
                          {isSorted && (sortDirection === 'asc' ? ' ↑' : ' ↓')}
                        </th>
                      )
                    })}
                  </tr>
                </thead>
                <tbody>
                  {sortedRows.map((row, rowIdx) => (
                    <tr
                      key={rowIdx}
                      style={{
                        borderBottom: '1px solid var(--border)',
                        background: rowIdx % 2 === 0 ? 'var(--surface)' : 'var(--surface-secondary)'
                      }}
                    >
                      {(reportResult.columns || []).map((col) => (
                        <td
                          key={col.name}
                          style={{
                            padding: '10px 12px',
                            color: 'var(--text)',
                            textAlign: (col.type === 'NUMBER' || col.type === 'CURRENCY' || col.type === 'PERCENTAGE') ? 'right' : 'left'
                          }}
                        >
                          {formatReportCellValue(row[col.name], col.type)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>

                {/* Summary / Totals Row */}
                {reportResult.totals && Object.keys(reportResult.totals).length > 0 && (
                  <tfoot>
                    <tr style={{
                      borderTop: '2px solid var(--text)',
                      background: 'var(--surface-secondary)',
                      fontWeight: 800
                    }}>
                      {(reportResult.columns || []).map((col, idx) => {
                        const totalVal = reportResult.totals[col.name]
                        return (
                          <td
                            key={col.name}
                            style={{
                              padding: '12px',
                              textAlign: (col.type === 'NUMBER' || col.type === 'CURRENCY' || col.type === 'PERCENTAGE') ? 'right' : 'left',
                              color: 'var(--text)'
                            }}
                          >
                            {idx === 0
                              ? `Total (${reportResult.rows.length} rows)`
                              : totalVal !== undefined
                                ? formatReportCellValue(totalVal, col.type)
                                : '—'}
                          </td>
                        )
                      })}
                    </tr>
                  </tfoot>
                )}
              </table>
            </div>
          )}
        </section>
      )}
    </div>
  )
}
