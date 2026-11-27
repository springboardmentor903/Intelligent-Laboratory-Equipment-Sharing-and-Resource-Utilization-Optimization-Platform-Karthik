import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'
import { canManageSharing } from '../access/roleAccess.js'
import {
  buildEquipmentSearchParams,
  getEditableEquipmentStatuses,
  getEquipmentPermissions,
  validateCalibrationForm,
  validateCertificationForm,
  validateEquipmentForm,
} from '../access/equipmentWorkflow.js'
import {
  validateMaintenanceRequest,
  buildMaintenanceRequestPayload,
} from '../access/maintenanceWorkflow.js'

const blankEquipment = (institutionId = '') => ({
  name: '', assetTag: '', category: '', description: '', institutionId: institutionId ?? '',
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

  const [problemModalOpen, setProblemModalOpen] = useState(false)
  const [problemEquipment, setProblemEquipment] = useState(null)
  const [problemForm, setProblemForm] = useState({ title: '', description: '', priority: 'MEDIUM' })
  const [problemSubmitting, setProblemSubmitting] = useState(false)
  const [problemError, setProblemError] = useState('')
  const [problemSuccess, setProblemSuccess] = useState('')

  const [recordsTab, setRecordsTab] = useState('calibrations')
  const [serviceHistory, setServiceHistory] = useState([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [downtimeSummary, setDowntimeSummary] = useState(null)
  const [downtimeLoading, setDowntimeLoading] = useState(false)
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
    const isSysAdmin = Array.isArray(user?.roles) && user.roles.includes('SYSTEM_ADMIN')

    const requests = [
      api.get(`/api/equipment/${equipmentId}/calibrations`),
      api.get(`/api/equipment/${equipmentId}/certifications`),
    ]
    if (!isSysAdmin) {
      requests.push(api.get(`/api/maintenance/work-orders/equipment/${equipmentId}/history`))
      requests.push(api.get(`/api/maintenance/downtime/summary?equipmentId=${equipmentId}`))
    }

    const results = await Promise.allSettled(requests)
    if (requestId !== recordsRequestId.current) return
    const calibrationResult = results[0]
    const certificationResult = results[1]
    const historyResult = !isSysAdmin ? results[2] : null
    const downtimeResult = !isSysAdmin ? results[3] : null

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

    if (historyResult && historyResult.status === 'fulfilled') {
      setServiceHistory(historyResult.value.data?.content || historyResult.value.data || [])
    } else {
      setServiceHistory([])
    }

    if (downtimeResult && downtimeResult.status === 'fulfilled') {
      setDowntimeSummary(downtimeResult.value.data)
    } else {
      setDowntimeSummary(null)
    }

    setRecordsError(errors.join(' '))
    setRecordsLoading(false)
  }, [user?.roles])

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
      name: item.name ?? '', assetTag: item.assetTag ?? '', category: item.category ?? '', description: item.description ?? '',
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
    if (!window.confirm(`Retire ${item.name || `equipment #${item.id}`}? Its booking and maintenance history will be preserved.`)) return
    deleteLock.current = true
    setDeletingId(item.id)
    setError('')
    setSuccess('')
    try {
      await api.delete(`/api/equipment/${item.id}`)
      equipmentRequests.current.clear()
      if (String(selectedIdRef.current) === String(item.id)) closeSelectedEquipment()
      setSuccess('Equipment retired. Its history has been preserved.')
      await loadEquipment(scopedSearchFilters)
    } catch (err) {
      setError(errorMessage(err, 'Unable to delete equipment.'))
    } finally {
      deleteLock.current = false
      setDeletingId(null)
    }
  }

  function openReportProblem(eq) {
    setProblemEquipment(eq)
    setProblemForm({ title: '', description: '', priority: 'MEDIUM' })
    setProblemError('')
    setProblemSuccess('')
    setProblemModalOpen(true)
  }

  async function submitProblemReport(e) {
    e.preventDefault()
    if (!problemEquipment || problemSubmitting) return
    const valErr = validateMaintenanceRequest({
      equipmentId: problemEquipment.id,
      title: problemForm.title,
      description: problemForm.description,
      priority: problemForm.priority,
    })
    if (valErr) {
      setProblemError(valErr)
      return
    }
    setProblemSubmitting(true)
    setProblemError('')
    setProblemSuccess('')
    try {
      const payload = buildMaintenanceRequestPayload({
        equipmentId: problemEquipment.id,
        title: problemForm.title,
        description: problemForm.description,
        priority: problemForm.priority,
      })
      await api.post('/api/maintenance/requests', payload)
      setProblemSuccess('Maintenance problem reported successfully.')
      setTimeout(() => {
        setProblemModalOpen(false)
        setProblemSuccess('')
      }, 1500)
    } catch (err) {
      setProblemError(errorMessage(err, 'Unable to submit maintenance request.'))
    } finally {
      setProblemSubmitting(false)
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

  const isOwnerAdminOrSystemAdmin = useMemo(() => {
    if (!canManageSharing(user?.roles)) return false
    if (user?.roles?.includes('SYSTEM_ADMIN')) return true
    return selectedEquipment?.institutionId != null && String(user?.institutionId) === String(selectedEquipment?.institutionId)
  }, [user?.roles, user?.institutionId, selectedEquipment?.institutionId])

  const [sharingListing, setSharingListing] = useState({
    feePerHour: '',
    externalSharePercentage: 20,
    minimumNoticeHours: 24,
    maximumBookingHours: 8,
    terms: '',
    active: true,
  })
  const [isShareable, setIsShareable] = useState(false)
  const [sharingLoading, setSharingLoading] = useState(false)
  const [sharingSaving, setSharingSaving] = useState(false)
  const [sharingError, setSharingError] = useState('')
  const [sharingSuccess, setSharingSuccess] = useState('')

  useEffect(() => {
    if (!selectedId || !isOwnerAdminOrSystemAdmin) return
    setIsShareable(Boolean(selectedEquipment?.shareable))
    setSharingLoading(true)
    setSharingError('')
    setSharingSuccess('')
    api.get(`/api/sharing/equipment/${selectedId}/listing`)
      .then((res) => {
        if (res.data) {
          setSharingListing({
            feePerHour: res.data.feePerHour ?? '',
            externalSharePercentage: res.data.externalSharePercentage ?? 20,
            minimumNoticeHours: res.data.minimumNoticeHours ?? 24,
            maximumBookingHours: res.data.maximumBookingHours ?? 8,
            terms: res.data.terms ?? '',
            active: res.data.active ?? true,
          })
        }
      })
      .catch(() => {
        setSharingListing({
          feePerHour: '',
          externalSharePercentage: 20,
          minimumNoticeHours: 24,
          maximumBookingHours: 8,
          terms: '',
          active: true,
        })
      })
      .finally(() => setSharingLoading(false))
  }, [selectedId, isOwnerAdminOrSystemAdmin, selectedEquipment?.shareable])

  async function toggleShareable(checked) {
    if (!selectedId) return
    setIsShareable(checked)
    try {
      await api.patch(`/api/sharing/equipment/${selectedId}/shareable`, { shareable: checked })
      setSharingSuccess(checked ? 'Equipment marked shareable.' : 'Equipment is no longer shareable.')
      if (selectedEquipment) selectedEquipment.shareable = checked
    } catch (err) {
      setIsShareable(!checked)
      setSharingError(errorMessage(err, 'Unable to update shareable status.'))
    }
  }

  async function saveSharingListing(e) {
    e.preventDefault()
    if (!selectedId || sharingSaving) return
    setSharingSaving(true)
    setSharingError('')
    setSharingSuccess('')
    try {
      await api.put(`/api/sharing/equipment/${selectedId}/listing`, {
        feePerHour: Number(sharingListing.feePerHour),
        externalSharePercentage: Number(sharingListing.externalSharePercentage),
        minimumNoticeHours: Number(sharingListing.minimumNoticeHours),
        maximumBookingHours: Number(sharingListing.maximumBookingHours),
        terms: sharingListing.terms,
        active: sharingListing.active,
      })
      setSharingSuccess('Sharing listing saved successfully.')
    } catch (err) {
      setSharingError(errorMessage(err, 'Unable to save sharing listing.'))
    } finally {
      setSharingSaving(false)
    }
  }

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
              <div className="form-field"><label htmlFor="equipment-asset-tag">Asset tag</label><input id="equipment-asset-tag" maxLength={100} value={equipmentForm.assetTag} onChange={(e) => setEquipmentForm({ ...equipmentForm, assetTag: e.target.value })} placeholder="Optional inventory label" /></div>
              <div className="form-field"><label htmlFor="equipment-category">Category</label><input id="equipment-category" maxLength={100} value={equipmentForm.category} onChange={(e) => setEquipmentForm({ ...equipmentForm, category: e.target.value })} /></div>
              <div className="form-field"><label htmlFor="equipment-institution">Institution ID *</label><input id="equipment-institution" type="number" min="1" value={equipmentForm.institutionId} onChange={(e) => setEquipmentForm({ ...equipmentForm, institutionId: e.target.value })} required /></div>
              <div className="form-field"><label htmlFor="equipment-quantity">Quantity *</label><input id="equipment-quantity" type="number" min="1" step="1" value={equipmentForm.quantity} onChange={(e) => setEquipmentForm({ ...equipmentForm, quantity: e.target.value })} required /></div>
              <div className="form-field"><label htmlFor="equipment-location">Lab location</label><input id="equipment-location" maxLength={150} value={equipmentForm.labLocation} onChange={(e) => setEquipmentForm({ ...equipmentForm, labLocation: e.target.value })} /></div>
              <div className="form-field"><label htmlFor="equipment-department">Department</label><select id="equipment-department" value={equipmentForm.departmentId || ''} onChange={(e) => setEquipmentForm({ ...equipmentForm, departmentId: e.target.value })}><option value="">None / General</option>{departments.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></div>
              <div className="form-field"><label htmlFor="equipment-status">Status</label><select id="equipment-status" value={equipmentForm.status || 'AVAILABLE'} disabled={editingId == null || !permissions.canChangeStatus || equipmentForm.status === 'RETIRED'} onChange={(e) => setEquipmentForm({ ...equipmentForm, status: e.target.value })}>{getEditableEquipmentStatuses(equipmentForm.status, permissions.canChangeStatus, editingId == null).map((status) => <option value={status} key={status}>{status.replaceAll('_', ' ')}</option>)}</select><small>BOOKED is managed by booking check-in. Retired equipment cannot be reactivated.</small></div>
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
                    {status === 'UNDER_MAINTENANCE' && (
                      <div className="banner-under-maintenance" style={{
                        background: 'rgba(239, 68, 68, 0.1)',
                        border: '1px solid rgba(239, 68, 68, 0.3)',
                        color: 'var(--color-danger, #ef4444)',
                        padding: '6px 10px',
                        borderRadius: '6px',
                        fontSize: '12.5px',
                        fontWeight: 600,
                        marginTop: '6px',
                        marginBottom: '8px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                      }}>
                        <span>⚠️ Under Maintenance — Bookings paused</span>
                      </div>
                    )}
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
                      {!user?.roles?.includes('SYSTEM_ADMIN') && (
                        <button
                          type="button"
                          className="secondary-button"
                          onClick={() => openReportProblem(item)}
                        >
                          ⚠️ Report Problem
                        </button>
                      )}
                      <button type="button" className="secondary-button" disabled={recordsLoading || deletingId != null} onClick={() => selectEquipment(item.id)}>{selectedId === item.id ? 'Refresh records' : 'Records & History'}</button>
                      {permissions.canEdit && <button type="button" className="secondary-button" disabled={equipmentSubmitting || deletingId != null} onClick={() => beginEdit(item)}>Edit</button>}
                      {canManageSharing(user?.roles) && (user?.roles?.includes('SYSTEM_ADMIN') || String(user?.institutionId) === String(item.institutionId)) && (
                        <button type="button" className="secondary-button" onClick={() => selectEquipment(item.id)}>
                          Share this equipment
                        </button>
                      )}
                      {permissions.canDelete && status !== 'RETIRED' && <button type="button" className="secondary-button" disabled={deletingId != null || equipmentSubmitting || recordSubmitting} onClick={() => deleteEquipment(item)}>{deletingId === item.id ? 'Retiring…' : 'Retire'}</button>}
                    </div>
                  </article>
                })}
              </div>
            </>}

      {selectedId != null && (
        <section className="booking-form-card">
          <div className="module-section-title">
            <div>
              <h2>Records for {selectedEquipment?.name || `equipment #${selectedId}`}</h2>
              <p>Maintenance, service history, and calibration records for the selected equipment.</p>
            </div>
            <button type="button" className="secondary-button" onClick={closeSelectedEquipment}>Close records</button>
          </div>

          <div style={{ display: 'flex', gap: '8px', marginBottom: '16px', borderBottom: '1px solid var(--border)', paddingBottom: '8px' }}>
            <button
              type="button"
              className={`secondary-button ${recordsTab === 'calibrations' ? 'active' : ''}`}
              style={{ fontWeight: recordsTab === 'calibrations' ? 700 : 500 }}
              onClick={() => setRecordsTab('calibrations')}
            >
              📜 Calibration & Certification
            </button>
            {!user?.roles?.includes('SYSTEM_ADMIN') && (
              <>
                <button
                  type="button"
                  className={`secondary-button ${recordsTab === 'history' ? 'active' : ''}`}
                  style={{ fontWeight: recordsTab === 'history' ? 700 : 500 }}
                  onClick={() => setRecordsTab('history')}
                >
                  🔧 Service History ({serviceHistory.length})
                </button>
                <button
                  type="button"
                  className={`secondary-button ${recordsTab === 'downtime' ? 'active' : ''}`}
                  style={{ fontWeight: recordsTab === 'downtime' ? 700 : 500 }}
                  onClick={() => setRecordsTab('downtime')}
                >
                  📉 Downtime Analytics
                </button>
              </>
            )}
          </div>

          {recordsTab === 'calibrations' && (
            <>
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
            </>
          )}

          {recordsTab === 'history' && (
            <div style={{ marginTop: '10px' }}>
              <h3 style={{ margin: '0 0 10px', fontSize: '1.05rem', fontWeight: 700 }}>Past Service & Maintenance History</h3>
              {historyLoading ? (
                <p>Loading service history…</p>
              ) : serviceHistory.length === 0 ? (
                <p style={{ color: 'var(--text-muted)' }}>No completed maintenance work orders recorded for this equipment.</p>
              ) : (
                <div style={{ overflowX: 'auto' }}>
                  <table className="module-table" style={{ width: '100%', borderCollapse: 'collapse', fontSize: '13px' }}>
                    <thead>
                      <tr style={{ textAlign: 'left', borderBottom: '2px solid var(--border)' }}>
                        <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Order #</th>
                        <th style={{ padding: '8px' }}>Title</th>
                        <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Type</th>
                        <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Technician</th>
                        <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Completed At</th>
                        <th style={{ padding: '8px', whiteSpace: 'nowrap' }}>Cost</th>
                        <th style={{ padding: '8px' }}>Resolution Notes</th>
                      </tr>
                    </thead>
                    <tbody>
                      {serviceHistory.map((wo) => (
                        <tr key={wo.id} style={{ borderBottom: '1px solid var(--border)' }}>
                          <td style={{ padding: '8px', fontWeight: 700, fontFamily: 'monospace', whiteSpace: 'nowrap' }}>{wo.workOrderNumber}</td>
                          <td style={{ padding: '8px', fontWeight: 600 }}>{wo.title}</td>
                          <td style={{ padding: '8px', whiteSpace: 'nowrap' }}>{wo.workOrderType}</td>
                          <td style={{ padding: '8px', whiteSpace: 'nowrap' }}>{wo.assignedTechnicianName || '—'}</td>
                          <td style={{ padding: '8px', whiteSpace: 'nowrap' }}>{wo.completedAt ? new Date(wo.completedAt).toLocaleString() : '—'}</td>
                          <td style={{ padding: '8px', whiteSpace: 'nowrap' }}>{wo.cost ? `₹${wo.cost}` : '—'}</td>
                          <td style={{ padding: '8px' }}>{wo.resolutionNotes || '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {recordsTab === 'downtime' && (
            <div style={{ marginTop: '10px' }}>
              <h3 style={{ margin: '0 0 10px', fontSize: '1.05rem', fontWeight: 700 }}>Downtime & Operational Reliability</h3>
              {downtimeLoading ? (
                <p>Loading downtime analytics…</p>
              ) : !downtimeSummary ? (
                <p style={{ color: 'var(--text-muted)' }}>No downtime recorded for this equipment.</p>
              ) : (
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '14px' }}>
                  <div style={{ background: 'var(--surface-muted, #f8fafc)', padding: '14px', borderRadius: '8px', border: '1px solid var(--border)' }}>
                    <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 600 }}>Total Raw Downtime</span>
                    <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '4px' }}>{downtimeSummary.totalRawDowntimeHours} <small style={{ fontSize: '12px' }}>hrs</small></div>
                  </div>
                  <div style={{ background: 'var(--surface-muted, #f8fafc)', padding: '14px', borderRadius: '8px', border: '1px solid var(--border)' }}>
                    <span style={{ fontSize: '12px', color: 'var(--color-danger, #ef4444)', fontWeight: 600 }}>Operating Hours Lost</span>
                    <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '4px', color: 'var(--color-danger, #ef4444)' }}>{downtimeSummary.totalOperatingDowntimeHours} <small style={{ fontSize: '12px' }}>hrs</small></div>
                  </div>
                  <div style={{ background: 'var(--surface-muted, #f8fafc)', padding: '14px', borderRadius: '8px', border: '1px solid var(--border)' }}>
                    <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 600 }}>Maintenance Incidents</span>
                    <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '4px' }}>{downtimeSummary.totalWorkOrders}</div>
                  </div>
                  <div style={{ background: 'var(--surface-muted, #f8fafc)', padding: '14px', borderRadius: '8px', border: '1px solid var(--border)' }}>
                    <span style={{ fontSize: '12px', color: 'var(--text-muted)', fontWeight: 600 }}>Facility Impact</span>
                    <div style={{ fontSize: '1.5rem', fontWeight: 800, marginTop: '4px' }}>{downtimeSummary.openHoursLostPercentage}%</div>
                  </div>
                </div>
              )}
            </div>
          )}

          {isOwnerAdminOrSystemAdmin && (
            <div style={{ marginTop: '24px', borderTop: '1px solid var(--border)', paddingTop: '20px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '8px', marginBottom: '14px' }}>
                <div>
                  <h3 style={{ margin: '0 0 4px', fontSize: '1.1rem', fontWeight: 700 }}>Share This Equipment</h3>
                  <p style={{ margin: 0, fontSize: '13px', color: 'var(--text-muted)' }}>
                    Configure inter-institution sharing parameters and publication terms.
                  </p>
                </div>
                <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontWeight: 600, fontSize: '13.5px' }}>
                  <input
                    type="checkbox"
                    checked={isShareable}
                    onChange={(e) => toggleShareable(e.target.checked)}
                  />
                  <span>Mark equipment shareable</span>
                </label>
              </div>

              {sharingSuccess && <div className="auth-success" role="status" style={{ marginBottom: '12px' }}>{sharingSuccess}</div>}
              {sharingError && <div className="module-alert" role="alert" style={{ marginBottom: '12px' }}>{sharingError}</div>}

              {sharingLoading ? (
                <p>Loading sharing listing…</p>
              ) : (
                <form className="booking-form" onSubmit={saveSharingListing}>
                  <div className="booking-time-grid">
                    <div className="form-field">
                      <label htmlFor="sharing-fee">Fee per Hour (₹) *</label>
                      <input
                        id="sharing-fee"
                        type="number"
                        min="0"
                        step="0.01"
                        required
                        placeholder="e.g. 1500"
                        value={sharingListing.feePerHour}
                        onChange={(e) => setSharingListing({ ...sharingListing, feePerHour: e.target.value })}
                      />
                    </div>
                    <div className="form-field">
                      <label htmlFor="sharing-pct">External Share Target (%) *</label>
                      <input
                        id="sharing-pct"
                        type="number"
                        min="0"
                        max="100"
                        step="0.01"
                        required
                        value={sharingListing.externalSharePercentage}
                        onChange={(e) => setSharingListing({ ...sharingListing, externalSharePercentage: e.target.value })}
                      />
                    </div>
                    <div className="form-field">
                      <label htmlFor="sharing-notice">Minimum Notice (Hours) *</label>
                      <input
                        id="sharing-notice"
                        type="number"
                        min="0"
                        required
                        value={sharingListing.minimumNoticeHours}
                        onChange={(e) => setSharingListing({ ...sharingListing, minimumNoticeHours: e.target.value })}
                      />
                    </div>
                    <div className="form-field">
                      <label htmlFor="sharing-duration">Maximum Duration (Hours) *</label>
                      <input
                        id="sharing-duration"
                        type="number"
                        min="1"
                        required
                        value={sharingListing.maximumBookingHours}
                        onChange={(e) => setSharingListing({ ...sharingListing, maximumBookingHours: e.target.value })}
                      />
                    </div>
                  </div>
                  <div className="form-field">
                    <label htmlFor="sharing-terms">Sharing Terms & Usage Conditions</label>
                    <textarea
                      id="sharing-terms"
                      maxLength={2000}
                      rows={3}
                      placeholder="Specify safety certifications, lab policies, operator assistance requirements…"
                      value={sharingListing.terms}
                      onChange={(e) => setSharingListing({ ...sharingListing, terms: e.target.value })}
                    />
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', fontSize: '13.5px' }}>
                      <input
                        type="checkbox"
                        checked={sharingListing.active}
                        onChange={(e) => setSharingListing({ ...sharingListing, active: e.target.checked })}
                      />
                      <span>Listing active and discoverable</span>
                    </label>
                    <button type="submit" className="button button-primary" disabled={sharingSaving || !isShareable}>
                      {sharingSaving ? 'Saving…' : 'Save Sharing Listing'}
                    </button>
                  </div>
                  {!isShareable && (
                    <small style={{ color: 'var(--text-muted)', display: 'block', marginTop: '6px' }}>
                      Mark equipment as shareable before saving or activating the sharing listing.
                    </small>
                  )}
                </form>
              )}
            </div>
          )}
        </section>
      )}

      {problemModalOpen && (
        <div className="modal-overlay" style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.5)', zIndex: 1100, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '16px' }}>
          <div style={{ background: 'var(--surface)', width: '100%', maxWidth: '500px', maxHeight: '90vh', overflowY: 'auto', borderRadius: '12px', padding: '24px', boxShadow: 'var(--shadow)' }}>
            <h3 style={{ margin: '0 0 10px', fontSize: '1.2rem', fontWeight: 700 }}>Report Problem: {problemEquipment?.name}</h3>
            {problemSuccess && <div className="auth-success" style={{ marginBottom: '12px' }}>{problemSuccess}</div>}
            {problemError && <div className="module-alert" style={{ marginBottom: '12px' }}>{problemError}</div>}
            <form onSubmit={submitProblemReport} className="booking-form">
              <div className="form-field">
                <label>Problem Title *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Laser power fluctuating, vacuum seal leaking"
                  value={problemForm.title}
                  onChange={(e) => setProblemForm({ ...problemForm, title: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Description *</label>
                <textarea
                  rows={3}
                  required
                  placeholder="Describe what occurred, any error messages, or symptoms observed…"
                  value={problemForm.description}
                  onChange={(e) => setProblemForm({ ...problemForm, description: e.target.value })}
                />
              </div>
              <div className="form-field">
                <label>Priority</label>
                <select
                  value={problemForm.priority}
                  onChange={(e) => setProblemForm({ ...problemForm, priority: e.target.value })}
                >
                  <option value="LOW">LOW</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="HIGH">HIGH</option>
                  <option value="CRITICAL">CRITICAL</option>
                </select>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', marginTop: '16px' }}>
                <button type="button" className="secondary-button" onClick={() => setProblemModalOpen(false)}>Cancel</button>
                <button type="submit" className="button button-primary" disabled={problemSubmitting}>
                  {problemSubmitting ? 'Submitting…' : 'Submit Report'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      <p className="module-footnote">Signed-in account institution: {currentInstitutionName}. The backend enforces institution access for each request.</p>
    </div>
  )
}
