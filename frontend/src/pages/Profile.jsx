import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/axiosInstance'
import {
  canManageMemberships,
  membershipApprovalError,
  PENDING_MEMBERSHIPS_URL,
} from '../access/membershipApproval.js'
import {
  canEditUserDepartment,
  canManageDepartments,
  validateDepartmentName,
} from '../access/departmentWorkflow.js'
import { getProfileDetails } from '../access/profile.js'
import { useAuth } from '../context/AuthContext'

export default function Profile() {
  const { user } = useAuth()
  const profile = getProfileDetails(user)

  // Saved department ID from authenticated session/database
  const [savedDepartmentId, setSavedDepartmentId] = useState(
    user?.departmentId != null ? String(user.departmentId) : '',
  )
  // Form draft state for department selection
  const [selectedDepartmentId, setSelectedDepartmentId] = useState(
    user?.departmentId != null ? String(user.departmentId) : '',
  )
  const [departments, setDepartments] = useState([])
  const [institutionName, setInstitutionName] = useState('')
  const [updating, setUpdating] = useState(false)
  const [deptMessage, setDeptMessage] = useState('')
  const [deptError, setDeptError] = useState('')

  useEffect(() => {
    let active = true
    if (user?.institutionId) {
      api.get(`/api/institutions/${user.institutionId}`)
        .then(({ data }) => {
          if (active && data?.name) setInstitutionName(data.name)
        })
        .catch(() => {})

      api.get('/api/departments', { params: { institutionId: user.institutionId } })
        .then(({ data }) => {
          if (active && Array.isArray(data)) setDepartments(data)
        })
        .catch(() => {})
    }
    api.get('/api/users/me')
      .then(({ data }) => {
        if (active && data) {
          const fetchedId = data.departmentId != null ? String(data.departmentId) : ''
          setSavedDepartmentId(fetchedId)
          setSelectedDepartmentId(fetchedId)
        }
      })
      .catch(() => {})
    return () => { active = false }
  }, [user?.institutionId])

  function handleDepartmentChange(e) {
    setSelectedDepartmentId(e.target.value)
    setDeptMessage('')
    setDeptError('')
  }

  function handleCancelDepartment() {
    setSelectedDepartmentId(savedDepartmentId)
    setDeptMessage('')
    setDeptError('')
  }

  async function handleDepartmentUpdate(e) {
    e.preventDefault()
    if (updating) return
    setUpdating(true)
    setDeptMessage('')
    setDeptError('')
    try {
      const payload = {
        departmentId: selectedDepartmentId ? Number(selectedDepartmentId) : null,
      }
      const { data } = await api.patch('/api/users/me', payload)
      const confirmedId = data?.departmentId != null ? String(data.departmentId) : ''
      setSavedDepartmentId(confirmedId)
      setSelectedDepartmentId(confirmedId)
      setDeptMessage('Department updated successfully.')
    } catch (err) {
      setDeptError(err.response?.data?.message || 'Failed to update department.')
    } finally {
      setUpdating(false)
    }
  }

  if (!profile) {
    return (
      <section className="module-state" role="alert">
        <h1>Profile unavailable</h1>
        <p>Sign in again to load your trusted account details.</p>
        <Link to="/login" className="button button-primary">Sign in</Link>
      </section>
    )
  }

  // Displayed department name is derived strictly from the confirmed savedDepartmentId
  const deptName = departments.find((d) => String(d.id) === String(savedDepartmentId))?.name
    || (savedDepartmentId ? `#${savedDepartmentId}` : 'Unassigned')

  const isSystemAdmin = Array.isArray(user?.roles) && user.roles.includes('SYSTEM_ADMIN')
  const canEditDept = canEditUserDepartment(user?.roles) && !isSystemAdmin
  const isDeptDirty = selectedDepartmentId !== savedDepartmentId

  return (
    <div className="module-page">
      <section className="module-page-heading">
        <div>
          <span className="eyebrow">ACCOUNT</span>
          <h1>Profile</h1>
          <p>Account details supplied by the authenticated server session.</p>
        </div>
      </section>

      <section className="booking-form-card" aria-labelledby="profile-details-title">
        <div className="module-section-title">
          <div>
            <h2 id="profile-details-title">Account details</h2>
            <p>Role assignments and institutional affiliations are verified server-side.</p>
          </div>
        </div>
        <dl className="profile-details">
          <div><dt>Full Name</dt><dd>{user?.fullName || profile.email.split('@')[0]}</dd></div>
          <div><dt>Email</dt><dd>{profile.email}</dd></div>
          <div><dt>User ID</dt><dd>#{profile.userId}</dd></div>
          <div>
            <dt>Institution</dt>
            <dd>
              {isSystemAdmin
                ? 'None (Platform-wide)'
                : institutionName
                  ? `${institutionName} (#${profile.institutionId})`
                  : profile.institutionId
                    ? `#${profile.institutionId}`
                    : 'None assigned'}
            </dd>
          </div>
          <div>
            <dt>Department</dt>
            <dd>{isSystemAdmin ? 'None (Platform-wide)' : deptName}</dd>
          </div>
          <div>
            <dt>Roles</dt>
            <dd>{profile.roles.length ? profile.roles.join(', ') : 'None assigned'}</dd>
          </div>
        </dl>

        {canEditDept ? (
          <form onSubmit={handleDepartmentUpdate} style={{ marginTop: '1.25rem', marginBottom: '1rem' }}>
            <div className="form-field">
              <label htmlFor="user-department-select">Update department affiliation</label>
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginTop: '0.25rem', flexWrap: 'wrap' }}>
                <select
                  id="user-department-select"
                  value={selectedDepartmentId}
                  onChange={handleDepartmentChange}
                  disabled={updating}
                >
                  <option value="">No department assigned</option>
                  {departments.map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
                <button
                  className="button button-primary"
                  type="submit"
                  disabled={updating || !isDeptDirty}
                >
                  {updating ? 'Saving…' : 'Save department'}
                </button>
                <button
                  className="button button-secondary"
                  type="button"
                  onClick={handleCancelDepartment}
                  disabled={updating || !isDeptDirty}
                >
                  Cancel
                </button>
              </div>
            </div>
            {deptMessage && <p className="auth-success" role="status" style={{ marginTop: '0.5rem' }}>{deptMessage}</p>}
            {deptError && <p className="module-alert" role="alert" style={{ marginTop: '0.5rem' }}>{deptError}</p>}
          </form>
        ) : !isSystemAdmin ? (
          <div
            className="module-alert"
            style={{
              marginTop: '1.25rem',
              marginBottom: '1rem',
              backgroundColor: 'var(--surface-color, #f8fafc)',
              borderColor: 'var(--border-color, #e2e8f0)',
              color: 'var(--text-muted, #64748b)',
            }}
          >
            Department affiliation is managed by your Institution Administrator. If you require a department change, please contact your administrator.
          </div>
        ) : null}

        <Link to="/password-reset" className="button button-secondary">Reset password</Link>
      </section>

      {/* Institution Admin: Department Management */}
      {canManageDepartments(user?.roles) && user?.institutionId && (
        <InstitutionDepartmentManagement
          institutionId={user.institutionId}
          onDepartmentChange={(updatedDepts) => setDepartments(updatedDepts)}
        />
      )}

      {/* Institution Admin: Membership Requests */}
      {canManageMemberships(user?.roles) && <PendingMemberships />}

      {/* System Admin: Platform Governance */}
      {isSystemAdmin && (
        <>
          <PendingInstitutionRegistrations />
          <PendingSystemAdminApplications />
          <SystemAdminInstitutions />
        </>
      )}
    </div>
  )
}

