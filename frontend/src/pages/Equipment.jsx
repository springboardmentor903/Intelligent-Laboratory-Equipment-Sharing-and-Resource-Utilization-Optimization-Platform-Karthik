
import { useCallback, useEffect, useMemo, useState } from 'react'
import api from '../api/axiosInstance'
import { useAuth } from '../context/AuthContext'

function getErrorMessage(error) {
  return (
    error.response?.data?.message ||
    error.response?.data?.error ||
    error.message ||
    'Unable to load equipment. Please try again.'
  )
}

export default function Equipment() {
  const { user } = useAuth()

  const [equipment, setEquipment] = useState([])
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const loadEquipment = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const response = await api.get('/api/equipment')

      setEquipment(
        Array.isArray(response.data) ? response.data : [],
      )
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadEquipment()
  }, [loadEquipment])

  const availableStatuses = useMemo(() => {
    const statuses = equipment
      .map((item) => String(item.status ?? '').toUpperCase())
      .filter(Boolean)

    return [...new Set(statuses)].sort()
  }, [equipment])

  const filteredEquipment = useMemo(() => {
    const query = search.trim().toLowerCase()

    return equipment.filter((item) => {
      const searchableValues = [
        item.name,
        item.category,
        item.description,
        item.labLocation,
        item.institutionId,
      ]

      const matchesSearch = searchableValues.some((value) =>
        String(value ?? '').toLowerCase().includes(query),
      )

      const matchesStatus =
        statusFilter === 'ALL' ||
        String(item.status ?? '').toUpperCase() === statusFilter

      return matchesSearch && matchesStatus
    })
  }, [equipment, search, statusFilter])

  return (
    <div className="module-page">
      <section className="module-page-heading">
        <div>
          <span className="eyebrow">RESOURCE MANAGEMENT</span>
          <h1>Equipment</h1>
          <p>
            Browse laboratory equipment and review availability details.
          </p>
        </div>

        <div className="module-count">
          <strong>{equipment.length}</strong>
          <span>Total records</span>
        </div>
      </section>

      <section className="module-toolbar">
        <div className="module-search">
          <span aria-hidden="true">⌕</span>
          <input
            type="search"
            aria-label="Search equipment"
            placeholder="Search by name, category, description, location, or institution"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>

        <select
          className="module-select"
          aria-label="Filter by equipment status"
          value={statusFilter}
          onChange={(event) => setStatusFilter(event.target.value)}
        >
          <option value="ALL">All statuses</option>

          {availableStatuses.map((status) => (
            <option value={status} key={status}>
              {status.replaceAll('_', ' ')}
            </option>
          ))}
        </select>
      </section>

      {error && (
        <div className="module-alert" role="alert">
          <span>{error}</span>
          <button
            type="button"
            onClick={loadEquipment}
            disabled={loading}
          >
            {loading ? 'Retrying…' : 'Retry'}
          </button>
        </div>
      )}

      {loading ? (
        <div className="module-state" role="status">
          <span className="loading-spinner" />
          <p>Loading equipment…</p>
        </div>
      ) : !error && filteredEquipment.length === 0 ? (
        <div className="module-state">
          <div className="module-empty-icon" aria-hidden="true">
            🔬
          </div>

          <h2>No equipment found</h2>

          <p>
            {equipment.length === 0
              ? 'There are no equipment records to display yet.'
              : 'Try changing your search or status filter.'}
          </p>

          {(search || statusFilter !== 'ALL') && (
            <button
              type="button"
              className="button button-secondary"
              onClick={() => {
                setSearch('')
                setStatusFilter('ALL')
              }}
            >
              Clear filters
            </button>
          )}
        </div>
      ) : !error ? (
        <>
          <p className="module-results-count">
            Showing {filteredEquipment.length} of {equipment.length} records
          </p>

          <div className="equipment-grid">
            {filteredEquipment.map((item) => {
              const status = String(
                item.status ?? 'UNKNOWN',
              ).toUpperCase()

              return (
                <article className="equipment-card" key={item.id}>
                  <div className="equipment-card-top">
                    <div className="equipment-icon" aria-hidden="true">
                      🔬
                    </div>

                    <span
                      className={`equipment-status status-badge-${status.toLowerCase()}`}
                    >
                      {status.replaceAll('_', ' ')}
                    </span>
                  </div>

                  <span className="equipment-category">
                    {item.category || 'Uncategorized'}
                  </span>

                  <h2>{item.name || `Equipment #${item.id}`}</h2>

                  <p className="equipment-description">
                    {item.description || 'No description provided.'}
                  </p>

                  <div className="equipment-details">
                    <div>
                      <span>Location</span>
                      <strong>
                        {item.labLocation || 'Not specified'}
                      </strong>
                    </div>

                    <div>
                      <span>Quantity</span>
                      <strong>{item.quantity ?? '—'}</strong>
                    </div>

                    <div>
                      <span>Institution</span>
                      <strong>{item.institutionId ?? '—'}</strong>
                    </div>
                  </div>
                </article>
              )
            })}
          </div>
        </>
      ) : null}

      <p className="module-footnote">
        Signed-in account institution ID: {user?.institutionId ?? '—'}.
        Equipment records are returned by the configured backend API.
      </p>
    </div>
  )
}
