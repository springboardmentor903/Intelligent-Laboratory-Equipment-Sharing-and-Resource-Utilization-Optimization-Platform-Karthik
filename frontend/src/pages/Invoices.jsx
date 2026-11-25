import { useState, useEffect, useCallback } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { formatAmount, formatSharedTime } from '../access/sharedFormatting.js'
import {
  INVOICE_STATUSES,
  INVOICE_TYPES,
  getInvoiceStatusBadge,
  validateInvoiceGeneration,
} from '../access/billingWorkflow.js'

export default function Invoices() {
  const { user } = useAuth()
  const isSysAdmin = user?.roles?.includes('SYSTEM_ADMIN')

  const [activeTab, setActiveTab] = useState('ISSUED') // 'ISSUED' or 'RECEIVED'
  const [institutionId, setInstitutionId] = useState('')
  const [institutions, setInstitutions] = useState([])
  const [departments, setDepartments] = useState([])

  // Financial summary
  const [summary, setSummary] = useState(null)
  const [loadingSummary, setLoadingSummary] = useState(true)

  // Invoices list state
  const [invoices, setInvoices] = useState([])
  const [loadingInvoices, setLoadingInvoices] = useState(true)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [totalElements, setTotalElements] = useState(0)
  const pageSize = 15

  // Filters
  const [statusFilter, setStatusFilter] = useState('')
  const [overdueOnly, setOverdueOnly] = useState(false)
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')

  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // Invoice Detail Drawer
  const [selectedInvoiceId, setSelectedInvoiceId] = useState(null)
  const [invoiceDetail, setInvoiceDetail] = useState(null)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [actionInProgress, setActionInProgress] = useState(false)

  // Generate Invoice Modal State
  const [genModalOpen, setGenModalOpen] = useState(false)
  const [genType, setGenType] = useState('INTER_INSTITUTION')
  const [genTargetId, setGenTargetId] = useState('')
  const [genFrom, setGenFrom] = useState('')
  const [genTo, setGenTo] = useState('')
  const [genDueDate, setGenDueDate] = useState(() => {
    const d = new Date()
    d.setDate(d.getDate() + 30)
    return d.toISOString().slice(0, 10)
  })
  const [genNotes, setGenNotes] = useState('')
  const [genPreview, setGenPreview] = useState(null)
  const [loadingPreview, setLoadingPreview] = useState(false)
  const [submittingInvoice, setSubmittingInvoice] = useState(false)
  const [genErrors, setGenErrors] = useState({})

  // Load institutions & departments
  useEffect(() => {
    api.get('/api/institutions')
      .then((res) => setInstitutions(Array.isArray(res.data) ? res.data : []))
      .catch(() => setInstitutions([]))

    api.get('/api/departments')
      .then((res) => setDepartments(Array.isArray(res.data) ? res.data : []))
      .catch(() => setDepartments([]))
  }, [])

  const fetchSummary = useCallback(async () => {
    setLoadingSummary(true)
    try {
      const params = {}
      if (institutionId) params.institutionId = institutionId
      const res = await api.get('/api/billing/summary', { params })
      setSummary(res.data)
    } catch (err) {
      console.error('Failed to load financial summary', err)
    } finally {
      setLoadingSummary(false)
    }
  }, [institutionId])

  const fetchInvoices = useCallback(async () => {
    setLoadingInvoices(true)
    setError('')
    try {
      const endpoint = activeTab === 'ISSUED' ? '/api/billing/invoices/issued' : '/api/billing/invoices/received'
      const params = {
        page,
        size: pageSize,
      }
      if (statusFilter) params.status = statusFilter
      if (overdueOnly) params.overdueOnly = true
      if (fromDate) params.from = fromDate
      if (toDate) params.to = toDate
      if (institutionId) params.institutionId = institutionId

      const res = await api.get(endpoint, { params })
      const data = res.data
      setInvoices(Array.isArray(data?.content) ? data.content : [])
      setTotalPages(data?.totalPages || 1)
      setTotalElements(data?.totalElements || 0)
    } catch (err) {
      console.error('Failed to load invoices', err)
      setError('Unable to load invoices.')
    } finally {
      setLoadingInvoices(false)
    }
  }, [activeTab, page, statusFilter, overdueOnly, fromDate, toDate, institutionId])

  useEffect(() => {
    fetchSummary()
    fetchInvoices()
  }, [fetchSummary, fetchInvoices])

  // Open invoice detail drawer
  const handleOpenDetail = async (id) => {
    setSelectedInvoiceId(id)
    setLoadingDetail(true)
    try {
      const res = await api.get(`/api/billing/invoices/${id}`)
      setInvoiceDetail(res.data)
    } catch (err) {
      console.error('Failed to load invoice details', err)
      setError('Unable to load invoice details.')
    } finally {
      setLoadingDetail(false)
    }
  }

  const handleCloseDetail = () => {
    setSelectedInvoiceId(null)
    setInvoiceDetail(null)
  }

  // Lifecycle actions
  const handleInvoiceAction = async (action) => {
    if (!selectedInvoiceId) return
    setActionInProgress(true)
    setError('')
    try {
      await api.post(`/api/billing/invoices/${selectedInvoiceId}/${action}`)
      setNotice(`Invoice successfully ${action === 'pay' ? 'marked as paid' : action + 'd'}.`)
      // Refresh current detail and list
      const res = await api.get(`/api/billing/invoices/${selectedInvoiceId}`)
      setInvoiceDetail(res.data)
      fetchInvoices()
      fetchSummary()
      setTimeout(() => setNotice(''), 4000)
    } catch (err) {
      console.error(`Failed to ${action} invoice`, err)
      setError(err?.response?.data?.message || `Failed to ${action} invoice.`)
    } finally {
      setActionInProgress(false)
    }
  }

  // Load preview when opening or changing generate invoice inputs
  const handleFetchPreview = useCallback(async (type, targetId, from, to) => {
    if (!targetId) {
      setGenPreview(null)
      return
    }
    setLoadingPreview(true)
    try {
      const params = {
        invoiceType: type,
        targetId,
      }
      if (from) params.from = from
      if (to) params.to = to
      if (institutionId) params.institutionId = institutionId

      const res = await api.get('/api/billing/invoices/generate/preview', { params })
      setGenPreview(res.data)
    } catch (err) {
      console.error('Failed to fetch invoice preview', err)
      setGenPreview(null)
    } finally {
      setLoadingPreview(false)
    }
  }, [institutionId])

  const handleOpenGenerateModal = () => {
    const defaultTarget = institutions[0]?.id ? String(institutions[0].id) : ''
    setGenType('INTER_INSTITUTION')
    setGenTargetId(defaultTarget)
    setGenFrom('')
    setGenTo('')
    setGenNotes('')
    setGenErrors({})
    setGenModalOpen(true)
    handleFetchPreview('INTER_INSTITUTION', defaultTarget, '', '')
  }

  const handleGenerateInvoice = async (e) => {
    e.preventDefault()
    const payload = {
      invoiceType: genType,
      clientInstitutionId: genType === 'INTER_INSTITUTION' ? Number(genTargetId) : null,
      departmentId: genType === 'DEPARTMENT_CHARGEBACK' ? Number(genTargetId) : null,
      dueDate: genDueDate,
      from: genFrom || null,
      to: genTo || null,
      notes: genNotes || null,
    }

    const { isValid, errors } = validateInvoiceGeneration(payload)
    if (!isValid) {
      setGenErrors(errors)
      return
    }

    setSubmittingInvoice(true)
    setError('')
    try {
      const res = await api.post('/api/billing/invoices/generate', payload)
      setNotice(`Draft invoice ${res.data?.invoiceNumber || ''} generated successfully.`)
      setGenModalOpen(false)
      fetchInvoices()
      fetchSummary()
      setTimeout(() => setNotice(''), 4000)
    } catch (err) {
      console.error('Failed to generate invoice', err)
      setError(err?.response?.data?.message || 'Failed to generate invoice.')
    } finally {
      setSubmittingInvoice(false)
    }
  }

  return (
    <div className="page-container" style={{ maxWidth: 1250, margin: '0 auto', padding: '24px 16px' }}>
      <header className="page-header" style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
          <div>
            <h1 style={{ fontSize: '1.75rem', fontWeight: 700, margin: 0 }}>Invoice Management</h1>
            <p className="muted-text" style={{ marginTop: 4, margin: 0, color: 'var(--text-muted, #64748b)' }}>
              Manage inter-institution sharing invoices and department chargebacks
            </p>
          </div>
          <button
            type="button"
            className="button-primary"
            onClick={handleOpenGenerateModal}
            style={{ display: 'flex', alignItems: 'center', gap: 8 }}
          >
            ➕ Generate Invoice
          </button>
        </div>
      </header>

      {notice && (
        <div className="alert alert-success" style={{ marginBottom: 20, padding: 12, borderRadius: 6, background: '#dcfce7', color: '#166534' }}>
          {notice}
        </div>
      )}

      {error && (
        <div className="alert alert-danger" style={{ marginBottom: 20, padding: 12, borderRadius: 6, background: '#fee2e2', color: '#991b1b' }}>
          {error}
        </div>
      )}

      {/* Financial Summary Cards */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))',
        gap: 16,
        marginBottom: 24,
      }}>
        <div style={{ background: 'var(--bg-card, #ffffff)', padding: 16, borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <small style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', fontWeight: 600, textTransform: 'uppercase' }}>
            Total Invoiced (Issued)
          </small>
          <div style={{ fontSize: '1.35rem', fontWeight: 700, marginTop: 4 }}>
            {formatAmount(summary?.totalInvoicedIssued || 0)}
          </div>
          <small style={{ color: '#64748b', fontSize: '0.75rem' }}>
            Received: {formatAmount(summary?.totalInvoicedReceived || 0)}
          </small>
        </div>

        <div style={{ background: 'var(--bg-card, #ffffff)', padding: 16, borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <small style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', fontWeight: 600, textTransform: 'uppercase' }}>
            Total Paid
          </small>
          <div style={{ fontSize: '1.35rem', fontWeight: 700, marginTop: 4, color: '#16a34a' }}>
            {formatAmount(summary?.totalPaidIssued || 0)}
          </div>
          <small style={{ color: '#16a34a', fontSize: '0.75rem' }}>
            Collected revenue
          </small>
        </div>

        <div style={{ background: 'var(--bg-card, #ffffff)', padding: 16, borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <small style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', fontWeight: 600, textTransform: 'uppercase' }}>
            Outstanding Balance
          </small>
          <div style={{ fontSize: '1.35rem', fontWeight: 700, marginTop: 4, color: '#0284c7' }}>
            {formatAmount(summary?.totalOutstandingIssued || 0)}
          </div>
          <small style={{ color: '#0284c7', fontSize: '0.75rem' }}>
            Awaiting settlement
          </small>
        </div>

        <div style={{ background: 'var(--bg-card, #ffffff)', padding: 16, borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <small style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', fontWeight: 600, textTransform: 'uppercase' }}>
            Overdue Balance
          </small>
          <div style={{ fontSize: '1.35rem', fontWeight: 700, marginTop: 4, color: '#dc2626' }}>
            {formatAmount(summary?.totalOverdueIssued || 0)}
          </div>
          <small style={{ color: '#dc2626', fontSize: '0.75rem' }}>
            Past due date
          </small>
        </div>

        <div style={{ background: 'var(--bg-card, #ffffff)', padding: 16, borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', boxShadow: '0 1px 3px rgba(0,0,0,0.04)' }}>
          <small style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', fontWeight: 600, textTransform: 'uppercase' }}>
            Un-invoiced Pending
          </small>
          <div style={{ fontSize: '1.35rem', fontWeight: 700, marginTop: 4, color: '#d97706' }}>
            {formatAmount(summary?.pendingUninvoicedChargesAmount || 0)}
          </div>
          <small style={{ color: '#d97706', fontSize: '0.75rem' }}>
            {summary?.pendingUninvoicedChargesCount || 0} pending charges
          </small>
        </div>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 8, borderBottom: '1px solid var(--border-color, #e2e8f0)', marginBottom: 20 }}>
        <button
          type="button"
          onClick={() => { setActiveTab('ISSUED'); setPage(0) }}
          style={{
            padding: '10px 18px',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            fontWeight: 600,
            fontSize: '0.95rem',
            borderBottom: activeTab === 'ISSUED' ? '2px solid var(--primary, #0284c7)' : '2px solid transparent',
            color: activeTab === 'ISSUED' ? 'var(--primary, #0284c7)' : 'var(--text-muted, #64748b)',
          }}
        >
          📤 Issued Invoices (Outgoing)
        </button>
        <button
          type="button"
          onClick={() => { setActiveTab('RECEIVED'); setPage(0) }}
          style={{
            padding: '10px 18px',
            border: 'none',
            background: 'none',
            cursor: 'pointer',
            fontWeight: 600,
            fontSize: '0.95rem',
            borderBottom: activeTab === 'RECEIVED' ? '2px solid var(--primary, #0284c7)' : '2px solid transparent',
            color: activeTab === 'RECEIVED' ? 'var(--primary, #0284c7)' : 'var(--text-muted, #64748b)',
          }}
        >
          📥 Received Invoices (Incoming)
        </button>
      </div>

      {/* Filters Toolbar */}
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
              onChange={(e) => { setInstitutionId(e.target.value); setPage(0) }}
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
            Status
          </label>
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}
            className="input-select"
            style={{ minWidth: 140, padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
          >
            <option value="">All Statuses</option>
            {INVOICE_STATUSES.map((st) => (
              <option key={st} value={st}>{st}</option>
            ))}
          </select>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', marginTop: 18, gap: 6 }}>
          <input
            type="checkbox"
            id="overdueOnly"
            checked={overdueOnly}
            onChange={(e) => { setOverdueOnly(e.target.checked); setPage(0) }}
          />
          <label htmlFor="overdueOnly" style={{ fontSize: '0.85rem', fontWeight: 600, cursor: 'pointer', color: overdueOnly ? '#dc2626' : 'inherit' }}>
            Overdue Only
          </label>
        </div>

        <div>
          <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, marginBottom: 4, color: 'var(--text-muted, #64748b)' }}>
            From Date
          </label>
          <input
            type="date"
            value={fromDate}
            onChange={(e) => { setFromDate(e.target.value); setPage(0) }}
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
            onChange={(e) => { setToDate(e.target.value); setPage(0) }}
            className="input-text"
            style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
          />
        </div>

        {(statusFilter || overdueOnly || fromDate || toDate || institutionId) && (
          <div style={{ alignSelf: 'flex-end', marginLeft: 'auto' }}>
            <button
              type="button"
              className="button-text"
              onClick={() => {
                setStatusFilter('')
                setOverdueOnly(false)
                setFromDate('')
                setToDate('')
                setInstitutionId('')
                setPage(0)
              }}
              style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)', cursor: 'pointer' }}
            >
              Reset Filters
            </button>
          </div>
        )}
      </div>

      {/* Invoices List Table */}
      {loadingInvoices ? (
        <div className="module-state" role="status" style={{ padding: 40, textAlign: 'center' }}>
          Loading invoices…
        </div>
      ) : invoices.length === 0 ? (
        <div className="empty-state" style={{ padding: 40, textAlign: 'center', background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)' }}>
          <p className="muted-text">No invoices found for the current selection.</p>
        </div>
      ) : (
        <div style={{ background: 'var(--bg-card, #ffffff)', borderRadius: 8, border: '1px solid var(--border-color, #e2e8f0)', overflow: 'hidden' }}>
          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)', background: 'var(--table-header-bg, #f8fafc)' }}>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Invoice #</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Type</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>
                    {activeTab === 'ISSUED' ? 'Client / Recipient' : 'Issuer'}
                  </th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Issue Date</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Due Date</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem' }}>Status</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'right' }}>Total Amount</th>
                  <th style={{ padding: '12px 16px', fontWeight: 600, fontSize: '0.85rem', textAlign: 'center' }}>Details</th>
                </tr>
              </thead>
              <tbody>
                {invoices.map((inv) => {
                  const statusBadge = getInvoiceStatusBadge(inv)

                  return (
                    <tr key={inv.id} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                      <td style={{ padding: '12px 16px', fontWeight: 700, color: 'var(--primary, #0284c7)' }}>
                        {inv.invoiceNumber}
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.85rem' }}>
                        {inv.invoiceType === 'INTER_INSTITUTION' ? 'Inter-Institution' : 'Dept Chargeback'}
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.9rem' }}>
                        {activeTab === 'ISSUED' ? (
                          inv.clientInstitutionName || (inv.departmentName ? `Dept: ${inv.departmentName}` : '—')
                        ) : (
                          inv.issuingInstitutionName || '—'
                        )}
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.85rem' }}>
                        {inv.issueDate || '—'}
                      </td>
                      <td style={{ padding: '12px 16px', fontSize: '0.85rem', color: statusBadge.isOverdue ? '#dc2626' : 'inherit', fontWeight: statusBadge.isOverdue ? 600 : 'normal' }}>
                        {inv.dueDate || '—'}
                      </td>
                      <td style={{ padding: '12px 16px' }}>
                        <span style={{
                          padding: '4px 8px',
                          borderRadius: 4,
                          fontSize: '0.75rem',
                          fontWeight: 600,
                          backgroundColor: statusBadge.bg,
                          color: statusBadge.color,
                        }}>
                          {statusBadge.label}
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'right', fontWeight: 700, fontSize: '0.95rem' }}>
                        {formatAmount(inv.totalAmount)}
                      </td>
                      <td style={{ padding: '12px 16px', textAlign: 'center' }}>
                        <button
                          type="button"
                          className="button-secondary"
                          onClick={() => handleOpenDetail(inv.id)}
                          style={{ padding: '4px 10px', fontSize: '0.8rem' }}
                        >
                          View
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px', borderTop: '1px solid var(--border-color, #e2e8f0)' }}>
              <button
                type="button"
                className="button-secondary"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </button>
              <span style={{ fontSize: '0.85rem', color: 'var(--text-muted, #64748b)' }}>
                Page {page + 1} of {totalPages} ({totalElements} invoices)
              </span>
              <button
                type="button"
                className="button-secondary"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          )}
        </div>
      )}

      {/* Slide-Over Drawer for Invoice Details */}
      {selectedInvoiceId && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.4)',
          display: 'flex',
          justifyContent: 'flex-end',
          zIndex: 1000,
        }}>
          <div style={{
            background: 'var(--bg-card, #ffffff)',
            width: '100%',
            maxWidth: 640,
            height: '100%',
            overflowY: 'auto',
            padding: 24,
            boxShadow: '-4px 0 20px rgba(0,0,0,0.15)',
            display: 'flex',
            flexDirection: 'column',
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20, borderBottom: '1px solid var(--border-color, #e2e8f0)', paddingBottom: 16 }}>
              <div>
                <h2 style={{ fontSize: '1.35rem', fontWeight: 700, margin: 0 }}>
                  Invoice {invoiceDetail?.invoice?.invoiceNumber || `#${selectedInvoiceId}`}
                </h2>
                <small style={{ color: 'var(--text-muted, #64748b)' }}>
                  {invoiceDetail?.invoice?.invoiceType === 'INTER_INSTITUTION' ? 'Inter-Institution Sharing Invoice' : 'Department Chargeback Invoice'}
                </small>
              </div>
              <button
                type="button"
                className="button-secondary"
                onClick={handleCloseDetail}
                style={{ padding: '4px 10px' }}
              >
                ✕ Close
              </button>
            </div>

            {loadingDetail ? (
              <div className="module-state" role="status" style={{ padding: 40, textAlign: 'center' }}>
                Loading invoice details…
              </div>
            ) : invoiceDetail?.invoice ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
                {/* Status & Action Banner */}
                <div style={{
                  padding: 16,
                  borderRadius: 8,
                  background: 'var(--table-header-bg, #f8fafc)',
                  border: '1px solid var(--border-color, #e2e8f0)',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  flexWrap: 'wrap',
                  gap: 12
                }}>
                  <div>
                    <span style={{ fontSize: '0.8rem', color: 'var(--text-muted, #64748b)', display: 'block', marginBottom: 4 }}>
                      Current Status
                    </span>
                    {(() => {
                      const badge = getInvoiceStatusBadge(invoiceDetail.invoice)
                      return (
                        <span style={{
                          padding: '4px 10px',
                          borderRadius: 4,
                          fontSize: '0.85rem',
                          fontWeight: 700,
                          backgroundColor: badge.bg,
                          color: badge.color,
                        }}>
                          {badge.label}
                        </span>
                      )
                    })()}
                  </div>

                  {/* Actions based on state */}
                  <div style={{ display: 'flex', gap: 8 }}>
                    {invoiceDetail.invoice.status === 'DRAFT' && (
                      <>
                        <button
                          type="button"
                          className="button-primary"
                          onClick={() => handleInvoiceAction('issue')}
                          disabled={actionInProgress}
                          style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                        >
                          {actionInProgress ? 'Issuing…' : '📤 Issue Invoice'}
                        </button>
                        <button
                          type="button"
                          className="button-secondary"
                          onClick={() => handleInvoiceAction('cancel')}
                          disabled={actionInProgress}
                          style={{ padding: '6px 14px', fontSize: '0.85rem', color: '#dc2626' }}
                        >
                          Cancel
                        </button>
                      </>
                    )}

                    {invoiceDetail.invoice.status === 'ISSUED' && (
                      <>
                        <button
                          type="button"
                          className="button-primary"
                          onClick={() => handleInvoiceAction('pay')}
                          disabled={actionInProgress}
                          style={{ padding: '6px 14px', fontSize: '0.85rem', backgroundColor: '#16a34a' }}
                        >
                          {actionInProgress ? 'Processing…' : '💳 Mark as Paid'}
                        </button>
                        <button
                          type="button"
                          className="button-secondary"
                          onClick={() => handleInvoiceAction('cancel')}
                          disabled={actionInProgress}
                          style={{ padding: '6px 14px', fontSize: '0.85rem', color: '#dc2626' }}
                        >
                          Cancel
                        </button>
                      </>
                    )}
                  </div>
                </div>

                {/* Metadata Grid */}
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, fontSize: '0.9rem' }}>
                  <div>
                    <span style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', display: 'block' }}>Issuing Institution</span>
                    <strong>{invoiceDetail.invoice.issuingInstitutionName || '—'}</strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', display: 'block' }}>Recipient</span>
                    <strong>
                      {invoiceDetail.invoice.clientInstitutionName || invoiceDetail.invoice.departmentName || '—'}
                    </strong>
                  </div>
                  <div>
                    <span style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', display: 'block' }}>Issue Date</span>
                    <span>{invoiceDetail.invoice.issueDate || '—'}</span>
                  </div>
                  <div>
                    <span style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', display: 'block' }}>Due Date</span>
                    <span>{invoiceDetail.invoice.dueDate || '—'}</span>
                  </div>
                  {invoiceDetail.invoice.notes && (
                    <div style={{ gridColumn: '1 / -1' }}>
                      <span style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.75rem', display: 'block' }}>Notes</span>
                      <p style={{ margin: 0, fontStyle: 'italic' }}>{invoiceDetail.invoice.notes}</p>
                    </div>
                  )}
                </div>

                {/* Itemized Charges Table */}
                <div>
                  <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: 8 }}>
                    Itemized Booking Charges ({invoiceDetail.items?.length || 0})
                  </h3>
                  <div style={{ border: '1px solid var(--border-color, #e2e8f0)', borderRadius: 6, overflow: 'hidden' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.85rem' }}>
                      <thead>
                        <tr style={{ background: 'var(--table-header-bg, #f8fafc)', borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                          <th style={{ padding: '8px 12px', textAlign: 'left' }}>Equipment</th>
                          <th style={{ padding: '8px 12px', textAlign: 'left' }}>Duration</th>
                          <th style={{ padding: '8px 12px', textAlign: 'right' }}>Rate</th>
                          <th style={{ padding: '8px 12px', textAlign: 'right' }}>Amount</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(invoiceDetail.items || []).map((item) => (
                          <tr key={item.id} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                            <td style={{ padding: '8px 12px' }}>
                              <div style={{ fontWeight: 600 }}>{item.equipmentName || 'Equipment'}</div>
                              <small style={{ color: 'var(--text-muted, #64748b)' }}>Charge #{item.chargeId}</small>
                            </td>
                            <td style={{ padding: '8px 12px' }}>
                              {item.usageMinutes}m ({Number(item.usageHours || 0).toFixed(2)}h)
                            </td>
                            <td style={{ padding: '8px 12px', textAlign: 'right' }}>
                              {formatAmount(item.hourlyRateSnapshot)}/h
                            </td>
                            <td style={{ padding: '8px 12px', textAlign: 'right', fontWeight: 600 }}>
                              {formatAmount(item.amount)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Financial Summary Calculation Breakdown */}
                <div style={{
                  background: 'var(--table-header-bg, #f8fafc)',
                  padding: 16,
                  borderRadius: 8,
                  border: '1px solid var(--border-color, #e2e8f0)',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8,
                  fontSize: '0.9rem'
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: 'var(--text-muted, #64748b)' }}>Subtotal</span>
                    <span>{formatAmount(invoiceDetail.invoice.subtotal)}</span>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <span style={{ color: 'var(--text-muted, #64748b)' }}>
                      Tax ({Number(invoiceDetail.invoice.taxRate || 0).toFixed(0)}%)
                    </span>
                    <span>{formatAmount(invoiceDetail.invoice.taxAmount)}</span>
                  </div>
                  <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    fontWeight: 700,
                    fontSize: '1.1rem',
                    borderTop: '1px solid var(--border-color, #cbd5e1)',
                    paddingTop: 8
                  }}>
                    <span>Total Amount</span>
                    <span style={{ color: 'var(--primary, #0284c7)' }}>
                      {formatAmount(invoiceDetail.invoice.totalAmount)}
                    </span>
                  </div>
                </div>
              </div>
            ) : null}
          </div>
        </div>
      )}

      {/* Generate Invoice Modal */}
      {genModalOpen && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000,
          padding: 16
        }}>
          <div style={{
            background: 'var(--bg-card, #ffffff)',
            borderRadius: 8,
            padding: 24,
            maxWidth: 520,
            width: '100%',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            maxHeight: '90vh',
            overflowY: 'auto'
          }}>
            <h2 style={{ fontSize: '1.25rem', fontWeight: 700, margin: '0 0 16px 0' }}>
              Generate Draft Invoice
            </h2>

            <form onSubmit={handleGenerateInvoice}>
              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 4 }}>
                  Invoice Type
                </label>
                <select
                  value={genType}
                  onChange={(e) => {
                    const newType = e.target.value
                    setGenType(newType)
                    const target = newType === 'INTER_INSTITUTION'
                      ? (institutions[0]?.id ? String(institutions[0].id) : '')
                      : (departments[0]?.id ? String(departments[0].id) : '')
                    setGenTargetId(target)
                    handleFetchPreview(newType, target, genFrom, genTo)
                  }}
                  className="input-select"
                  style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
                >
                  <option value="INTER_INSTITUTION">Inter-Institution Sharing (18% Tax)</option>
                  <option value="DEPARTMENT_CHARGEBACK">Department Chargeback (0% Tax)</option>
                </select>
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 4 }}>
                  {genType === 'INTER_INSTITUTION' ? 'Client Institution' : 'Target Department'}
                </label>
                <select
                  value={genTargetId}
                  onChange={(e) => {
                    const newTarget = e.target.value
                    setGenTargetId(newTarget)
                    handleFetchPreview(genType, newTarget, genFrom, genTo)
                  }}
                  className="input-select"
                  style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
                >
                  {genType === 'INTER_INSTITUTION' ? (
                    institutions.map((i) => (
                      <option key={i.id} value={i.id}>{i.name}</option>
                    ))
                  ) : (
                    departments.map((d) => (
                      <option key={d.id} value={d.id}>{d.name}</option>
                    ))
                  )}
                </select>
                {genErrors.clientInstitutionId && (
                  <small style={{ color: '#dc2626' }}>{genErrors.clientInstitutionId}</small>
                )}
                {genErrors.departmentId && (
                  <small style={{ color: '#dc2626' }}>{genErrors.departmentId}</small>
                )}
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 14 }}>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: 4 }}>
                    From Date (Optional)
                  </label>
                  <input
                    type="date"
                    value={genFrom}
                    onChange={(e) => {
                      setGenFrom(e.target.value)
                      handleFetchPreview(genType, genTargetId, e.target.value, genTo)
                    }}
                    className="input-text"
                    style={{ width: '100%', padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: 4 }}>
                    To Date (Optional)
                  </label>
                  <input
                    type="date"
                    value={genTo}
                    onChange={(e) => {
                      setGenTo(e.target.value)
                      handleFetchPreview(genType, genTargetId, genFrom, e.target.value)
                    }}
                    className="input-text"
                    style={{ width: '100%', padding: '6px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
                  />
                </div>
              </div>

              <div style={{ marginBottom: 14 }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 4 }}>
                  Due Date
                </label>
                <input
                  type="date"
                  value={genDueDate}
                  onChange={(e) => setGenDueDate(e.target.value)}
                  className="input-text"
                  style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
                />
                {genErrors.dueDate && (
                  <small style={{ color: '#dc2626' }}>{genErrors.dueDate}</small>
                )}
              </div>

              <div style={{ marginBottom: 16 }}>
                <label style={{ display: 'block', fontSize: '0.85rem', fontWeight: 600, marginBottom: 4 }}>
                  Notes (Optional)
                </label>
                <textarea
                  rows={2}
                  value={genNotes}
                  onChange={(e) => setGenNotes(e.target.value)}
                  placeholder="e.g. Q4 Inter-institution sharing settlement"
                  className="input-textarea"
                  style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid var(--border-color, #cbd5e1)' }}
                />
              </div>

              {/* Preview Box */}
              <div style={{
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: 6,
                padding: 12,
                marginBottom: 20
              }}>
                <div style={{ fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-muted, #64748b)', marginBottom: 6 }}>
                  Invoice Generation Preview
                </div>
                {loadingPreview ? (
                  <div style={{ fontSize: '0.85rem', color: '#64748b' }}>Calculating pending charges…</div>
                ) : genPreview ? (
                  <div style={{ fontSize: '0.85rem', display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span>Pending Un-invoiced Charges:</span>
                      <strong>{genPreview.pendingChargesCount || 0}</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span>Subtotal:</span>
                      <span>{formatAmount(genPreview.subtotal || 0)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span>Tax ({genPreview.taxRate || 0}%):</span>
                      <span>{formatAmount(genPreview.taxAmount || 0)}</span>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: '1px solid #e2e8f0', paddingTop: 4, fontWeight: 700 }}>
                      <span>Estimated Total:</span>
                      <span style={{ color: '#0284c7' }}>{formatAmount(genPreview.totalAmount || 0)}</span>
                    </div>
                  </div>
                ) : (
                  <div style={{ fontSize: '0.85rem', color: '#64748b' }}>Select target to calculate preview.</div>
                )}
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 12 }}>
                <button
                  type="button"
                  className="button-secondary"
                  onClick={() => setGenModalOpen(false)}
                  disabled={submittingInvoice}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="button-primary"
                  disabled={submittingInvoice || !genPreview || Number(genPreview.pendingChargesCount || 0) === 0}
                >
                  {submittingInvoice ? 'Generating…' : 'Generate Invoice'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
