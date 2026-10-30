import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import {
  buildEquipmentSearchParams,
  EQUIPMENT_STATUSES,
  getEquipmentPermissions,
  validateCalibrationForm,
  validateCertificationForm,
  validateEquipmentForm,
} from '../access/equipmentWorkflow.js'

const blankEquipment = (institutionId = '') => ({
  name: '', category: '', description: '', institutionId: institutionId ?? '',
  departmentId: '',
  labLocation: '', quantity: '1', status: 'AVAILABLE',
  specificationsText: '{}', tags: '',
})
const blankCalibration = { calibrationDate: '', nextDueDate: '', result: 'PASS', notes: '' }
const blankCertification = { certificateType: '', issuingOrganization: '', issueDate: '', expiryDate: '', documentReference: '' }

function errorMessage(error, fallback) {
  return error.response?.data?.message || error.response?.data?.detail || error.response?.data?.error || error.message || fallback
}

function asDisplayJson(value) {
  if (value == null) return '{}'
  try { return JSON.stringify(value, null, 2) } catch { return '{}' }
}

export default function Equipment() {
  const { user } = useAuth()
  const permissions = useMemo(() => getEquipmentPermissions(user?.roles), [user?.roles])
  const [equipment, setEquipment] = useState([])
  const [departments, setDepartments] = useState([])
  const [institutions, setInstitutions] = useState([])
  const [filters, setFilters] = useState({ name: '', category: '', description: '', labLocation: '', status: '', tag: '', institutionId: '', departmentId: '' })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let active = true
    Promise.allSettled([
      api.get('/api/departments'),
      api.get('/api/institutions'),
    ]).then(([deptResult, instResult]) => {
      if (!active) return
      if (deptResult.status === 'fulfilled' && Array.isArray(deptResult.value.data)) {
        setDepartments(deptResult.value.data)
      }
      if (instResult.status === 'fulfilled' && Array.isArray(instResult.value.data)) {
        setInstitutions(instResult.value.data)
      }
    })
    return () => { active = false }
  }, [])
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [editingId, setEditingId] = useState(null)
  const [showEquipmentForm, setShowEquipmentForm] = useState(false)
  const [equipmentForm, setEquipmentForm] = useState(blankEquipment(user?.institutionId))
  const [equipmentErrors, setEquipmentErrors] = useState([])
  const [equipmentSubmitting, setEquipmentSubmitting] = useState(false)
  const [deletingId, setDeletingId] = useState(null)
  const [selectedId, setSelectedId] = useState(null)
  const [calibrations, setCalibrations] = useState([])
  const [certifications, setCertifications] = useState([])
  const [recordsLoading, setRecordsLoading] = useState(false)
  const [recordsError, setRecordsError] = useState('')
  const [calibrationForm, setCalibrationForm] = useState(blankCalibration)
  const [certificationForm, setCertificationForm] = useState(blankCertification)
  const [recordErrors, setRecordErrors] = useState([])
  const [recordSubmitting, setRecordSubmitting] = useState(false)
  const [showCalibrationForm, setShowCalibrationForm] = useState(false)
  const [showCertificationForm, setShowCertificationForm] = useState(false)
  const equipmentMutationLock = useRef(false)
  const recordMutationLock = useRef(false)
  const deleteLock = useRef(false)
  const equipmentRequestId = useRef(0)
  const recordsRequestId = useRef(0)
  const equipmentRequests = useRef(new Map())
  const selectedIdRef = useRef(null)
  const scopedSearchFilters = {
    ...filters,
    institutionId: permissions.canFilterInstitution ? filters.institutionId : (user?.institutionId ?? ''),
  }

  const fetchEquipmentData = useCallback((requestedFilters = {}) => {
    const params = buildEquipmentSearchParams(requestedFilters)
    const key = JSON.stringify(params)
    const currentRequest = equipmentRequests.current.get(key)
    if (currentRequest) return currentRequest

    const request = api.get('/api/equipment', { params })
      .then((response) => Array.isArray(response.data) ? response.data : [])
    equipmentRequests.current.set(key, request)
    request.then(
      () => { if (equipmentRequests.current.get(key) === request) equipmentRequests.current.delete(key) },
      () => { if (equipmentRequests.current.get(key) === request) equipmentRequests.current.delete(key) },
    )
    return request
  }, [])

  const applyEquipmentData = useCallback((data) => {
    setEquipment(data)
    const currentId = selectedIdRef.current
    if (currentId != null && !data.some((item) => String(item.id) === String(currentId))) {
      selectedIdRef.current = null
      recordsRequestId.current += 1
      setSelectedId(null)
      setCalibrations([])
      setCertifications([])
      setRecordsError('')
      setRecordsLoading(false)
      setShowCalibrationForm(false)
      setShowCertificationForm(false)
    }
  }, [])

  function selectEquipment(id) {
    selectedIdRef.current = id
    setSelectedId(id)
    setShowCalibrationForm(false)
    setShowCertificationForm(false)
    setRecordErrors([])
    loadRecords(id)
  }

  function closeSelectedEquipment() {
    selectedIdRef.current = null
    recordsRequestId.current += 1
    setSelectedId(null)
    setCalibrations([])
    setCertifications([])
    setRecordsError('')
    setRecordsLoading(false)
    setShowCalibrationForm(false)
    setShowCertificationForm(false)
  }

  const loadEquipment = useCallback(async (requestedFilters = {}) => {
    const requestId = ++equipmentRequestId.current
    setLoading(true)
    setError('')
    try {
      const data = await fetchEquipmentData(requestedFilters)
      if (requestId === equipmentRequestId.current) applyEquipmentData(data)
    } catch (err) {
      if (requestId === equipmentRequestId.current) {
        setError(errorMessage(err, 'Unable to load equipment. Please try again.'))
      }
    } finally {
      if (requestId === equipmentRequestId.current) setLoading(false)
    }
  }, [applyEquipmentData, fetchEquipmentData])

  useEffect(() => {
    const requestId = ++equipmentRequestId.current
    let active = true
    const initialFilters = {
      institutionId: permissions.canFilterInstitution ? '' : (user?.institutionId ?? ''),
    }
    fetchEquipmentData(initialFilters)
      .then((data) => {
        if (active && requestId === equipmentRequestId.current) {
          applyEquipmentData(data)
          setError('')
        }
      })
      .catch((err) => {
        if (active && requestId === equipmentRequestId.current) {
          setError(errorMessage(err, 'Unable to load equipment. Please try again.'))
        }
      })
      .finally(() => {
        if (active && requestId === equipmentRequestId.current) setLoading(false)
      })
    return () => { active = false }
  }, [applyEquipmentData, fetchEquipmentData, permissions.canFilterInstitution, user?.institutionId])

  useEffect(() => () => {
    equipmentRequestId.current += 1
    recordsRequestId.current += 1
  }, [])

  const loadRecords = useCallback(async (equipmentId) => {
    if (!equipmentId) return
    const requestId = ++recordsRequestId.current
    setRecordsLoading(true)
    setRecordsError('')
    const [calibrationResult, certificationResult] = await Promise.allSettled([
      api.get(`/api/equipment/${equipmentId}/calibrations`),
      api.get(`/api/equipment/${equipmentId}/certifications`),
    ])
    if (requestId !== recordsRequestId.current) return
    const errors = []
    if (calibrationResult.status === 'fulfilled') {
      setCalibrations(Array.isArray(calibrationResult.value.data) ? calibrationResult.value.data : [])
    } else {
      setCalibrations([])
      errors.push(`Calibration: ${errorMessage(calibrationResult.reason, 'Unable to load calibration history.')}`)
    }
    if (certificationResult.status === 'fulfilled') {
      setCertifications(Array.isArray(certificationResult.value.data) ? certificationResult.value.data : [])
    } else {
      setCertifications([])
      errors.push(`Certification: ${errorMessage(certificationResult.reason, 'Unable to load certification history.')}`)
    }
    setRecordsError(errors.join(' '))
    setRecordsLoading(false)
  }, [])

  const availableStatuses = useMemo(() => [...new Set(
    equipment.map((item) => String(item.status ?? '').toUpperCase()).filter(Boolean),
  )].sort(), [equipment])

  const currentInstitutionName = useMemo(() => {
    const instId = user?.institutionId
    if (!instId) return '—'
    const found = institutions.find((i) => String(i.id) === String(instId))
    return found ? `${found.name} (#${instId})` : `Institution #${instId}`
  }, [institutions, user?.institutionId])

  const activeScopeName = useMemo(() => {
    const targetId = permissions.canFilterInstitution
      ? (filters.institutionId ? String(filters.institutionId).trim() : null)
      : user?.institutionId
    if (!targetId) {
      return permissions.canFilterInstitution ? 'all institutions' : 'your institution'
    }
    const found = institutions.find((i) => String(i.id) === String(targetId))
    return found ? `${found.name} (#${targetId})` : `Institution #${targetId}`
  }, [filters.institutionId, institutions, permissions.canFilterInstitution, user?.institutionId])

  function changeFilter(event) {
    setFilters((current) => ({ ...current, [event.target.name]: event.target.value }))
  }

  function handleSearch(event) {
    event.preventDefault()
    if (loading) return
    setSuccess('')
    loadEquipment(scopedSearchFilters)
  }

  function beginCreate() {
    setEditingId(null)
    setEquipmentForm(blankEquipment(user?.institutionId))
    setEquipmentErrors([])
    setShowEquipmentForm(true)
  }

  function beginEdit(item) {
    setEditingId(item.id)
    setEquipmentForm({
      name: item.name ?? '', category: item.category ?? '', description: item.description ?? '',
      institutionId: item.institutionId ?? '', departmentId: item.departmentId ?? '', labLocation: item.labLocation ?? '',
      quantity: String(item.quantity ?? 1), status: item.status ?? '',
      specificationsText: asDisplayJson(item.specifications),
      tags: Array.isArray(item.tags) ? item.tags.join(', ') : '',
    })
    setEquipmentErrors([])
    setShowEquipmentForm(true)
  }

  async function submitEquipment(event) {
    event.preventDefault()
    if (equipmentMutationLock.current) return
    const { errors, body } = validateEquipmentForm(equipmentForm)
    setEquipmentErrors(errors)
    if (errors.length) return
    equipmentMutationLock.current = true
    setEquipmentSubmitting(true)
    setError('')
    setSuccess('')
    try {
      if (editingId != null) await api.put(`/api/equipment/${editingId}`, body)
      else await api.post('/api/equipment', body)
      equipmentRequests.current.clear()
      setSuccess(editingId != null ? 'Equipment updated.' : 'Equipment created.')
      setShowEquipmentForm(false)
      setEditingId(null)
      await loadEquipment(scopedSearchFilters)
    } catch (err) {
      setError(errorMessage(err, 'Unable to save equipment.'))
    } finally {
      equipmentMutationLock.current = false
      setEquipmentSubmitting(false)
    }
  }

  async function deleteEquipment(item) {
    if (deleteLock.current || deletingId != null) return
    if (!window.confirm(`Delete ${item.name || `equipment #${item.id}`}? This cannot be undone.`)) return
    deleteLock.current = true
    setDeletingId(item.id)
    setError('')
    setSuccess('')
    try {
      await api.delete(`/api/equipment/${item.id}`)
      equipmentRequests.current.clear()
      if (String(selectedIdRef.current) === String(item.id)) closeSelectedEquipment()
      setSuccess('Equipment deleted.')
      await loadEquipment(scopedSearchFilters)
    } catch (err) {
      setError(errorMessage(err, 'Unable to delete equipment.'))
    } finally {
      deleteLock.current = false
      setDeletingId(null)
    }
  }

  async function submitCalibration(event) {
    event.preventDefault()
    if (recordMutationLock.current) return
    const { errors, body } = validateCalibrationForm(calibrationForm)
    setRecordErrors(errors)
    if (errors.length || !selectedId) return
    const equipmentId = selectedId
    recordMutationLock.current = true
    setRecordSubmitting(true)
    setRecordsError('')
    setError('')
    setSuccess('')
    try {
      await api.post(`/api/equipment/${equipmentId}/calibrations`, body)
      equipmentRequests.current.clear()
      setSuccess('Calibration record created.')
      if (String(selectedIdRef.current) === String(equipmentId)) {
        setCalibrationForm(blankCalibration)
        setShowCalibrationForm(false)
        await loadRecords(equipmentId)
      }
      await loadEquipment(scopedSearchFilters)
    } catch (err) {
      if (String(selectedIdRef.current) === String(equipmentId)) {
        setRecordsError(errorMessage(err, 'Unable to create the calibration record.'))
      }
    } finally {
      recordMutationLock.current = false
      setRecordSubmitting(false)
    }
  }

  async function submitCertification(event) {
    event.preventDefault()
    if (recordMutationLock.current) return
    const { errors, body } = validateCertificationForm(certificationForm)
    setRecordErrors(errors)
    if (errors.length || !selectedId) return
    const equipmentId = selectedId
    recordMutationLock.current = true
    setRecordSubmitting(true)
    setRecordsError('')
    setError('')
    setSuccess('')
    try {
      await api.post(`/api/equipment/${equipmentId}/certifications`, body)
      equipmentRequests.current.clear()
      setSuccess('Certification record created.')
      if (String(selectedIdRef.current) === String(equipmentId)) {
        setCertificationForm(blankCertification)
        setShowCertificationForm(false)
        await loadRecords(equipmentId)
      }
      await loadEquipment(scopedSearchFilters)
    } catch (err) {
      if (String(selectedIdRef.current) === String(equipmentId)) {
        setRecordsError(errorMessage(err, 'Unable to create the certification record.'))
      }
    } finally {
      recordMutationLock.current = false
      setRecordSubmitting(false)
    }
  }

  const selectedEquipment = equipment.find((item) => item.id === selectedId)

  return (
    <div className="module-page">
      <section className="module-page-heading">
        <div>
          <span className="eyebrow">RESOURCE MANAGEMENT</span>
          <h1>Equipment</h1>
          <p>Browse, search, and manage laboratory equipment and records.</p>
        </div>
        <div className="module-count"><strong>{equipment.length}</strong><span>Matching records</span></div>
      </section>

      {success && <div className="auth-success" role="status">{success}</div>}
      {error && <div className="module-alert" role="alert"><span>{error}</span><button type="button" onClick={() => loadEquipment(scopedSearchFilters)} disabled={loading}>{loading ? 'Retrying…' : 'Retry'}</button></div>}

      <form className="booking-form-card" onSubmit={handleSearch}>
        <div className="module-section-title"><div><h2>Search inventory</h2><p>Filters are sent to the backend equipment search.</p></div></div>
        <div className="booking-time-grid">
          <div className="form-field"><label htmlFor="equipment-name-filter">Name</label><input id="equipment-name-filter" name="name" value={filters.name} onChange={changeFilter} /></div>
          <div className="form-field"><label htmlFor="equipment-category-filter">Category</label><input id="equipment-category-filter" name="category" value={filters.category} onChange={changeFilter} /></div>
          <div className="form-field"><label htmlFor="equipment-description-filter">Description</label><input id="equipment-description-filter" name="description" value={filters.description} onChange={changeFilter} /></div>
          <div className="form-field"><label htmlFor="equipment-tag-filter">Tag</label><input id="equipment-tag-filter" name="tag" value={filters.tag} onChange={changeFilter} /></div>
          <div className="form-field"><label htmlFor="equipment-location-filter">Lab location</label><input id="equipment-location-filter" name="labLocation" value={filters.labLocation} onChange={changeFilter} /></div>
          <div className="form-field"><label htmlFor="equipment-status-filter">Status</label><input id="equipment-status-filter" list="equipment-status-options" name="status" value={filters.status} onChange={changeFilter} placeholder="Any status" /><datalist id="equipment-status-options">{availableStatuses.map((status) => <option value={status} key={status} />)}</datalist></div>
          <div className="form-field"><label htmlFor="equipment-department-filter">Department</label><select id="equipment-department-filter" name="departmentId" value={filters.departmentId} onChange={changeFilter}><option value="">All departments</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
          <div className="form-field">
            <label htmlFor="equipment-institution-filter">Institution ID</label>
            <input id="equipment-institution-filter" name="institutionId" type="number" min="1" value={permissions.canFilterInstitution ? filters.institutionId : (user?.institutionId ?? '')} onChange={changeFilter} readOnly={!permissions.canFilterInstitution} title={currentInstitutionName} />
            {permissions.canFilterInstitution ? (
              <small style={{ display: 'block', marginTop: '0.25rem', color: 'var(--text-muted, #64748b)' }}>
                {filters.institutionId ? activeScopeName : 'All institutions (unfiltered)'}
              </small>
            ) : (
              <small style={{ display: 'block', marginTop: '0.25rem', color: 'var(--text-muted, #64748b)' }}>
                {currentInstitutionName}
              </small>
            )}
          </div>
        </div>
        <button className="button button-primary" type="submit">{loading ? 'Searching…' : 'Search equipment'}</button>
        {permissions.canCreate && <button className="secondary-button" type="button" disabled={equipmentSubmitting || deletingId != null} onClick={beginCreate}>Add equipment</button>}
      </form>

      {showEquipmentForm && (
        <section className="booking-form-card">
          <div className="module-section-title"><div><h2>{editingId == null ? 'Add equipment' : `Edit equipment #${editingId}`}</h2><p>Enter fields supported by the equipment API.</p></div></div>
          {equipmentErrors.length > 0 && <div className="module-alert" role="alert"><ul>{equipmentErrors.map((message) => <li key={message}>{message}</li>)}</ul></div>}
          <form className="booking-form" onSubmit={submitEquipment}>
            <div className="booking-time-grid">
              <div className="form-field"><label htmlFor="equipment-name">Name *</label><input id="equipment-name" maxLength={150} value={equipmentForm.name} onChange={(e) => setEquipmentForm({ ...equipmentForm, name: e.target.value })} required /></div>
              <div className="form-field"><label htmlFor="equipment-category">Category</label><input id="equipment-category" maxLength={100} value={equipmentForm.category} onChange={(e) => setEquipmentForm({ ...equipmentForm, category: e.target.value })} /></div>
              <div className="form-field"><label htmlFor="equipment-institution">Institution ID *</label><input id="equipment-institution" type="number" min="1" value={equipmentForm.institutionId} onChange={(e) => setEquipmentForm({ ...equipmentForm, institutionId: e.target.value })} required /></div>
              <div className="form-field"><label htmlFor="equipment-quantity">Quantity *</label><input id="equipment-quantity" type="number" min="1" step="1" value={equipmentForm.quantity} onChange={(e) => setEquipmentForm({ ...equipmentForm, quantity: e.target.value })} required /></div>
              <div className="form-field"><label htmlFor="equipment-location">Lab location</label><input id="equipment-location" maxLength={150} value={equipmentForm.labLocation} onChange={(e) => setEquipmentForm({ ...equipmentForm, labLocation: e.target.value })} /></div>
              <div className="form-field"><label htmlFor="equipment-department">Department</label><select id="equipment-department" value={equipmentForm.departmentId || ''} onChange={(e) => setEquipmentForm({ ...equipmentForm, departmentId: e.target.value })}><option value="">None / General</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
              <div className="form-field"><label htmlFor="equipment-status">Status</label><select id="equipment-status" value={equipmentForm.status || 'AVAILABLE'} onChange={(e) => setEquipmentForm({ ...equipmentForm, status: e.target.value })}>{EQUIPMENT_STATUSES.map((status) => <option value={status} key={status}>{status.replaceAll('_', ' ')}</option>)}</select></div>
            </div>
            <div className="form-field"><label htmlFor="equipment-description">Description</label><textarea id="equipment-description" maxLength={500} rows={3} value={equipmentForm.description} onChange={(e) => setEquipmentForm({ ...equipmentForm, description: e.target.value })} /></div>
            <div className="form-field"><label htmlFor="equipment-tags">Tags (comma-separated, up to 30)</label><input id="equipment-tags" placeholder="microscopy, imaging" value={equipmentForm.tags} onChange={(e) => setEquipmentForm({ ...equipmentForm, tags: e.target.value })} /></div>
            <div className="form-field"><label htmlFor="equipment-specifications">Specifications (JSON object)</label><textarea id="equipment-specifications" rows={5} value={equipmentForm.specificationsText} onChange={(e) => setEquipmentForm({ ...equipmentForm, specificationsText: e.target.value })} /></div>
            <button className="button button-primary" type="submit" disabled={equipmentSubmitting || deletingId != null}>{equipmentSubmitting ? 'Saving…' : 'Save equipment'}</button>{' '}
            <button className="secondary-button" type="button" disabled={equipmentSubmitting} onClick={() => setShowEquipmentForm(false)}>Close</button>
          </form>
        </section>
      )}

      {loading ? <div className="module-state" role="status"><span className="loading-spinner" /><p>Loading equipment…</p></div>
        : error ? null
          : equipment.length === 0 ? <div className="module-state"><h2>No equipment found</h2><p>There are no records matching these filters in {activeScopeName}.</p></div>
            : <>
              <p className="module-results-count">Showing {equipment.length} equipment records</p>
              <div className="equipment-grid">
                {equipment.map((item) => {
                  const status = String(item.status ?? 'UNKNOWN').toUpperCase()
                  const tags = Array.isArray(item.tags) ? item.tags : []
                  return <article className="equipment-card" key={item.id}>
                    <div className="equipment-card-top"><div className="equipment-icon" aria-hidden="true">🔬</div><span className={`equipment-status status-badge-${status.toLowerCase()}`}>{status.replaceAll('_', ' ')}</span></div>
                    <span className="equipment-category">{item.category || 'Uncategorized'}</span>
                    <h2>{item.name || `Equipment #${item.id}`}</h2>
                    <p className="equipment-description">{item.description || 'No description provided.'}</p>
                    <div className="equipment-details">
                      <div><span>Location</span><strong>{item.labLocation || 'Not specified'}</strong></div>
                      <div><span>Quantity</span><strong>{item.quantity ?? '—'}</strong></div>
                      <div><span>Institution</span><strong>{institutions.find((i) => String(i.id) === String(item.institutionId))?.name || (item.institutionId ? `#${item.institutionId}` : '—')}</strong></div>
                      <div><span>Department</span><strong>{departments.find((d) => String(d.id) === String(item.departmentId))?.name || (item.departmentId ? `#${item.departmentId}` : 'General')}</strong></div>
                      <div><span>Specifications</span><strong>{Object.keys(item.specifications || {}).length ? Object.entries(item.specifications).map(([key, value]) => `${key}: ${typeof value === 'object' ? JSON.stringify(value) : String(value)}`).join('; ') : 'None recorded'}</strong></div>
                      <div><span>Tags</span><strong>{tags.length ? tags.join(', ') : 'None'}</strong></div>
                    </div>
                    <div className="equipment-card-actions">
                      {user?.roles?.includes('RESEARCHER_STUDENT') && item.status === 'AVAILABLE' && (
                        <Link to={`/bookings?equipmentId=${item.id}`} className="button button-primary">
                          Book equipment
                        </Link>
                      )}
                      <button type="button" className="secondary-button" disabled={recordsLoading || deletingId != null} onClick={() => selectEquipment(item.id)}>{selectedId === item.id ? 'Refresh records' : 'Calibration & certification'}</button>
                      {permissions.canEdit && <button type="button" className="secondary-button" disabled={equipmentSubmitting || deletingId != null} onClick={() => beginEdit(item)}>Edit</button>}
                      {permissions.canDelete && <button type="button" className="secondary-button" disabled={deletingId != null || equipmentSubmitting || recordSubmitting} onClick={() => deleteEquipment(item)}>{deletingId === item.id ? 'Deleting…' : 'Delete'}</button>}
                    </div>
                  </article>
                })}
              </div>
            </>}

      {selectedId != null && (
        <section className="booking-form-card">
          <div className="module-section-title"><div><h2>Records for {selectedEquipment?.name || `equipment #${selectedId}`}</h2><p>Calibration and certification history for the selected equipment.</p></div><button type="button" className="secondary-button" onClick={closeSelectedEquipment}>Close records</button></div>
          {recordsError && <div className="module-alert" role="alert">{recordsError}</div>}
          {recordsLoading ? <p role="status">Loading calibration and certification history…</p> : <div className="booking-time-grid">
            <div><h3>Calibration history</h3>{calibrations.length ? <ul>{calibrations.map((record) => <li key={record.id}>{record.calibrationDate} — next due {record.nextDueDate}; {record.result}{record.notes ? `; ${record.notes}` : ''}</li>)}</ul> : <p>No calibration records.</p>}</div>
            <div><h3>Certification history</h3>{certifications.length ? <ul>{certifications.map((record) => <li key={record.id}>{record.certificateType} — {record.issuingOrganization}; {record.issueDate} to {record.expiryDate}{record.documentReference ? `; reference: ${record.documentReference}` : ''}</li>)}</ul> : <p>No certification records.</p>}</div>
          </div>}
          {permissions.canCreateRecords && <>
            {recordErrors.length > 0 && <div className="module-alert" role="alert"><ul>{recordErrors.map((message) => <li key={message}>{message}</li>)}</ul></div>}
            <div className="equipment-card-actions">
              <button type="button" className="secondary-button" disabled={recordSubmitting} onClick={() => { setShowCalibrationForm((visible) => !visible); setRecordErrors([]) }}>{showCalibrationForm ? 'Close calibration form' : 'Add calibration'}</button>
              <button type="button" className="secondary-button" disabled={recordSubmitting} onClick={() => { setShowCertificationForm((visible) => !visible); setRecordErrors([]) }}>{showCertificationForm ? 'Close certification form' : 'Add certification'}</button>
            </div>
            <div className="booking-time-grid">
              {showCalibrationForm && (
              <form className="booking-form-card" onSubmit={submitCalibration}>
                <h3>Add calibration record</h3>
                <div className="form-field"><label htmlFor="cal-date">Calibration date *</label><input id="cal-date" type="date" value={calibrationForm.calibrationDate} onChange={(e) => setCalibrationForm({ ...calibrationForm, calibrationDate: e.target.value })} required /></div>
                <div className="form-field"><label htmlFor="cal-due">Next due date *</label><input id="cal-due" type="date" value={calibrationForm.nextDueDate} onChange={(e) => setCalibrationForm({ ...calibrationForm, nextDueDate: e.target.value })} required /></div>
                <div className="form-field"><label htmlFor="cal-result">Result *</label><select id="cal-result" value={calibrationForm.result} onChange={(e) => setCalibrationForm({ ...calibrationForm, result: e.target.value })}><option>PASS</option><option>FAIL</option><option>CONDITIONAL</option></select></div>
                <div className="form-field"><label htmlFor="cal-notes">Notes</label><textarea id="cal-notes" maxLength={1000} value={calibrationForm.notes} onChange={(e) => setCalibrationForm({ ...calibrationForm, notes: e.target.value })} /></div>
                <button className="button button-primary" type="submit" disabled={recordSubmitting}>{recordSubmitting ? 'Saving…' : 'Save calibration'}</button>
              </form>
              )}
              {showCertificationForm && (
              <form className="booking-form-card" onSubmit={submitCertification}>
                <h3>Add certification record</h3>
                <div className="form-field"><label htmlFor="cert-type">Certificate type *</label><input id="cert-type" maxLength={150} value={certificationForm.certificateType} onChange={(e) => setCertificationForm({ ...certificationForm, certificateType: e.target.value })} required /></div>
                <div className="form-field"><label htmlFor="cert-issuer">Issuing organization *</label><input id="cert-issuer" maxLength={200} value={certificationForm.issuingOrganization} onChange={(e) => setCertificationForm({ ...certificationForm, issuingOrganization: e.target.value })} required /></div>
                <div className="form-field"><label htmlFor="cert-issue">Issue date *</label><input id="cert-issue" type="date" value={certificationForm.issueDate} onChange={(e) => setCertificationForm({ ...certificationForm, issueDate: e.target.value })} required /></div>
                <div className="form-field"><label htmlFor="cert-expiry">Expiry date *</label><input id="cert-expiry" type="date" value={certificationForm.expiryDate} onChange={(e) => setCertificationForm({ ...certificationForm, expiryDate: e.target.value })} required /></div>
                <div className="form-field"><label htmlFor="cert-reference">Document reference (not an upload)</label><input id="cert-reference" maxLength={500} value={certificationForm.documentReference} onChange={(e) => setCertificationForm({ ...certificationForm, documentReference: e.target.value })} /></div>
                <button className="button button-primary" type="submit" disabled={recordSubmitting}>{recordSubmitting ? 'Saving…' : 'Save certification'}</button>
              </form>
              )}
            </div>
          </>}
        </section>
      )}

      <p className="module-footnote">Signed-in account institution: {currentInstitutionName}. The backend enforces institution access for each request.</p>
    </div>
  )
}
