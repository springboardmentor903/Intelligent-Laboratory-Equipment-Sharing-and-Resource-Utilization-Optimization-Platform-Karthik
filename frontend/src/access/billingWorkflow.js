import { hasAnyRole, hasValidRoles } from './roleAccess.js'

export const CHARGE_TYPES = Object.freeze(['INTERNAL', 'EXTERNAL', 'UNASSIGNED'])
export const CHARGE_STATUSES = Object.freeze(['PENDING', 'INVOICED', 'PAID', 'WAIVED'])
export const INVOICE_TYPES = Object.freeze(['INTER_INSTITUTION', 'DEPARTMENT_CHARGEBACK'])
export const INVOICE_STATUSES = Object.freeze(['DRAFT', 'ISSUED', 'PAID', 'CANCELLED'])
export const BUDGET_STATUSES = Object.freeze(['OK', 'WARNING', 'EXCEEDED', 'NO_BUDGET'])

export function canViewCharges(roles) {
  return hasAnyRole(roles, ['LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN'])
}

export function canViewMyCharges(roles) {
  return hasValidRoles(roles)
}

export function canViewCostOverview(roles) {
  return hasAnyRole(roles, ['LAB_MANAGER', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN', 'DEPARTMENT_HEAD'])
}

export function canViewBudgets(roles) {
  return hasAnyRole(roles, ['DEPARTMENT_HEAD', 'INSTITUTION_ADMIN', 'SYSTEM_ADMIN'])
}

export function canManageBudgets(roles) {
  return hasAnyRole(roles, ['INSTITUTION_ADMIN', 'SYSTEM_ADMIN'])
}

export function canManageInvoices(roles) {
  return hasAnyRole(roles, ['INSTITUTION_ADMIN', 'SYSTEM_ADMIN'])
}

export function isInvoiceOverdue(invoice, referenceDate = new Date()) {
  if (!invoice || !invoice.dueDate) return false
  if (invoice.status === 'PAID' || invoice.status === 'CANCELLED') return false
  const due = new Date(invoice.dueDate)
  const ref = new Date(referenceDate)
  // Compare date parts only (YYYY-MM-DD)
  const dueDateStr = due.toISOString().slice(0, 10)
  const refDateStr = ref.toISOString().slice(0, 10)
  return dueDateStr < refDateStr
}

export function getBudgetStatusBadge(status) {
  switch (status) {
    case 'OK':
      return { label: 'OK', className: 'status-badge-ok', color: '#16a34a', bg: '#dcfce7' }
    case 'WARNING':
      return { label: 'Warning (≥80%)', className: 'status-badge-warning', color: '#d97706', bg: '#fef3c7' }
    case 'EXCEEDED':
      return { label: 'Exceeded (>100%)', className: 'status-badge-exceeded', color: '#dc2626', bg: '#fee2e2' }
    case 'NO_BUDGET':
    default:
      return { label: 'No Budget', className: 'status-badge-neutral', color: '#64748b', bg: '#f1f5f9' }
  }
}

export function getInvoiceStatusBadge(invoice, referenceDate = new Date()) {
  if (!invoice) return { label: 'Unknown', className: 'status-badge-neutral' }
  const overdue = isInvoiceOverdue(invoice, referenceDate)
  if (overdue) {
    return { label: `${invoice.status} (OVERDUE)`, className: 'status-badge-overdue', isOverdue: true, color: '#dc2626', bg: '#fee2e2' }
  }
  switch (invoice.status) {
    case 'DRAFT':
      return { label: 'Draft', className: 'status-badge-draft', isOverdue: false, color: '#64748b', bg: '#f1f5f9' }
    case 'ISSUED':
      return { label: 'Issued', className: 'status-badge-issued', isOverdue: false, color: '#0284c7', bg: '#e0f2fe' }
    case 'PAID':
      return { label: 'Paid', className: 'status-badge-paid', isOverdue: false, color: '#16a34a', bg: '#dcfce7' }
    case 'CANCELLED':
      return { label: 'Cancelled', className: 'status-badge-cancelled', isOverdue: false, color: '#94a3b8', bg: '#f8fafc' }
    default:
      return { label: invoice.status || 'Unknown', className: 'status-badge-neutral', isOverdue: false, color: '#64748b', bg: '#f1f5f9' }
  }
}

export function getChargeStatusBadge(status) {
  switch (status) {
    case 'PENDING':
      return { label: 'Pending', className: 'status-badge-pending', color: '#d97706', bg: '#fef3c7' }
    case 'INVOICED':
      return { label: 'Invoiced', className: 'status-badge-invoiced', color: '#0284c7', bg: '#e0f2fe' }
    case 'PAID':
      return { label: 'Paid', className: 'status-badge-paid', color: '#16a34a', bg: '#dcfce7' }
    case 'WAIVED':
      return { label: 'Waived', className: 'status-badge-waived', color: '#64748b', bg: '#f1f5f9' }
    default:
      return { label: status || 'Unknown', className: 'status-badge-neutral', color: '#64748b', bg: '#f1f5f9' }
  }
}

export function getChargeTypeBadge(type) {
  switch (type) {
    case 'INTERNAL':
      return { label: 'Internal', className: 'type-badge-internal', color: '#0d9488', bg: '#ccfbf1' }
    case 'EXTERNAL':
      return { label: 'External', className: 'type-badge-external', color: '#7c3aed', bg: '#ede9fe' }
    case 'UNASSIGNED':
      return { label: 'Unassigned', className: 'type-badge-unassigned', color: '#ea580c', bg: '#ffedd5' }
    default:
      return { label: type || 'Unknown', className: 'type-badge-neutral', color: '#64748b', bg: '#f1f5f9' }
  }
}

export function getFiscalYearOptions(referenceDate = new Date()) {
  const currentYear = new Date(referenceDate).getFullYear()
  return [currentYear - 2, currentYear - 1, currentYear, currentYear + 1]
}

export function validateBudgetForm({ departmentId, fiscalYear, budgetAmount }) {
  const errors = {}
  if (!departmentId) {
    errors.departmentId = 'Department is required'
  }
  const yearNum = Number(fiscalYear)
  if (!fiscalYear || !Number.isInteger(yearNum) || yearNum < 2000 || yearNum > 2100) {
    errors.fiscalYear = 'Valid fiscal year is required (2000 - 2100)'
  }
  const amountNum = Number(budgetAmount)
  if (budgetAmount == null || isNaN(amountNum) || amountNum <= 0) {
    errors.budgetAmount = 'Budget amount must be greater than zero'
  }
  return { isValid: Object.keys(errors).length === 0, errors }
}

export function validateInvoiceGeneration({ invoiceType, clientInstitutionId, departmentId, dueDate }) {
  const errors = {}
  if (!invoiceType || !INVOICE_TYPES.includes(invoiceType)) {
    errors.invoiceType = 'Valid invoice type is required'
  }
  if (invoiceType === 'INTER_INSTITUTION' && !clientInstitutionId) {
    errors.clientInstitutionId = 'Client institution is required for inter-institution invoices'
  }
  if (invoiceType === 'DEPARTMENT_CHARGEBACK' && !departmentId) {
    errors.departmentId = 'Department is required for department chargeback invoices'
  }
  if (!dueDate) {
    errors.dueDate = 'Due date is required'
  }
  return { isValid: Object.keys(errors).length === 0, errors }
}
