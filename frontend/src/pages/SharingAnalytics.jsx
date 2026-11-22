import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { formatAmount, formatSharedTime } from '../access/sharedFormatting.js'

const isoDate = (date) => date.toISOString().slice(0, 10)
const dateDaysAgo = (days) => { const date = new Date(); date.setDate(date.getDate() - days); return isoDate(date) }
const errorText = (error) => error?.response?.data?.message || 'Unable to load sharing analytics.'
const fmtHours = (value) => `${Number(value || 0).toFixed(2)} h`

function BarSeries({ rows, firstKey, secondKey, firstLabel, secondLabel, format = fmtHours }) {
  const peak = Math.max(1, ...rows.flatMap(row => [Number(row[firstKey] || 0), Number(row[secondKey] || 0)]))
  if (!rows.length) return <p className="muted-text">No trend data for this period.</p>
  return <>
    <div className="analytics-chart-legend" aria-label="Chart legend">
      <span className="legend-chip">
        <span className="legend-color-dot" style={{ background: '#2563eb' }} aria-hidden="true"></span>
        {firstLabel}
      </span>
      <span className="legend-chip">
        <span className="legend-color-dot" style={{ background: '#b45309' }} aria-hidden="true"></span>
        {secondLabel}
      </span>
    </div>
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, minHeight: 190, overflowX: 'auto', padding: '16px 4px 4px' }}>
      {rows.map(row => <div key={row.periodStart} title={`${row.periodStart}: ${firstLabel} ${format(row[firstKey])}, ${secondLabel} ${format(row[secondKey])}`} style={{ minWidth: 50, flex: 1, textAlign: 'center' }}>
        <div style={{ height: 150, display: 'flex', alignItems: 'flex-end', justifyContent: 'center', gap: 4 }}>
          {[ [firstKey, '#2563eb'], [secondKey, '#b45309'] ].map(([key, color]) => <div key={key} style={{ width: '42%', height: `${Math.max(2, Number(row[key] || 0) / peak * 100)}%`, background: color, borderRadius: '5px 5px 0 0' }} />)}
        </div><small>{String(row.periodStart).slice(5)}</small>
      </div>)}
    </div>
  </>
}

function Pager({ page, pages, onChange, label }) {
  return <nav className="pagination-bar" aria-label={`${label} pages`}>
    <button type="button" className="button-secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>Previous</button>
    <span>Page {page + 1} of {Math.max(1, pages)}</span>
    <button type="button" className="button-secondary" disabled={page + 1 >= pages} onClick={() => onChange(page + 1)}>Next</button>
  </nav>
}

