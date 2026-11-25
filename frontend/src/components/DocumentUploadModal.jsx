import { useState, useEffect } from 'react'
import api from '../api/axiosInstance'
import { validateDocumentUpload } from '../access/maintenanceWorkflow.js'

export default function DocumentUploadModal({
  isOpen,
  onClose,
  entityType = 'CALIBRATION_CERTIFICATE',
  entityId,
  title = 'Document Attachments',
  onSuccess,
}) {
  const [file, setFile] = useState(null)
  const [uploading, setUploading] = useState(false)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [error, setError] = useState('')
  const [successMsg, setSuccessMsg] = useState('')
  const [documents, setDocuments] = useState([])
  const [loadingDocs, setLoadingDocs] = useState(false)
  const [viewingId, setViewingId] = useState(null)

  useEffect(() => {
    if (isOpen) {
      setError('')
      setSuccessMsg('')
      setFile(null)
      setUploadProgress(0)
      if (entityId) {
        fetchDocuments()
      } else {
        setDocuments([])
      }
    }
  }, [isOpen, entityType, entityId])

  async function fetchDocuments() {
    if (!entityId) return
    setLoadingDocs(true)
    try {
      const res = await api.get(`/api/documents/entity/${encodeURIComponent(entityType)}/${encodeURIComponent(entityId)}`)
      setDocuments(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      console.warn('Could not load documents for entity', err)
    } finally {
      setLoadingDocs(false)
    }
  }

  function handleFileSelect(e) {
    const selected = e.target.files?.[0]
    setError('')
    setSuccessMsg('')
    if (!selected) {
      setFile(null)
      return
    }

    const validationError = validateDocumentUpload(selected, 10)
    if (validationError) {
      setError(validationError)
      setFile(null)
      e.target.value = ''
      return
    }

    setFile(selected)
  }

  async function handleUpload(e) {
    e.preventDefault()
    if (!file) {
      setError('Please choose a file to upload.')
      return
    }

    const validationError = validateDocumentUpload(file, 10)
    if (validationError) {
      setError(validationError)
      return
    }

    setUploading(true)
    setUploadProgress(0)
    setError('')
    setSuccessMsg('')

    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('entityType', entityType)
      if (entityId) {
        formData.append('entityId', entityId)
      }

      const res = await api.post('/api/documents/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (progressEvent) => {
          if (progressEvent.total) {
            const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
            setUploadProgress(percent)
          }
        },
      })

      setSuccessMsg(`"${file.name}" uploaded successfully! Valid 10-minute access link generated.`)
      setFile(null)
      setUploadProgress(100)
      if (onSuccess) {
        onSuccess(res.data)
      }
      if (entityId) {
        fetchDocuments()
      } else {
        setDocuments((prev) => [res.data, ...prev])
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'File upload failed.'
      setError(msg)
    } finally {
      setUploading(false)
    }
  }

  async function handleView(doc) {
    setViewingId(doc.id)
    setError('')
    try {
      const res = await api.get(`/api/documents/${doc.id}/access-url`)
      const url = res.data?.accessUrl
      if (url) {
        window.open(url, '_blank', 'noopener,noreferrer')
      } else {
        setError('Could not retrieve access URL for this document.')
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Failed to open document.'
      setError(msg)
    } finally {
      setViewingId(null)
    }
  }

  async function handleDelete(docId) {
    if (!window.confirm('Are you sure you want to delete this document?')) return
    try {
      await api.delete(`/api/documents/${docId}`)
      setDocuments((prev) => prev.filter((d) => d.id !== docId))
      setSuccessMsg('Document deleted successfully.')
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete document.')
    }
  }

  if (!isOpen) return null

  return (
    <div className="modal-backdrop" onClick={onClose} role="presentation">
      <div
        className="modal-card max-w-xl w-full p-6"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="doc-modal-title"
      >
        <div className="flex items-center justify-between pb-4 border-b border-slate-200 dark:border-slate-800">
          <div className="flex items-center gap-2">
            <span className="text-xl" aria-hidden="true">📎</span>
            <h3 id="doc-modal-title" className="text-lg font-bold text-slate-900 dark:text-slate-100">
              {title}
            </h3>
          </div>
          <button
            type="button"
            className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200 text-xl font-bold p-1 leading-none rounded"
            onClick={onClose}
            aria-label="Close dialog"
          >
            ✕
          </button>
        </div>

        {error && (
          <div className="mt-4 p-3 rounded-lg bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 text-sm font-medium border border-rose-200 dark:border-rose-900" role="alert">
            {error}
          </div>
        )}

        {successMsg && (
          <div className="mt-4 p-3 rounded-lg bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 text-sm font-medium border border-emerald-200 dark:border-emerald-900" role="status">
            {successMsg}
          </div>
        )}

        {/* Upload Form */}
        <form onSubmit={handleUpload} className="mt-4 space-y-4">
          <div className="p-4 border-2 border-dashed border-slate-300 dark:border-slate-700 rounded-xl bg-slate-50 dark:bg-slate-900/50 text-center">
            <input
              type="file"
              id="doc-file-input"
              className="hidden"
              accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
              onChange={handleFileSelect}
              disabled={uploading}
            />
            <label
              htmlFor="doc-file-input"
              className="cursor-pointer block text-sm font-medium text-slate-700 dark:text-slate-300"
            >
              <span className="text-2xl block mb-1">📄</span>
              {file ? (
                <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                  {file.name} ({(file.size / (1024 * 1024)).toFixed(2)} MB)
                </span>
              ) : (
                <>
                  <span className="text-indigo-600 dark:text-indigo-400 font-semibold hover:underline">
                    Click to select file
                  </span>{' '}
                  or drag and drop
                </>
              )}
              <span className="block text-xs text-slate-500 dark:text-slate-400 mt-1">
                Supported formats: PDF, PNG, JPEG (Max: 10 MB)
              </span>
            </label>
          </div>

          {uploading && (
            <div className="space-y-1">
              <div className="flex justify-between text-xs text-slate-600 dark:text-slate-400">
                <span>Uploading…</span>
                <span>{uploadProgress}%</span>
              </div>
              <div className="w-full bg-slate-200 dark:bg-slate-700 h-2 rounded-full overflow-hidden">
                <div
                  className="bg-indigo-600 h-2 transition-all duration-200"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              className="px-4 py-2 text-sm font-medium rounded-lg border border-slate-300 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
              onClick={onClose}
              disabled={uploading}
            >
              Close
            </button>
            <button
              type="submit"
              className="px-4 py-2 text-sm font-semibold rounded-lg bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50 transition"
              disabled={!file || uploading}
            >
              {uploading ? 'Uploading…' : 'Upload Document'}
            </button>
          </div>
        </form>

        {/* Document list */}
        <div className="mt-6 border-t border-slate-200 dark:border-slate-800 pt-4">
          <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100 mb-2">
            Attached Documents ({documents.length})
          </h4>

          {loadingDocs ? (
            <p className="text-xs text-slate-500 py-3 text-center">Loading attached files…</p>
          ) : documents.length === 0 ? (
            <p className="text-xs text-slate-500 dark:text-slate-400 py-3 text-center italic">
              No documents attached yet.
            </p>
          ) : (
            <ul className="divide-y divide-slate-100 dark:divide-slate-800 max-h-48 overflow-y-auto">
              {documents.map((doc) => (
                <li key={doc.id} className="py-2.5 flex items-center justify-between gap-3 text-sm">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-slate-800 dark:text-slate-200 truncate">
                      {doc.fileName}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400">
                      {(doc.fileSize / (1024 * 1024)).toFixed(2)} MB • {doc.createdAt ? new Date(doc.createdAt).toLocaleDateString() : 'Recent'}
                      {doc.uploaderName ? ` • by ${doc.uploaderName}` : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      type="button"
                      className="px-2.5 py-1 text-xs font-semibold rounded bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300 hover:bg-indigo-100 dark:hover:bg-indigo-900 transition flex items-center gap-1"
                      onClick={() => handleView(doc)}
                      disabled={viewingId === doc.id}
                      title="Request fresh 10-minute access link and view"
                    >
                      {viewingId === doc.id ? 'Loading…' : '👁️ View (10m Link)'}
                    </button>
                    <button
                      type="button"
                      className="px-2 py-1 text-xs font-medium rounded text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/40 transition"
                      onClick={() => handleDelete(doc.id)}
                      title="Delete document"
                    >
                      🗑️
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}