function InstitutionDepartmentManagement({ institutionId, onDepartmentChange }) {
  const [departments, setDepartments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [newDeptName, setNewDeptName] = useState('')
  const [adding, setAdding] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [editingName, setEditingName] = useState('')
  const [savingEdit, setSavingEdit] = useState(false)
  const [deletingId, setDeletingId] = useState(null)

  function loadDepartments() {
    setLoading(true)
    setError('')
    api.get('/api/departments', { params: { institutionId } })
      .then(({ data }) => {
        const depts = Array.isArray(data) ? data : []
        setDepartments(depts)
        if (onDepartmentChange) onDepartmentChange(depts)
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Failed to load departments.')
      })
      .finally(() => {
        setLoading(false)
      })
  }

  useEffect(() => {
    let active = true
    api.get('/api/departments', { params: { institutionId } })
      .then(({ data }) => {
        if (active) {
          const depts = Array.isArray(data) ? data : []
          setDepartments(depts)
          if (onDepartmentChange) onDepartmentChange(depts)
        }
      })
      .catch((err) => {
        if (active) setError(err.response?.data?.message || 'Failed to load departments.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [institutionId, onDepartmentChange])

  async function handleAddDepartment(e) {
    e.preventDefault()
    setError('')
    setSuccess('')
    const validationError = validateDepartmentName(newDeptName, departments)
    if (validationError) {
      setError(validationError)
      return
    }

    setAdding(true)
    try {
      const { data } = await api.post('/api/departments', {
        name: newDeptName.trim(),
        institutionId,
      })
      const updated = [...departments, data]
      setDepartments(updated)
      if (onDepartmentChange) onDepartmentChange(updated)
      setNewDeptName('')
      setSuccess(`Department "${data.name}" created successfully.`)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to add department.')
    } finally {
      setAdding(false)
    }
  }

  function startEdit(dept) {
    setEditingId(dept.id)
    setEditingName(dept.name)
    setError('')
    setSuccess('')
  }

  function cancelEdit() {
    setEditingId(null)
    setEditingName('')
  }

  async function handleSaveEdit(id) {
    setError('')
    setSuccess('')
    const validationError = validateDepartmentName(editingName, departments, id)
    if (validationError) {
      setError(validationError)
      return
    }

    setSavingEdit(true)
    try {
      const { data } = await api.put(`/api/departments/${id}`, {
        name: editingName.trim(),
      })
      const updated = departments.map((d) => (d.id === id ? data : d))
      setDepartments(updated)
      if (onDepartmentChange) onDepartmentChange(updated)
      setEditingId(null)
      setEditingName('')
      setSuccess(`Department updated to "${data.name}".`)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to update department.')
    } finally {
      setSavingEdit(false)
    }
  }

  async function handleDelete(dept) {
    if (!window.confirm(`Are you sure you want to delete department "${dept.name}"? Any users and equipment currently in this department will have their department assignment cleared safely.`)) {
      return
    }

    setDeletingId(dept.id)
    setError('')
    setSuccess('')
    try {
      await api.delete(`/api/departments/${dept.id}`)
      const updated = departments.filter((d) => d.id !== dept.id)
      setDepartments(updated)
      if (onDepartmentChange) onDepartmentChange(updated)
      setSuccess(`Department "${dept.name}" deleted successfully.`)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete department.')
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <section className="booking-form-card" aria-labelledby="dept-management-title" style={{ marginTop: '1.5rem' }}>
      <div className="module-section-title">
        <div>
          <h2 id="dept-management-title">Department Management</h2>
          <p>Create and manage departments for your institution. Assigned users and equipment remain safe if unlinked.</p>
        </div>
        <button
          type="button"
          className="secondary-button"
          onClick={loadDepartments}
          disabled={loading || adding || savingEdit || deletingId != null}
        >
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {error && <div className="module-alert" role="alert" style={{ marginBottom: '1rem' }}>{error}</div>}
      {success && <div className="auth-success" role="status" style={{ marginBottom: '1rem' }}>{success}</div>}

      <form onSubmit={handleAddDepartment} style={{ marginBottom: '1.5rem' }}>
        <div className="form-field">
          <label htmlFor="new-dept-name">Add New Department</label>
          <div style={{ display: 'flex', gap: '0.5rem', marginTop: '0.25rem', flexWrap: 'wrap' }}>
            <input
              id="new-dept-name"
              type="text"
              placeholder="e.g. Molecular Biology"
              value={newDeptName}
              onChange={(e) => setNewDeptName(e.target.value)}
              disabled={adding}
              maxLength={150}
              style={{ flex: 1, minWidth: '220px' }}
            />
            <button className="button button-primary" type="submit" disabled={adding || !newDeptName.trim()}>
              {adding ? 'Adding…' : 'Add department'}
            </button>
          </div>
        </div>
      </form>

      {loading ? (
        <p role="status">Loading departments…</p>
      ) : departments.length === 0 ? (
        <p>No departments configured for this institution yet.</p>
      ) : (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border-color, #cbd5e1)' }}>
                <th style={{ padding: '0.5rem' }}>ID</th>
                <th style={{ padding: '0.5rem' }}>Department Name</th>
                <th style={{ padding: '0.5rem', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {departments.map((dept) => (
                <tr key={dept.id} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                  <td style={{ padding: '0.5rem' }}>#{dept.id}</td>
                  <td style={{ padding: '0.5rem' }}>
                    {editingId === dept.id ? (
                      <input
                        type="text"
                        value={editingName}
                        onChange={(e) => setEditingName(e.target.value)}
                        disabled={savingEdit}
                        maxLength={150}
                        style={{ width: '100%', maxWidth: '300px' }}
                      />
                    ) : (
                      <strong>{dept.name}</strong>
                    )}
                  </td>
                  <td style={{ padding: '0.5rem', textAlign: 'right' }}>
                    {editingId === dept.id ? (
                      <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                        <button
                          type="button"
                          className="button button-primary"
                          onClick={() => handleSaveEdit(dept.id)}
                          disabled={savingEdit || !editingName.trim()}
                        >
                          {savingEdit ? 'Saving…' : 'Save'}
                        </button>
                        <button
                          type="button"
                          className="button button-secondary"
                          onClick={cancelEdit}
                          disabled={savingEdit}
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <div style={{ display: 'inline-flex', gap: '0.5rem' }}>
                        <button
                          type="button"
                          className="secondary-button"
                          onClick={() => startEdit(dept)}
                          disabled={deletingId === dept.id}
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          className="secondary-button"
                          style={{ borderColor: '#ef4444', color: '#dc2626' }}
                          onClick={() => handleDelete(dept)}
                          disabled={deletingId === dept.id}
                        >
                          {deletingId === dept.id ? 'Deleting…' : 'Delete'}
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

function PendingMemberships() {
  const [memberships, setMemberships] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [processingId, setProcessingId] = useState(null)

  function loadPending() {
    setLoading(true)
    setError('')
    api.get(PENDING_MEMBERSHIPS_URL)
      .then(({ data }) => {
        setMemberships(Array.isArray(data) ? data : [])
      })
      .catch((requestError) => {
        setError(membershipApprovalError(requestError))
      })
      .finally(() => {
        setLoading(false)
      })
  }

  useEffect(() => {
    let active = true
    api.get(PENDING_MEMBERSHIPS_URL)
      .then(({ data }) => {
        if (active) setMemberships(Array.isArray(data) ? data : [])
      })
      .catch((requestError) => {
        if (active) setError(membershipApprovalError(requestError))
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  async function approve(userId) {
    if (processingId != null) return
    setProcessingId(userId)
    setError('')
    setSuccess('')
    try {
      await api.patch(`/api/users/${encodeURIComponent(userId)}/membership/approve`)
      setMemberships((current) => current.filter((member) => member.id !== userId))
      setSuccess('Membership approved. The user has been granted their requested role and can now sign in.')
    } catch (requestError) {
      setError(membershipApprovalError(requestError))
    } finally {
      setProcessingId(null)
    }
  }

  async function reject(userId) {
    if (processingId != null) return
    const reason = window.prompt('Please enter the reason for rejecting this membership request:', 'Registration details could not be verified')
    if (reason === null) return // User cancelled prompt

    setProcessingId(userId)
    setError('')
    setSuccess('')
    try {
      await api.patch(`/api/users/${encodeURIComponent(userId)}/membership/reject`, { reason })
      setMemberships((current) => current.filter((member) => member.id !== userId))
      setSuccess('Membership request rejected.')
    } catch (requestError) {
      setError(membershipApprovalError(requestError))
    } finally {
      setProcessingId(null)
    }
  }

  return (
    <section className="booking-form-card" aria-labelledby="membership-requests-title" style={{ marginTop: '1.5rem' }}>
      <div className="module-section-title">
        <div>
          <h2 id="membership-requests-title">Membership approval requests</h2>
          <p>Review and verify user membership and role assignments for your institution.</p>
        </div>
        <button
          type="button"
          className="secondary-button"
          onClick={loadPending}
          disabled={loading || processingId != null}
        >
          {loading ? 'Refreshing…' : 'Refresh list'}
        </button>
      </div>

      {error && <div className="module-alert" role="alert">{error}</div>}
      {success && <div className="auth-success" role="status">{success}</div>}

      {loading ? (
        <p role="status">Loading pending memberships…</p>
      ) : memberships.length === 0 ? (
        <p>No membership requests are currently pending approval.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1rem' }}>
          {memberships.map((member) => (
            <article
              key={member.id}
              style={{
                border: '1px solid var(--border-color, #e2e8f0)',
                borderRadius: '8px',
                padding: '1rem',
                backgroundColor: 'var(--surface-color, #ffffff)',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.75rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.5rem' }}>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1.1rem', color: 'var(--text-primary)' }}>
                    {member.fullName || 'Unnamed Applicant'}
                  </h3>
                  <span style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.9rem' }}>
                    {member.email} • ID #{member.id}
                  </span>
                </div>
                <span
                  style={{
                    backgroundColor: '#fef3c7',
                    color: '#92400e',
                    padding: '0.2rem 0.6rem',
                    borderRadius: '9999px',
                    fontSize: '0.8rem',
                    fontWeight: 600,
                  }}
                >
                  {member.membershipStatus || 'PENDING'}
                </span>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.5rem', fontSize: '0.9rem' }}>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Requested Role:</span>
                  <strong>{member.requestedRole ? member.requestedRole.replaceAll('_', ' ') : 'RESEARCHER STUDENT'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Department:</span>
                  <strong>{member.departmentName || (member.departmentId ? `#${member.departmentId}` : 'General / None')}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Institution:</span>
                  <strong>{member.institutionName || `#${member.institutionId}`}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Phone:</span>
                  <strong>{member.phoneNumber || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Registered:</span>
                  <strong>{member.createdAt ? new Date(member.createdAt).toLocaleDateString() : '—'}</strong>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  className="button button-primary"
                  disabled={processingId != null}
                  onClick={() => approve(member.id)}
                >
                  {processingId === member.id ? 'Processing…' : 'Approve membership'}
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  style={{ borderColor: '#ef4444', color: '#dc2626' }}
                  disabled={processingId != null}
                  onClick={() => reject(member.id)}
                >
                  Reject request
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function PendingInstitutionRegistrations() {
  const [institutions, setInstitutions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [processingId, setProcessingId] = useState(null)

  function loadPendingInstitutions() {
    setLoading(true)
    setError('')
    api.get('/api/institutions/pending')
      .then(({ data }) => {
        setInstitutions(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Failed to load pending institutions.')
      })
      .finally(() => {
        setLoading(false)
      })
  }

  useEffect(() => {
    let active = true
    api.get('/api/institutions/pending')
      .then(({ data }) => {
        if (active) setInstitutions(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        if (active) setError(err.response?.data?.message || 'Failed to load pending institutions.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  async function handleApprove(id) {
    if (processingId != null) return
    setProcessingId(id)
    setError('')
    setSuccess('')
    try {
      await api.patch(`/api/institutions/${id}/approve`)
      setInstitutions((prev) => prev.filter((inst) => inst.id !== id))
      setSuccess(`Institution #${id} and its administrator have been approved.`)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to approve institution.')
    } finally {
      setProcessingId(null)
    }
  }

  async function handleReject(id) {
    if (processingId != null) return
    const reason = window.prompt('Please enter the reason for rejecting this institution registration:', 'Institutional credentials could not be verified')
    if (reason === null) return

    setProcessingId(id)
    setError('')
    setSuccess('')
    try {
      await api.patch(`/api/institutions/${id}/reject`, { reason })
      setInstitutions((prev) => prev.filter((inst) => inst.id !== id))
      setSuccess(`Institution #${id} registration rejected.`)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to reject institution.')
    } finally {
      setProcessingId(null)
    }
  }

  return (
    <section className="booking-form-card" aria-labelledby="pending-institutions-title" style={{ marginTop: '1.5rem' }}>
      <div className="module-section-title">
        <div>
          <h2 id="pending-institutions-title">Pending Institution Registrations</h2>
          <p>Review new college/institution onboarding requests and activate institutional administrators.</p>
        </div>
        <button
          type="button"
          className="secondary-button"
          onClick={loadPendingInstitutions}
          disabled={loading || processingId != null}
        >
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {error && <div className="module-alert" role="alert">{error}</div>}
      {success && <div className="auth-success" role="status">{success}</div>}

      {loading ? (
        <p role="status">Loading pending institutions…</p>
      ) : institutions.length === 0 ? (
        <p>No new institutions are awaiting approval.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1rem' }}>
          {institutions.map((inst) => (
            <article
              key={inst.id}
              style={{
                border: '1px solid var(--border-color, #e2e8f0)',
                borderRadius: '8px',
                padding: '1rem',
                backgroundColor: 'var(--surface-color, #ffffff)',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.75rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.5rem' }}>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1.1rem', color: 'var(--text-primary)' }}>
                    {inst.name}
                  </h3>
                  <span style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.9rem' }}>
                    ID #{inst.id} • {inst.location || 'Location unassigned'}
                  </span>
                </div>
                <span
                  style={{
                    backgroundColor: '#fef3c7',
                    color: '#92400e',
                    padding: '0.2rem 0.6rem',
                    borderRadius: '9999px',
                    fontSize: '0.8rem',
                    fontWeight: 600,
                  }}
                >
                  {inst.status || 'PENDING'}
                </span>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.5rem', fontSize: '0.9rem' }}>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Address:</span>
                  <strong>{inst.address || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Contact Email:</span>
                  <strong>{inst.contactEmail || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Phone:</span>
                  <strong>{inst.phoneNumber || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Registered:</span>
                  <strong>{inst.createdAt ? new Date(inst.createdAt).toLocaleDateString() : '—'}</strong>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  className="button button-primary"
                  disabled={processingId != null}
                  onClick={() => handleApprove(inst.id)}
                >
                  {processingId === inst.id ? 'Approving…' : 'Approve Institution'}
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  style={{ borderColor: '#ef4444', color: '#dc2626' }}
                  disabled={processingId != null}
                  onClick={() => handleReject(inst.id)}
                >
                  Reject Registration
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function PendingSystemAdminApplications() {
  const [applicants, setApplicants] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [processingId, setProcessingId] = useState(null)

  function loadApplicants() {
    setLoading(true)
    setError('')
    api.get('/api/users/pending-system-admins')
      .then(({ data }) => {
        setApplicants(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Failed to load system admin applicants.')
      })
      .finally(() => {
        setLoading(false)
      })
  }

  useEffect(() => {
    let active = true
    api.get('/api/users/pending-system-admins')
      .then(({ data }) => {
        if (active) setApplicants(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        if (active) setError(err.response?.data?.message || 'Failed to load system admin applicants.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  async function handleApprove(userId) {
    if (processingId != null) return
    setProcessingId(userId)
    setError('')
    setSuccess('')
    try {
      await api.patch(`/api/users/${userId}/system-admin/approve`)
      setApplicants((prev) => prev.filter((a) => a.id !== userId))
      setSuccess(`System Administrator #${userId} approved successfully.`)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to approve System Administrator.')
    } finally {
      setProcessingId(null)
    }
  }

  async function handleReject(userId) {
    if (processingId != null) return
    const reason = window.prompt('Please enter the reason for rejecting this System Administrator application:', 'Platform administrator verification failed')
    if (reason === null) return

    setProcessingId(userId)
    setError('')
    setSuccess('')
    try {
      await api.patch(`/api/users/${userId}/system-admin/reject`, { reason })
      setApplicants((prev) => prev.filter((a) => a.id !== userId))
      setSuccess(`System Administrator application #${userId} rejected.`)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to reject System Administrator.')
    } finally {
      setProcessingId(null)
    }
  }

  return (
    <section className="booking-form-card" aria-labelledby="pending-sysadmins-title" style={{ marginTop: '1.5rem' }}>
      <div className="module-section-title">
        <div>
          <h2 id="pending-sysadmins-title">Pending System Administrator Applications</h2>
          <p>Review prospective platform administrators. Self-approval is barred server-side.</p>
        </div>
        <button
          type="button"
          className="secondary-button"
          onClick={loadApplicants}
          disabled={loading || processingId != null}
        >
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {error && <div className="module-alert" role="alert">{error}</div>}
      {success && <div className="auth-success" role="status">{success}</div>}

      {loading ? (
        <p role="status">Loading applications…</p>
      ) : applicants.length === 0 ? (
        <p>No System Administrator applications are currently awaiting approval.</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginTop: '1rem' }}>
          {applicants.map((app) => (
            <article
              key={app.id}
              style={{
                border: '1px solid var(--border-color, #e2e8f0)',
                borderRadius: '8px',
                padding: '1rem',
                backgroundColor: 'var(--surface-color, #ffffff)',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.75rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '0.5rem' }}>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1.1rem', color: 'var(--text-primary)' }}>
                    {app.fullName || 'Unnamed Applicant'}
                  </h3>
                  <span style={{ color: 'var(--text-muted, #64748b)', fontSize: '0.9rem' }}>
                    {app.email} • ID #{app.id}
                  </span>
                </div>
                <span
                  style={{
                    backgroundColor: '#fef3c7',
                    color: '#92400e',
                    padding: '0.2rem 0.6rem',
                    borderRadius: '9999px',
                    fontSize: '0.8rem',
                    fontWeight: 600,
                  }}
                >
                  SYSTEM_ADMIN APPLICANT
                </span>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '0.5rem', fontSize: '0.9rem' }}>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Email:</span>
                  <strong>{app.email}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Phone:</span>
                  <strong>{app.phoneNumber || '—'}</strong>
                </div>
                <div>
                  <span style={{ color: 'var(--text-muted, #64748b)', display: 'block' }}>Applied:</span>
                  <strong>{app.createdAt ? new Date(app.createdAt).toLocaleDateString() : '—'}</strong>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
                <button
                  type="button"
                  className="button button-primary"
                  disabled={processingId != null}
                  onClick={() => handleApprove(app.id)}
                >
                  {processingId === app.id ? 'Approving…' : 'Approve System Admin'}
                </button>
                <button
                  type="button"
                  className="secondary-button"
                  style={{ borderColor: '#ef4444', color: '#dc2626' }}
                  disabled={processingId != null}
                  onClick={() => handleReject(app.id)}
                >
                  Reject Application
                </button>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}

function SystemAdminInstitutions() {
  const [institutions, setInstitutions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [updatingId, setUpdatingId] = useState(null)

  function loadInstitutions() {
    setLoading(true)
    setError('')
    api.get('/api/institutions')
      .then(({ data }) => {
        setInstitutions(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Failed to load institutions.')
      })
      .finally(() => {
        setLoading(false)
      })
  }

  useEffect(() => {
    let active = true
    api.get('/api/institutions')
      .then(({ data }) => {
        if (active) setInstitutions(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        if (active) setError(err.response?.data?.message || 'Failed to load institutions.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => { active = false }
  }, [])

  async function toggleStatus(institutionId, currentStatus) {
    const nextStatus = currentStatus === 'APPROVED' ? 'SUSPENDED' : 'APPROVED'
    setUpdatingId(institutionId)
    setError('')
    setSuccess('')
    try {
      await api.patch(`/api/institutions/${institutionId}/status`, { status: nextStatus })
      setInstitutions((prev) =>
        prev.map((inst) => (inst.id === institutionId ? { ...inst, status: nextStatus } : inst)),
      )
      setSuccess(`Institution #${institutionId} status updated to ${nextStatus}.`)
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to update institution status.')
    } finally {
      setUpdatingId(null)
    }
  }

  return (
    <section className="booking-form-card" aria-labelledby="system-admin-inst-title" style={{ marginTop: '1.5rem' }}>
      <div className="module-section-title">
        <div>
          <h2 id="system-admin-inst-title">Platform institutions management</h2>
          <p>System Administrator view for monitoring all registered institutions across the platform.</p>
        </div>
        <button
          type="button"
          className="secondary-button"
          onClick={loadInstitutions}
          disabled={loading || updatingId != null}
        >
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {error && <div className="module-alert" role="alert">{error}</div>}
      {success && <div className="auth-success" role="status">{success}</div>}

      {loading ? (
        <p role="status">Loading institutions…</p>
      ) : institutions.length === 0 ? (
        <p>No institutions found.</p>
      ) : (
        <div style={{ overflowX: 'auto', marginTop: '1rem' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.9rem' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid var(--border-color, #cbd5e1)' }}>
                <th style={{ padding: '0.5rem' }}>ID</th>
                <th style={{ padding: '0.5rem' }}>Name</th>
                <th style={{ padding: '0.5rem' }}>Location</th>
                <th style={{ padding: '0.5rem' }}>Contact</th>
                <th style={{ padding: '0.5rem' }}>Status</th>
                <th style={{ padding: '0.5rem' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {institutions.map((inst) => (
                <tr key={inst.id} style={{ borderBottom: '1px solid var(--border-color, #e2e8f0)' }}>
                  <td style={{ padding: '0.5rem' }}>#{inst.id}</td>
                  <td style={{ padding: '0.5rem' }}><strong>{inst.name}</strong></td>
                  <td style={{ padding: '0.5rem' }}>{inst.location || inst.address || '—'}</td>
                  <td style={{ padding: '0.5rem' }}>{inst.contactEmail || inst.phoneNumber || '—'}</td>
                  <td style={{ padding: '0.5rem' }}>
                    <span
                      style={{
                        padding: '0.2rem 0.5rem',
                        borderRadius: '4px',
                        fontSize: '0.8rem',
                        fontWeight: 600,
                        backgroundColor: inst.status === 'APPROVED' ? '#dcfce7' : '#fee2e2',
                        color: inst.status === 'APPROVED' ? '#15803d' : '#b91c1c',
                      }}
                    >
                      {inst.status || 'APPROVED'}
                    </span>
                  </td>
                  <td style={{ padding: '0.5rem' }}>
                    <button
                      type="button"
                      className="secondary-button"
                      disabled={updatingId === inst.id}
                      onClick={() => toggleStatus(inst.id, inst.status || 'APPROVED')}
                    >
                      {updatingId === inst.id ? 'Updating…' : inst.status === 'APPROVED' ? 'Suspend' : 'Activate'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
