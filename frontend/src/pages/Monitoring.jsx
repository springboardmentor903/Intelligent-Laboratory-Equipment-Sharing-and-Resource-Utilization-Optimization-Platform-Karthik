import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import api, { WEBSOCKET_URL } from '../api/axiosInstance'
import { connectEquipmentSocket } from '../access/equipmentSocket.js'
import {
  applyEquipmentStatusEvent,
  EQUIPMENT_STATUSES,
  formatElapsedUsage,
  formatEquipmentStatus,
  formatMonitoringTimestamp,
  getMonitoringErrorMessage,
  readLiveEquipmentPage,
} from '../access/monitoringModel.js'
const PAGE_SIZE = 12

export default function Monitoring() {
  const [equipment, setEquipment] = useState([])
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [pageInfo, setPageInfo] = useState({ totalElements: 0, totalPages: 0, first: true, last: true })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [loaded, setLoaded] = useState(false)
  const [now, setNow] = useState(null)
  const [connection, setConnection] = useState('Connecting')
  const searchTimer = useRef(null)
  const mounted = useRef(true)
  const equipmentRef = useRef([])
  const queryRef = useRef({ q: '', status: '', page: 0 })

  const loadEquipment = useCallback(async ({ showLoading = true } = {}) => {
    if (showLoading && mounted.current) setLoading(true)
    const query = queryRef.current
    try {
      const response = await api.get('/api/equipment/live-status', {
        params: { q: query.q || undefined, status: query.status || undefined, page: query.page, size: PAGE_SIZE },
      })
      const result = readLiveEquipmentPage(response.data)
      if (mounted.current) {
        setError('')
        setNow(Date.now())
        equipmentRef.current = result.content
        setEquipment(result.content)
        setPageInfo(result)
        setLoaded(true)
      }
    } catch (requestError) {
      if (mounted.current) setError(getMonitoringErrorMessage(requestError))
    } finally {
      if (mounted.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    mounted.current = true
    queryRef.current = { q: '', status: '', page: 0 }
    const initialLoad = window.setTimeout(() => {
      void loadEquipment()
    }, 0)
    return () => {
      mounted.current = false
      window.clearTimeout(initialLoad)
      if (searchTimer.current) window.clearTimeout(searchTimer.current)
      searchTimer.current = null
    }
  }, [loadEquipment])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    const socket = connectEquipmentSocket({
      url: WEBSOCKET_URL,
      token: localStorage.getItem('token'),
      onStateChange: setConnection,
      onReconnect: () => { void loadEquipment({ showLoading: false }) },
      onEquipmentUpdate: (message) => {
        const update = applyEquipmentStatusEvent(equipmentRef.current, message, queryRef.current)
        if (!update.affected) return
        equipmentRef.current = update.records
        setEquipment(update.records)
        if (update.removed) {
          setPageInfo(previous => ({ ...previous, totalElements: Math.max(0, previous.totalElements - 1) }))
        }
      },
    })
    return () => {
      socket.close()
    }
  }, [loadEquipment])

  function updateQuery(next) {
    const nextQuery = { ...queryRef.current, ...next, page: 0 }
    queryRef.current = nextQuery
    setPage(0)
    void loadEquipment()
  }
  function changeSearch(value) {
    setSearch(value)
    if (searchTimer.current) window.clearTimeout(searchTimer.current)
    searchTimer.current = window.setTimeout(() => updateQuery({ q: value.trim() }), 300)
  }
  function goPage(nextPage) {
    const next = { ...queryRef.current, page: nextPage }
    queryRef.current = next
    setPage(nextPage)
    void loadEquipment()
  }

  return (
    <section className="module-page monitoring-page">
      <header className="module-page-heading">
        <div><span className="eyebrow">EQUIPMENT OPERATIONS</span><h1>Live Equipment Monitoring</h1><p>Review equipment status and current usage details supplied by the live status API.</p></div>
        <button type="button" className="secondary-button" onClick={() => loadEquipment()} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
      </header>
      <p className="monitoring-connection" role="status">Live updates: <strong>{connection}</strong></p>
      <section className="monitoring-utilization-note" aria-label="Utilization rate">
        <div>
          <span className="eyebrow">UTILIZATION</span>
          <h2>Live Utilization Analytics Active</h2>
          <p>Real-time capacity tracking, 7×24 heatmap intensity, and peak usage patterns are available.</p>
        </div>
        <Link to="/utilization?tab=analytics" className="primary-button" style={{ textDecoration: 'none' }}>
          Open Analytics Dashboard
        </Link>
      </section>
      <section className="monitoring-panel">
        <div className="monitoring-toolbar">
          <label className="monitoring-search">Search equipment<input type="search" value={search} onChange={event => changeSearch(event.target.value)} placeholder="Name or asset tag" /></label>
          <label className="monitoring-status-filter">Equipment status<select value={statusFilter} onChange={event => { setStatusFilter(event.target.value); updateQuery({ status: event.target.value }) }}><option value="">All statuses</option>{EQUIPMENT_STATUSES.map(status => <option key={status} value={status}>{formatEquipmentStatus(status)}</option>)}</select></label>
        </div>
        {error && <div className="module-alert" role="alert"><span>{error}</span><button type="button" onClick={() => loadEquipment()} disabled={loading}>{loading ? 'Retrying…' : 'Retry'}</button></div>}
        {loading && !loaded ? <div className="module-state" role="status"><span className="loading-spinner" /><p>Loading equipment…</p></div>
          : error && !loaded ? <div className="module-state"><h2>Monitoring data unavailable</h2><p>Retry to request live equipment data again.</p></div>
            : equipment.length === 0 ? <div className="module-state"><h2>{loaded && pageInfo.totalElements ? 'No matching equipment' : 'No equipment available'}</h2><p>{pageInfo.totalElements ? 'Change your search or status filter.' : 'Equipment will appear here when it is available to your account.'}</p></div>
              : <>
                <p className="monitoring-result-count">Showing {equipment.length} of {pageInfo.totalElements} equipment records</p>
                <div className="monitoring-grid">{equipment.map(item => {
                  const status = String(item.status || 'UNKNOWN').toUpperCase()
                  return <article className="monitoring-card" key={item.id}>
                    <div className="monitoring-card-heading"><div><h2>{item.name || `Equipment #${item.id}`}</h2><p>Equipment ID: {item.id ?? 'Not provided'}</p></div><span className={`equipment-status-badge equipment-status-${status.toLowerCase()}`}>{formatEquipmentStatus(status)}</span></div>
                    <dl className="monitoring-details">
                      <div><dt>Asset tag</dt><dd>{item.assetTag || 'Not provided'}</dd></div><div><dt>Currently in use</dt><dd>{item.inUse ? 'Yes' : 'No'}</dd></div>
                      <div><dt>Usage started</dt><dd>{formatMonitoringTimestamp(item.currentUsageStartTime)}</dd></div><div><dt>Scheduled end</dt><dd>{formatMonitoringTimestamp(item.scheduledBookingEndTime)}</dd></div>
                      <div><dt>Elapsed usage</dt><dd>{item.inUse && item.currentUsageStartTime ? formatElapsedUsage(item.currentUsageStartTime, now) : 'Not provided'}</dd></div>
                      <div><dt>Next confirmed booking</dt><dd>{item.nextConfirmedBooking ? `${formatMonitoringTimestamp(item.nextConfirmedBooking.startTime)} – ${formatMonitoringTimestamp(item.nextConfirmedBooking.endTime)}` : 'None scheduled'}</dd></div>
                      {item.managerUsageDetails && <div><dt>Usage notes</dt><dd>{item.managerUsageDetails.remarks || item.managerUsageDetails.usageType || 'Not provided'}</dd></div>}
                    </dl>
                  </article>
                })}</div>
                <nav className="monitoring-pagination" aria-label="Equipment pages"><button type="button" onClick={() => goPage(page - 1)} disabled={pageInfo.first || loading}>Previous</button><span>Page {pageInfo.number + 1} of {Math.max(pageInfo.totalPages, 1)}</span><button type="button" onClick={() => goPage(page + 1)} disabled={pageInfo.last || loading}>Next</button></nav>
              </>}
      </section>
    </section>
  )
}