export default function SharingAnalytics() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const query = params.toString()
  const isSystemAdmin = user?.roles?.includes('SYSTEM_ADMIN')
  const [institutions, setInstitutions] = useState([])
  const [summary, setSummary] = useState(null)
  const [trends, setTrends] = useState([])
  const [partners, setPartners] = useState({ content: [], totalPages: 0 })
  const [equipment, setEquipment] = useState({ content: [], totalPages: 0 })
  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const today = isoDate(new Date())
  const from = params.get('from') || dateDaysAgo(89)
  const to = params.get('to') || today
  const institutionId = params.get('institutionId') || (isSystemAdmin ? '' : String(user?.institutionId || ''))
  const grouping = params.get('grouping') || 'WEEKLY'
  const reportPartner = params.get('partnerInstitutionId') || ''
  const partnerPage = Number(params.get('partnerPage') || 0)
  const equipmentPage = Number(params.get('equipmentPage') || 0)
  const partnerSort = params.get('partnerSort') || 'hoursProvided'
  const partnerDirection = params.get('partnerDirection') || 'desc'
  const equipmentSort = params.get('equipmentSort') || 'providedMinutes'
  const equipmentDirection = params.get('equipmentDirection') || 'desc'

  const setFilter = useCallback((updates) => {
    setParams(current => {
      const next = new URLSearchParams(current)
      for (const [key, value] of Object.entries(updates)) {
        if (value === '' || value == null) next.delete(key)
        else next.set(key, String(value))
      }
      return next
    })
  }, [setParams])

  useEffect(() => {
    if (isSystemAdmin) api.get('/api/institutions').then(response => setInstitutions(response.data || [])).catch(() => setInstitutions([]))
  }, [isSystemAdmin])

  const load = useCallback(async () => {
    if (isSystemAdmin && !institutionId) {
      setSummary(null)
      setTrends([])
      setPartners({ content: [], totalPages: 0 })
      setEquipment({ content: [], totalPages: 0 })
      setReport(null)
      setLoading(false)
      return
    }

    setLoading(true); setError('')
    try {
      const base = { institutionId: institutionId || undefined, from, to }
      if (reportPartner) {
        if (!institutionId) {
          setError('Select an institution first to view partnership report.')
          setLoading(false)
          return
        }
        const response = await api.get('/api/sharing/analytics/partnership-report', {
          params: { firstInstitutionId: institutionId, secondInstitutionId: reportPartner, from, to },
        })
        setReport(response.data)
        return
      }
      const [summaryResponse, trendResponse, partnerResponse, equipmentResponse] = await Promise.all([
        api.get('/api/sharing/analytics/summary', { params: base }),
        api.get('/api/sharing/analytics/trends', { params: { ...base, grouping } }),
        api.get('/api/sharing/analytics/partners', { params: { ...base, page: partnerPage, size: 8, sort: partnerSort, direction: partnerDirection } }),
        api.get('/api/sharing/analytics/equipment/most-shared', { params: { ...base, page: equipmentPage, size: 8, sort: equipmentSort, direction: equipmentDirection } }),
      ])
      setSummary(summaryResponse.data); setTrends(trendResponse.data || [])
      setPartners(partnerResponse.data); setEquipment(equipmentResponse.data)
      setReport(null)
    } catch (requestError) { setError(errorText(requestError)) }
    finally { setLoading(false) }
  }, [isSystemAdmin, institutionId, from, to, grouping, partnerPage, equipmentPage, partnerSort, partnerDirection, equipmentSort, equipmentDirection, reportPartner])

  useEffect(() => { void load() }, [load, query])

  const reportQuery = useMemo(() => {
    const next = new URLSearchParams(query)
    next.delete('partnerInstitutionId')
    return next.toString()
  }, [query])

  function setPage(key, value) { setFilter({ [key]: value }) }
  function setSort(field) {
    const direction = partnerSort === field && partnerDirection === 'asc' ? 'desc' : 'asc'
    setFilter({ partnerSort: field, partnerDirection: direction, partnerPage: 0 })
  }
  function setEquipmentSort(field) {
    const direction = equipmentSort === field && equipmentDirection === 'asc' ? 'desc' : 'asc'
    setFilter({ equipmentSort: field, equipmentDirection: direction, equipmentPage: 0 })
  }
  function choosePeriod(value) {
    if (value === 'custom') { setFilter({ period: 'custom' }); return }
    const days = Number(value)
    setFilter({ period: value, from: dateDaysAgo(days - 1), to: isoDate(new Date()), partnerPage: 0, equipmentPage: 0 })
  }

  if (loading && !summary && !report) return <main className="module-page"><div className="module-state" role="status">Loading sharing analytics…</div></main>
  return <main className="module-page">
    <header className="module-page-heading"><div><span className="eyebrow">MILESTONE 2 · SESSION 5</span><h1>{reportPartner ? 'Partnership report' : 'Sharing analytics'}</h1><p>Actual shared usage and agreement performance for the selected period.</p></div></header>
    {error && <div className="module-alert" role="alert">{error} <button type="button" className="button-secondary" onClick={() => void load()}>Retry</button></div>}
    <section className="booking-form-card">
      <div className="module-section-title">
        <div className="module-section-icon" aria-hidden="true">📊</div>
        <div>
          <h2>Analytics Filters</h2>
          <p>Filter sharing metrics by institution, reporting period, or trend grouping.</p>
        </div>
      </div>
      <div className="sharing-filter-panel" style={{ marginTop: 12 }}>
        {isSystemAdmin && (
          <div className="sharing-filter-field">
            <label htmlFor="analytics-institution-select">Institution</label>
            <select
              id="analytics-institution-select"
              value={institutionId}
              onChange={event => setFilter({ institutionId: event.target.value, partnerInstitutionId: '', partnerPage: 0, equipmentPage: 0 })}
            >
              <option value="">Select institution</option>
              {institutions.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </div>
        )}
        <div className="sharing-filter-field">
          <label htmlFor="analytics-period-select">Period</label>
          <select
            id="analytics-period-select"
            value={params.get('period') || '90'}
            onChange={event => choosePeriod(event.target.value)}
          >
            <option value="30">Last 30 days</option>
            <option value="90">Last 90 days</option>
            <option value="365">Last 12 months</option>
            <option value="custom">Custom range</option>
          </select>
        </div>
        <div className="sharing-filter-field">
          <label htmlFor="analytics-from-date">From</label>
          <input
            id="analytics-from-date"
            type="date"
            value={from}
            max={to}
            onChange={event => setFilter({ from: event.target.value, period: 'custom', partnerPage: 0, equipmentPage: 0 })}
          />
        </div>
        <div className="sharing-filter-field">
          <label htmlFor="analytics-to-date">To</label>
          <input
            id="analytics-to-date"
            type="date"
            value={to}
            min={from}
            max={today}
            onChange={event => setFilter({ to: event.target.value, period: 'custom', partnerPage: 0, equipmentPage: 0 })}
          />
        </div>
        {!reportPartner && (
          <div className="sharing-filter-field">
            <label htmlFor="analytics-grouping-select">Trend grouping</label>
            <select
              id="analytics-grouping-select"
              value={grouping}
              onChange={event => setFilter({ grouping: event.target.value })}
            >
              <option value="WEEKLY">Weekly</option>
              <option value="MONTHLY">Monthly</option>
            </select>
          </div>
        )}
      </div>
    </section>
    {!institutionId && isSystemAdmin && <p className="auth-alert" role="status">Select an institution to load its sharing analytics.</p>}
    {reportPartner && report && <>
      <div style={{ marginBottom: 16 }}>
        <Link className="button-secondary" to={`/sharing-analytics${reportQuery ? `?${reportQuery}` : ''}`}>← Back to partners</Link>
      </div>
      <section className="equipment-grid">
        {[[report.firstToSecond, `${report.firstInstitution} → ${report.secondInstitution}`], [report.secondToFirst, `${report.secondInstitution} → ${report.firstInstitution}`]].map(([direction, label]) => (
          <article className="booking-form-card" key={label}>
            <h2>{label}</h2>
            <div className="sharing-meta-list" style={{ marginTop: 10 }}>
              <div className="sharing-meta-item">
                <span className="sharing-meta-label">Hours provided:</span>
                <span className="sharing-meta-value">{fmtHours(direction.hoursProvided)}</span>
              </div>
              <div className="sharing-meta-item">
                <span className="sharing-meta-label">Hours used:</span>
                <span className="sharing-meta-value">{fmtHours(direction.hoursUsed)}</span>
              </div>
              <div className="sharing-meta-item">
                <span className="sharing-meta-label">Fees earned:</span>
                <span className="sharing-meta-value">{formatAmount(direction.feesEarned)}</span>
              </div>
              <div className="sharing-meta-item">
                <span className="sharing-meta-label">Fees owed:</span>
                <span className="sharing-meta-value">{formatAmount(direction.feesOwed)}</span>
              </div>
              <div className="sharing-meta-item">
                <span className="sharing-meta-label">Net balance:</span>
                <span className={`sharing-meta-value ${Number(direction.balance) < 0 ? 'balance-cell-negative' : 'balance-cell-positive'}`}>{formatAmount(direction.balance)}</span>
              </div>
              <div className="sharing-meta-item">
                <span className="sharing-meta-label">Bookings / No-show:</span>
                <span className="sharing-meta-value">{direction.bookingCount} bookings · {Number(direction.noShowRate || 0).toFixed(2)}% no-show</span>
              </div>
            </div>
          </article>
        ))}
      </section>
      <section className="booking-form-card">
        <h2>Balance for {report.firstInstitution}</h2>
        <div style={{ fontSize: '24px', fontWeight: 700, margin: '8px 0', color: Number(report.balanceForFirstInstitution) < 0 ? '#dc2626' : '#16a34a' }}>
          {formatAmount(report.balanceForFirstInstitution)}
        </div>
        <p className="muted-text">{report.bookingCount} bookings · {Number(report.noShowRate || 0).toFixed(2)}% overall no-show rate</p>
      </section>
      <section className="booking-form-card">
        <h2>Monthly sharing trends</h2>
        <BarSeries rows={report.monthlyTrends || []} firstKey="hoursProvided" secondKey="hoursUsed" firstLabel="Hours provided" secondLabel="Hours used" />
      </section>
      <section className="booking-form-card">
        <h2>Agreements</h2>
        {!report.agreements?.length ? (
          <div className="sharing-empty-state"><p>No agreements found for this institution pair.</p></div>
        ) : (
          <div className="utilization-table-wrapper">
            <table className="utilization-table">
              <thead>
                <tr>
                  <th>Equipment</th>
                  <th>Owner</th>
                  <th>Partner</th>
                  <th>Start</th>
                  <th>End</th>
                  <th>Fee/hour</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {report.agreements.map(item => (
                  <tr key={item.agreementId}>
                    <td><strong>{item.equipmentName || `#${item.equipmentId}`}</strong></td>
                    <td>{item.ownerInstitution}</td>
                    <td>{item.partnerInstitution}</td>
                    <td>{formatSharedTime(item.startDate)}</td>
                    <td>{formatSharedTime(item.endDate)}</td>
                    <td>{formatAmount(item.feePerHour)}</td>
                    <td><span className={item.status === 'ACTIVE' ? 'sharing-badge-active' : 'sharing-badge-pending'}>{item.status}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>}
    {!reportPartner && summary && <>
      <section className="utilization-summary-grid">
        {[["Hours provided", fmtHours(summary.hoursProvided)], ["Hours used", fmtHours(summary.hoursUsed)], ["Fees earned", formatAmount(summary.feesEarned)], ["Fees owed", formatAmount(summary.feesOwed)], ["Expected fees", formatAmount(summary.expectedFees)], ["Agreement bookings", summary.bookingCount], ["No-show rate", `${Number(summary.noShowRate || 0).toFixed(2)}%`], ["Active agreements", summary.activeAgreements], ["Partners", summary.partnerCount], ["Expiring in 30 days", summary.agreementsExpiringWithin30Days], ["External share of usage", `${Number(summary.externalShareOfUsage || 0).toFixed(2)}%`]].map(([label, value]) => (
          <article className="app-surface utilization-stat" key={label}>
            <p>{label}</p>
            <h2>{value}</h2>
          </article>
        ))}
      </section>
      <section className="equipment-grid">
        <article className="booking-form-card">
          <h2>Sharing hours</h2>
          <BarSeries rows={trends} firstKey="hoursProvided" secondKey="hoursUsed" firstLabel="Hours provided" secondLabel="Hours used" />
        </article>
        <article className="booking-form-card">
          <h2>Sharing fees</h2>
          <BarSeries rows={trends} firstKey="feesEarned" secondKey="feesOwed" firstLabel="Fees earned" secondLabel="Fees owed" format={formatAmount} />
        </article>
      </section>
      <section className="booking-form-card">
        <h2>Partners</h2>
        {!partners.content?.length ? (
          <div className="sharing-empty-state"><p>No partner activity in this period.</p></div>
        ) : (
          <div className="utilization-table-wrapper">
            <table className="utilization-table">
              <thead>
                <tr>
                  <th>Partner</th>
                  {[['hoursProvided','Hours provided'],['hoursUsed','Hours used'],['feesEarned','Fees earned'],['feesOwed','Fees owed'],['balance','Balance']].map(([field,label])=>(
                    <th key={field}>
                      <button type="button" className="analytics-sort-button" onClick={()=>setSort(field)}>
                        {label}{partnerSort===field?(partnerDirection==='asc'?' ↑':' ↓'):''}
                      </button>
                    </th>
                  ))}
                  <th>Agreements</th>
                  <th>Report</th>
                </tr>
              </thead>
              <tbody>
                {partners.content.map(row=>(
                  <tr key={row.partnerInstitutionId}>
                    <td><strong>{row.partnerInstitution}</strong></td>
                    <td>{fmtHours(row.hoursProvided)}</td>
                    <td>{fmtHours(row.hoursUsed)}</td>
                    <td>{formatAmount(row.feesEarned)}</td>
                    <td>{formatAmount(row.feesOwed)}</td>
                    <td className={Number(row.balance) < 0 ? 'balance-cell-negative' : 'balance-cell-positive'}>
                      {Number(row.balance) > 0 ? '+' : ''}{formatAmount(row.balance)}
                    </td>
                    <td>{row.agreementCount}</td>
                    <td>
                      <Link to={`/sharing-analytics?${new URLSearchParams({ ...Object.fromEntries(new URLSearchParams(query)), partnerInstitutionId: row.partnerInstitutionId })}`}>
                        View report →
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pager page={partnerPage} pages={partners.totalPages || 0} label="Partner" onChange={value=>setPage('partnerPage',value)} />
      </section>
      <section className="booking-form-card">
        <h2>Most shared equipment</h2>
        {!equipment.content?.length ? (
          <div className="sharing-empty-state"><p>No shared equipment usage in this period.</p></div>
        ) : (
          <div className="utilization-table-wrapper">
            <table className="utilization-table">
              <thead>
                <tr>
                  <th>
                    <button className="analytics-sort-button" type="button" onClick={()=>setEquipmentSort('equipmentName')}>
                      Equipment{equipmentSort==='equipmentName'?(equipmentDirection==='asc'?' ↑':' ↓'):''}
                    </button>
                  </th>
                  <th>Asset tag</th>
                  <th>
                    <button className="analytics-sort-button" type="button" onClick={()=>setEquipmentSort('providedMinutes')}>
                      Provided minutes{equipmentSort==='providedMinutes'?(equipmentDirection==='asc'?' ↑':' ↓'):''}
                    </button>
                  </th>
                  <th>
                    <button className="analytics-sort-button" type="button" onClick={()=>setEquipmentSort('feesEarned')}>
                      Fees earned{equipmentSort==='feesEarned'?(equipmentDirection==='asc'?' ↑':' ↓'):''}
                    </button>
                  </th>
                  <th>
                    <button className="analytics-sort-button" type="button" onClick={()=>setEquipmentSort('externalSharePercentage')}>
                      External share{equipmentSort==='externalSharePercentage'?(equipmentDirection==='asc'?' ↑':' ↓'):''}
                    </button>
                  </th>
                </tr>
              </thead>
              <tbody>
                {equipment.content.map(row=>(
                  <tr key={row.equipmentId}>
                    <td><strong>{row.equipmentName}</strong></td>
                    <td>{row.assetTag || '—'}</td>
                    <td>{row.providedMinutes}</td>
                    <td>{formatAmount(row.feesEarned)}</td>
                    <td>{Number(row.externalSharePercentage || 0).toFixed(2)}%</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pager page={equipmentPage} pages={equipment.totalPages || 0} label="Equipment" onChange={value=>setPage('equipmentPage',value)} />
      </section>
    </>}
  </main>
}
