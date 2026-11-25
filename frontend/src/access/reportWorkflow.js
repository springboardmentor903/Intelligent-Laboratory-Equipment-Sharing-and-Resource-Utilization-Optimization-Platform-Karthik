export const REPORT_TYPES = [
  {
    type: 'UTILIZATION',
    title: 'Equipment Utilization Report',
    description: 'Usage hours, booking counts, and daily utilization rates against target capacity.',
    icon: '📊',
    roles: ['INSTITUTION_ADMIN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'SYSTEM_ADMIN'],
  },
  {
    type: 'DEPARTMENT_USAGE',
    title: 'Department Usage & Cost Report',
    description: 'Booking breakdown, total hours, and internal chargeback costs per department.',
    icon: '🏢',
    roles: ['INSTITUTION_ADMIN', 'DEPARTMENT_HEAD', 'SYSTEM_ADMIN'],
  },
  {
    type: 'MAINTENANCE_DOWNTIME',
    title: 'Maintenance & Downtime Report',
    description: 'Downtime logs, work order counts, and maintenance expenditure per equipment.',
    icon: '🔧',
    roles: ['INSTITUTION_ADMIN', 'LAB_MANAGER', 'SYSTEM_ADMIN'],
  },
  {
    type: 'INTER_INSTITUTION_SHARING',
    title: 'Inter-Institution Sharing Report',
    description: 'Partner agreements, external booking hours, and accrued sharing revenue.',
    icon: '🤝',
    roles: ['INSTITUTION_ADMIN', 'SYSTEM_ADMIN'],
  },
  {
    type: 'PROCUREMENT_COST',
    title: 'Procurement & Lifecycle ROI Report',
    description: 'Purchase cost, cumulative maintenance, usage earnings, and lifecycle Return on Investment (ROI).',
    icon: '💰',
    roles: ['INSTITUTION_ADMIN', 'SYSTEM_ADMIN'],
  },
]

export function canViewReports(roles) {
  if (!Array.isArray(roles)) return false
  return roles.some((role) =>
    ['INSTITUTION_ADMIN', 'LAB_MANAGER', 'DEPARTMENT_HEAD', 'SYSTEM_ADMIN'].includes(role)
  )
}

export function getAllowedReportTypes(roles) {
  if (!Array.isArray(roles)) return []
  return REPORT_TYPES.filter((r) => r.roles.some((role) => roles.includes(role)))
}

export function formatReportCellValue(val, type) {
  if (val === null || val === undefined) return '—'
  if (type === 'CURRENCY') {
    return `₹${Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
  }
  if (type === 'PERCENTAGE') {
    return `${Number(val).toFixed(1)}%`
  }
  if (type === 'NUMBER') {
    return typeof val === 'number' ? Number(val).toLocaleString() : val
  }
  if (type === 'DATE' || type === 'DATETIME') {
    try {
      const d = new Date(val)
      return isNaN(d.getTime()) ? String(val) : d.toLocaleDateString()
    } catch {
      return String(val)
    }
  }
  return String(val)
}

export async function downloadReportFile(apiClient, type, format, params = {}) {
  const queryParams = new URLSearchParams()
  queryParams.append('format', format)
  if (params.institutionId) queryParams.append('institutionId', params.institutionId)
  if (params.departmentId) queryParams.append('departmentId', params.departmentId)
  if (params.startDate) queryParams.append('startDate', params.startDate)
  if (params.endDate) queryParams.append('endDate', params.endDate)

  const response = await apiClient.get(`/api/reports/${type}/export?${queryParams.toString()}`, {
    responseType: 'blob',
  })

  // Extract filename from Content-Disposition if present
  let filename = `${type.toLowerCase()}_report.${format === 'xlsx' ? 'xlsx' : 'pdf'}`
  const disposition = response.headers?.['content-disposition'] || response.headers?.get?.('content-disposition')
  if (disposition) {
    const filenameMatch = disposition.match(/filename=["']?([^"']+)["']?/)
    if (filenameMatch && filenameMatch[1]) {
      filename = filenameMatch[1]
    }
  }

  const blob = new Blob([response.data], {
    type: format === 'xlsx'
      ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      : 'application/pdf',
  })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', filename)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

export async function downloadInvoicePdf(apiClient, invoiceId, invoiceNumber) {
  const response = await apiClient.get(`/api/billing/invoices/${invoiceId}/pdf`, {
    responseType: 'blob',
  })

  const filename = invoiceNumber ? `invoice_${invoiceNumber}.pdf` : `invoice_${invoiceId}.pdf`
  const blob = new Blob([response.data], { type: 'application/pdf' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', filename)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}
