import { useCallback, useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import api from '../../api/axiosInstance'
import {
  PLATFORM_TIMEZONE,
  DEFAULT_OPERATING_SCHEDULE,
  buildAnalyticsQueryParams,
  getDateRangeForPreset,
  resolveAnalyticsUrlState,
  validateDateRange,
} from '../../access/analyticsModel.js'
import UtilizationHeatmap from './UtilizationHeatmap'
import PeakUsageChart from './PeakUsageChart'
import IdleTimeAlerts from './IdleTimeAlerts'
import DemandAnalysisView from './DemandAnalysisView'
import DepartmentUtilizationTable from './DepartmentUtilizationTable'

export default function UtilizationAnalyticsDashboard({ equipmentList = [] }) {
  const [searchParams, setSearchParams] = useSearchParams()

  const initialUrlState = resolveAnalyticsUrlState(searchParams)

  const [timeRangePreset, setTimeRangePreset] = useState(initialUrlState.preset)
  const [startDate, setStartDate] = useState(initialUrlState.start)
  const [endDate, setEndDate] = useState(initialUrlState.end)
  const [targetRateInput, setTargetRateInput] = useState(initialUrlState.target)
  const [selectedDept, setSelectedDept] = useState(initialUrlState.dept)
  const [selectedEquipment, setSelectedEquipment] = useState(initialUrlState.equipment)

  const [institutionSummary, setInstitutionSummary] = useState(null)
  const [equipmentRates, setEquipmentRates] = useState([])
  const [departmentSummaries, setDepartmentSummaries] = useState([])
  const [demandData, setDemandData] = useState(null)
  const [idleReports, setIdleReports] = useState([])
  const [peakData, setPeakData] = useState(null)
  const [heatmapData, setHeatmapData] = useState(null)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [sectionErrors, setSectionErrors] = useState({})

  const requestSeqRef = useRef(0)

  const syncParams = useCallback((updates) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      for (const [key, value] of Object.entries(updates)) {
        if (value !== undefined && value !== null && value !== '') {
          next.set(key, String(value))
        } else {
          next.delete(key)
        }
      }
      next.delete('hours') // ensure legacy misleading parameter is deleted
      return next
    }, { replace: true })
  }, [setSearchParams])

  // Sync state when URL search params change externally (browser navigation / link restoration)
  useEffect(() => {
    const nextState = resolveAnalyticsUrlState(searchParams)
    setTimeRangePreset(nextState.preset)
    setStartDate(nextState.start)
    setEndDate(nextState.end)
    setSelectedDept(nextState.dept)
    setSelectedEquipment(nextState.equipment)
    setTargetRateInput(nextState.target)
  }, [searchParams])

  function applyPreset(preset) {
    const range = getDateRangeForPreset(preset)
    setTimeRangePreset(range.preset)
    setStartDate(range.start)
    setEndDate(range.end)
    syncParams({ preset: range.preset, start: range.start, end: range.end })
  }

  const loadAnalytics = useCallback(async () => {
    const validation = validateDateRange(startDate, endDate)
    if (!validation.valid) {
      setError(validation.error)
      setLoading(false)
      return
    }

    const currentSeq = ++requestSeqRef.current
    setLoading(true)
    setError('')

    // Immediately clear stale analytics results so previously loaded presets (e.g. 90d) do not linger
    setInstitutionSummary(null)
    setEquipmentRates([])
    setDepartmentSummaries([])
    setDemandData(null)
    setIdleReports([])
    setPeakData(null)
    setHeatmapData(null)

    try {
      const params = buildAnalyticsQueryParams({
        startDate,
        endDate,
        departmentId: selectedDept,
        equipmentId: selectedEquipment,
        targetRate: targetRateInput,
      })
      const idleParams = {}
      if (selectedDept) idleParams.departmentId = selectedDept
      if (selectedEquipment) idleParams.equipmentId = selectedEquipment

      const [instRes, ratesRes, deptsRes, demandRes, idleRes, peakRes, heatmapRes] =
        await Promise.allSettled([
          api.get('/api/analytics/utilization/institution', { params }),
          api.get('/api/analytics/utilization/equipment', { params }),
          api.get('/api/analytics/utilization/departments', { params }),
          api.get('/api/analytics/demand', { params }),
          api.get('/api/analytics/idle-time', { params: idleParams }),
          api.get('/api/analytics/peak-usage', { params }),
          api.get('/api/analytics/heatmap', { params }),
        ])

      // Stale-response guard: ignore response if a newer query was fired
      if (currentSeq !== requestSeqRef.current) {
        return
      }

      const errors = {}
      if (instRes.status === 'fulfilled') {
        setInstitutionSummary(instRes.value.data)
      } else {
        errors.institution = instRes.reason?.response?.data?.message || 'Failed to load institution summary'
      }

      if (ratesRes.status === 'fulfilled') {
        setEquipmentRates(ratesRes.value.data || [])
      } else {
        errors.rates = ratesRes.reason?.response?.data?.message || 'Failed to load equipment rates'
      }

      if (deptsRes.status === 'fulfilled') {
        setDepartmentSummaries(deptsRes.value.data || [])
      } else {
        errors.departments = deptsRes.reason?.response?.data?.message || 'Failed to load department breakdown'
      }

      if (demandRes.status === 'fulfilled') {
        setDemandData(demandRes.value.data)
      } else {
        errors.demand = demandRes.reason?.response?.data?.message || 'Failed to load demand analysis'
      }

      if (idleRes.status === 'fulfilled') {
        setIdleReports(idleRes.value.data || [])
      } else {
        errors.idle = idleRes.reason?.response?.data?.message || 'Failed to load idle alerts'
      }

      if (peakRes.status === 'fulfilled') {
        setPeakData(peakRes.value.data)
      } else {
        errors.peak = peakRes.reason?.response?.data?.message || 'Failed to load peak usage'
      }

      if (heatmapRes.status === 'fulfilled') {
        setHeatmapData(heatmapRes.value.data)
      } else {
        errors.heatmap = heatmapRes.reason?.response?.data?.message || 'Failed to load heatmap'
      }

      setSectionErrors(errors)

      const failures = [instRes, ratesRes, demandRes, heatmapRes].filter((r) => r.status === 'rejected')
      if (failures.length === 4) {
        setError('Unable to load analytics data. Please check connection and permissions.')
      }
    } catch (err) {
      if (currentSeq === requestSeqRef.current) {
        setError(err?.response?.data?.message || err?.message || 'Error fetching analytics.')
      }
    } finally {
      if (currentSeq === requestSeqRef.current) {
        setLoading(false)
      }
    }
  }, [startDate, endDate, selectedDept, selectedEquipment, targetRateInput])

  useEffect(() => {
    void loadAnalytics()
  }, [loadAnalytics])

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
      {/* Controls & Filter Bar */}
      <section className="app-surface utilization-panel" style={{ padding: '18px 22px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', flexWrap: 'wrap', gap: '8px' }}>
          <div>
            <h2 style={{ margin: 0, fontSize: '1.15rem' }}>Resource Utilization Analytics & Demand Intelligence</h2>
            <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginTop: '3px' }}>
              🕒 Evaluated in platform timezone (<strong>{PLATFORM_TIMEZONE}</strong>). Default schedule: <strong>Mon–Fri 09:00–17:00 IST</strong> (Sat &amp; Sun closed). Custom schedules apply only when explicitly configured.
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '14px', alignItems: 'flex-end', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '10px', alignItems: 'center' }}>
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--text)' }}>Time Window:</span>
            <button
              type="button"
              className={timeRangePreset === '7d' ? 'primary-button' : 'secondary-button'}
              style={{ padding: '6px 12px', fontSize: '0.82rem' }}
              onClick={() => applyPreset('7d')}
            >
              Last 7 Days
            </button>
            <button
              type="button"
              className={timeRangePreset === '30d' ? 'primary-button' : 'secondary-button'}
              style={{ padding: '6px 12px', fontSize: '0.82rem' }}
              onClick={() => applyPreset('30d')}
            >
              Last 30 Days
            </button>
            <button
              type="button"
              className={timeRangePreset === '90d' ? 'primary-button' : 'secondary-button'}
              style={{ padding: '6px 12px', fontSize: '0.82rem' }}
              onClick={() => applyPreset('90d')}
            >
              Last 90 Days
            </button>
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'center' }}>
            <label style={{ fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
              From:
              <input
                type="date"
                value={startDate}
                max={endDate}
                onChange={(e) => {
                  setStartDate(e.target.value)
                  setTimeRangePreset('custom')
                  syncParams({ start: e.target.value, preset: 'custom' })
                }}
                style={{ padding: '4px 8px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--bg)', color: 'var(--text)' }}
              />
            </label>
            <label style={{ fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
              To:
              <input
                type="date"
                value={endDate}
                min={startDate}
                onChange={(e) => {
                  setEndDate(e.target.value)
                  setTimeRangePreset('custom')
                  syncParams({ end: e.target.value, preset: 'custom' })
                }}
                style={{ padding: '4px 8px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--bg)', color: 'var(--text)' }}
              />
            </label>

            {/* Read-only operating schedule banner replacing misleading 24/7 dropdown */}
            <div
              className="schedule-read-only-banner"
              style={{
                fontSize: '0.82rem',
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                padding: '5px 12px',
                borderRadius: '6px',
                border: '1px solid var(--border)',
                background: 'rgba(59, 130, 246, 0.08)',
                color: 'var(--text)',
              }}
              title={DEFAULT_OPERATING_SCHEDULE.description}
            >
              <span style={{ fontWeight: 600, color: '#38bdf8' }}>Operating Schedule:</span>
              <span>
                <strong>{DEFAULT_OPERATING_SCHEDULE.displayText}</strong>
              </span>
              <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)', padding: '2px 6px', borderRadius: '4px', background: 'rgba(255, 255, 255, 0.06)' }}>
                {DEFAULT_OPERATING_SCHEDULE.badgeLabel}
              </span>
            </div>

            <label style={{ fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
              Target (%):
              <input
                type="number"
                min="1"
                max="100"
                placeholder="Optional"
                value={targetRateInput}
                onChange={(e) => {
                  setTargetRateInput(e.target.value)
                  syncParams({ target: e.target.value })
                }}
                style={{ width: '65px', padding: '4px 8px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--bg)', color: 'var(--text)' }}
              />
            </label>
            {equipmentList.length > 0 && (
              <label style={{ fontSize: '0.82rem', display: 'flex', alignItems: 'center', gap: '6px' }}>
                Equipment:
                <select
                  value={selectedEquipment}
                  onChange={(e) => {
                    setSelectedEquipment(e.target.value)
                    syncParams({ equipment: e.target.value })
                  }}
                  style={{ padding: '4px 8px', borderRadius: '6px', border: '1px solid var(--border)', background: 'var(--bg)', color: 'var(--text)' }}
                >
                  <option value="">All Equipment</option>
                  {equipmentList.map((eq) => (
                    <option key={eq.id} value={String(eq.id)}>{eq.name || `Equipment #${eq.id}`}</option>
                  ))}
                </select>
              </label>
            )}
            <button type="button" className="secondary-button" style={{ padding: '6px 12px' }} onClick={loadAnalytics} disabled={loading}>
              {loading ? 'Refreshing…' : '↻ Refresh'}
            </button>
          </div>
        </div>
      </section>

      {error && (
        <div className="auth-alert" role="alert" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{error}</span>
          <button type="button" className="secondary-button" onClick={loadAnalytics}>Retry</button>
        </div>
      )}

      {/* KPI Overview Cards */}
      <div className="utilization-summary-grid">
        <article className="app-surface utilization-stat">
          <span className="utilization-stat-icon">📈</span>
          <p>Overall Utilization Rate</p>
          <h2>{loading ? '…' : `${institutionSummary?.overallUtilizationRate ?? 0}%`}</h2>
          <span className="muted-text">
            {loading
              ? 'Evaluating benchmark…'
              : institutionSummary?.trendPercentage != null
              ? `${institutionSummary.trendPercentage >= 0 ? '+' : ''}${institutionSummary.trendPercentage}% vs 28d benchmark (${institutionSummary.historicalBenchmarkRate}%)`
              : 'Benchmark calculated'}
          </span>
          {institutionSummary?.targetGap != null && (
            <div style={{ fontSize: '0.75rem', marginTop: '4px', fontWeight: 600, color: institutionSummary.targetGap >= 0 ? '#34d399' : '#f87171' }}>
              Target Gap: {institutionSummary.targetGap >= 0 ? `+${institutionSummary.targetGap}% (Above target)` : `${institutionSummary.targetGap}% (Below target)`}
            </div>
          )}
        </article>
        <article className="app-surface utilization-stat">
          <span className="utilization-stat-icon">⏳</span>
          <p>Total Usage Hours</p>
          <h2>{loading ? '…' : `${institutionSummary?.actualUsageHours ?? 0} hrs`}</h2>
          <span className="muted-text">
            {loading
              ? 'Calculating capacity…'
              : institutionSummary?.totalCapacityHours != null
              ? `Out of ${institutionSummary.totalCapacityHours} total capacity hrs`
              : 'Capacity calculated'}
          </span>
        </article>
        <article className="app-surface utilization-stat">
          <span className="utilization-stat-icon">⚠️</span>
          <p>Underutilized Alerts</p>
          <h2>{loading ? '…' : idleReports.filter((r) => r.idleAlert).length}</h2>
          <span className="muted-text">Active alerts with &gt;=90% idle time</span>
        </article>
      </div>

      {/* 7x24 Heatmap Grid */}
      <UtilizationHeatmap heatmapData={heatmapData} loading={loading} />

      {/* Peak Concurrency & Demand Analysis 2-Column Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))', gap: '22px' }}>
        <PeakUsageChart peakData={peakData} loading={loading} />
        <DemandAnalysisView demandData={demandData} loading={loading} />
      </div>

      {/* Idle Time Detection Alerts */}
      <IdleTimeAlerts idleReports={idleReports} loading={loading} />

      {/* Department Rollups */}
      {departmentSummaries.length > 0 && (
        <DepartmentUtilizationTable departments={departmentSummaries} loading={loading} />
      )}

      {/* Equipment Utilization Rates Detail Table */}
      <section className="app-surface utilization-panel">
        <div className="utilization-panel-heading">
          <div>
            <h2>Equipment Utilization Rates & Historical Benchmarks</h2>
            <p className="muted-text">Per-equipment rate calculation against available capacity and trend analysis.</p>
          </div>
          <span className="utilization-record-count">
            {equipmentRates.length} {equipmentRates.length === 1 ? 'asset' : 'assets'}
          </span>
        </div>

        {equipmentRates.length === 0 ? (
          <div className="utilization-state">
            <span className="utilization-empty-icon">▣</span>
            <h3>{loading ? 'Loading utilization data…' : 'No equipment utilization data'}</h3>
            <p className="muted-text">
              {loading ? 'Fetching records for the selected date range…' : 'Equipment usage will appear here once sessions are recorded.'}
            </p>
          </div>
        ) : (
          <div className="utilization-table-wrapper">
            <table className="utilization-table">
              <thead>
                <tr>
                  <th>Equipment</th>
                  <th>Asset Tag</th>
                  <th>Department</th>
                  <th>Units</th>
                  <th>Capacity</th>
                  <th>Usage (Excl / Shared)</th>
                  <th>Rate (%)</th>
                  {targetRateInput && <th>Target Gap</th>}
                  <th>Prior Benchmark</th>
                  <th>Trend</th>
                </tr>
              </thead>
              <tbody>
                {equipmentRates.map((item) => {
                  const trend = item.trendPercentage ?? 0
                  const trendColor = trend > 0 ? '#34d399' : trend < 0 ? '#f87171' : 'var(--text-muted)'
                  return (
                    <tr key={item.equipmentId}>
                      <td><strong>{item.equipmentName}</strong></td>
                      <td>{item.assetTag || '—'}</td>
                      <td>{item.departmentName || '—'}</td>
                      <td>{item.quantity}</td>
                      <td>{item.totalCapacityHours}h</td>
                      <td>{item.actualUsageHours}h ({item.exclusiveUsageHours}h / {item.sharedUsageHours}h)</td>
                      <td>
                        <strong style={{ color: item.utilizationRate > 75 ? '#f59e0b' : 'var(--text)' }}>
                          {item.utilizationRate != null ? `${item.utilizationRate}%` : '—'}
                        </strong>
                      </td>
                      {targetRateInput && (
                        <td>
                          {item.targetGap != null ? (
                            <span style={{ color: item.targetGap >= 0 ? '#34d399' : '#f87171', fontWeight: 600 }}>
                              {item.targetGap >= 0 ? `+${item.targetGap}%` : `${item.targetGap}%`}
                            </span>
                          ) : '—'}
                        </td>
                      )}
                      <td>{item.historicalBenchmarkRate != null ? `${item.historicalBenchmarkRate}%` : '—'}</td>
                      <td>
                        <span style={{ color: trendColor, fontWeight: 600 }}>
                          {trend > 0 ? `+${trend}%` : `${trend}%`}
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
