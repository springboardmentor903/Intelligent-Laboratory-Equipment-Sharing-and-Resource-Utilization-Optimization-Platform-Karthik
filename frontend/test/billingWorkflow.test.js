import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canViewCharges,
  canViewMyCharges,
  canViewCostOverview,
  canViewBudgets,
  canManageBudgets,
  canManageInvoices,
  isInvoiceOverdue,
  getBudgetStatusBadge,
  getInvoiceStatusBadge,
  getChargeStatusBadge,
  getChargeTypeBadge,
  getFiscalYearOptions,
  validateBudgetForm,
  validateInvoiceGeneration,
} from '../src/access/billingWorkflow.js'

test('billing role access controls match security specifications', () => {
  // canViewCharges: LAB_MANAGER, INSTITUTION_ADMIN, SYSTEM_ADMIN
  assert.equal(canViewCharges(['LAB_MANAGER']), true)
  assert.equal(canViewCharges(['INSTITUTION_ADMIN']), true)
  assert.equal(canViewCharges(['SYSTEM_ADMIN']), true)
  assert.equal(canViewCharges(['RESEARCHER_STUDENT']), false)
  assert.equal(canViewCharges(['LAB_TECHNICIAN']), false)

  // canViewMyCharges: all authenticated roles
  assert.equal(canViewMyCharges(['RESEARCHER_STUDENT']), true)
  assert.equal(canViewMyCharges(['LAB_TECHNICIAN']), true)
  assert.equal(canViewMyCharges([]), false)
  assert.equal(canViewMyCharges(null), false)

  // canViewCostOverview: LAB_MANAGER, INSTITUTION_ADMIN, SYSTEM_ADMIN, DEPARTMENT_HEAD
  assert.equal(canViewCostOverview(['DEPARTMENT_HEAD']), true)
  assert.equal(canViewCostOverview(['LAB_MANAGER']), true)
  assert.equal(canViewCostOverview(['RESEARCHER_STUDENT']), false)

  // canViewBudgets: DEPARTMENT_HEAD, INSTITUTION_ADMIN, SYSTEM_ADMIN
  assert.equal(canViewBudgets(['DEPARTMENT_HEAD']), true)
  assert.equal(canViewBudgets(['INSTITUTION_ADMIN']), true)
  assert.equal(canViewBudgets(['LAB_TECHNICIAN']), false)

  // canManageBudgets: INSTITUTION_ADMIN, SYSTEM_ADMIN (DEPARTMENT_HEAD is read-only)
  assert.equal(canManageBudgets(['INSTITUTION_ADMIN']), true)
  assert.equal(canManageBudgets(['SYSTEM_ADMIN']), true)
  assert.equal(canManageBudgets(['DEPARTMENT_HEAD']), false)

  // canManageInvoices: INSTITUTION_ADMIN, SYSTEM_ADMIN
  assert.equal(canManageInvoices(['INSTITUTION_ADMIN']), true)
  assert.equal(canManageInvoices(['SYSTEM_ADMIN']), true)
  assert.equal(canManageInvoices(['LAB_MANAGER']), false)
})

test('isInvoiceOverdue calculates dynamic overdue condition accurately', () => {
  const refDate = new Date('2026-11-25T10:00:00Z')

  // Past due date and ISSUED -> overdue
  assert.equal(isInvoiceOverdue({ dueDate: '2026-11-20', status: 'ISSUED' }, refDate), true)
  // Past due date and DRAFT -> overdue
  assert.equal(isInvoiceOverdue({ dueDate: '2026-11-20', status: 'DRAFT' }, refDate), true)
  // Past due date but PAID -> NOT overdue
  assert.equal(isInvoiceOverdue({ dueDate: '2026-11-20', status: 'PAID' }, refDate), false)
  // Past due date but CANCELLED -> NOT overdue
  assert.equal(isInvoiceOverdue({ dueDate: '2026-11-20', status: 'CANCELLED' }, refDate), false)
  // Future due date -> NOT overdue
  assert.equal(isInvoiceOverdue({ dueDate: '2026-11-30', status: 'ISSUED' }, refDate), false)
  // Today's date -> NOT overdue
  assert.equal(isInvoiceOverdue({ dueDate: '2026-11-25', status: 'ISSUED' }, refDate), false)
})

test('getBudgetStatusBadge maps dynamic budget threshold states', () => {
  assert.equal(getBudgetStatusBadge('OK').label, 'OK')
  assert.match(getBudgetStatusBadge('WARNING').label, /80%/)
  assert.match(getBudgetStatusBadge('EXCEEDED').label, /100%/)
  assert.equal(getBudgetStatusBadge('NO_BUDGET').label, 'No Budget')
  assert.equal(getBudgetStatusBadge(null).label, 'No Budget')
})

test('getInvoiceStatusBadge flags overdue invoices and maps valid statuses', () => {
  const refDate = new Date('2026-11-25T10:00:00Z')

  const draft = getInvoiceStatusBadge({ status: 'DRAFT', dueDate: '2026-12-01' }, refDate)
  assert.equal(draft.label, 'Draft')
  assert.equal(draft.isOverdue, false)

  const issued = getInvoiceStatusBadge({ status: 'ISSUED', dueDate: '2026-12-01' }, refDate)
  assert.equal(issued.label, 'Issued')
  assert.equal(issued.isOverdue, false)

  const overdue = getInvoiceStatusBadge({ status: 'ISSUED', dueDate: '2026-11-10' }, refDate)
  assert.match(overdue.label, /OVERDUE/)
  assert.equal(overdue.isOverdue, true)

  const paid = getInvoiceStatusBadge({ status: 'PAID', dueDate: '2026-11-10' }, refDate)
  assert.equal(paid.label, 'Paid')
  assert.equal(paid.isOverdue, false)
})

test('getChargeStatusBadge and getChargeTypeBadge return human-readable badges', () => {
  assert.equal(getChargeStatusBadge('PENDING').label, 'Pending')
  assert.equal(getChargeStatusBadge('INVOICED').label, 'Invoiced')
  assert.equal(getChargeStatusBadge('PAID').label, 'Paid')
  assert.equal(getChargeStatusBadge('WAIVED').label, 'Waived')

  assert.equal(getChargeTypeBadge('INTERNAL').label, 'Internal')
  assert.equal(getChargeTypeBadge('EXTERNAL').label, 'External')
  assert.equal(getChargeTypeBadge('UNASSIGNED').label, 'Unassigned')
})

test('getFiscalYearOptions generates surrounding years around reference date', () => {
  const years = getFiscalYearOptions(new Date('2026-05-10'))
  assert.deepEqual(years, [2024, 2025, 2026, 2027])
})

test('validateBudgetForm validates required fields and bounds', () => {
  const invalid = validateBudgetForm({ departmentId: '', fiscalYear: '', budgetAmount: -5 })
  assert.equal(invalid.isValid, false)
  assert.ok(invalid.errors.departmentId)
  assert.ok(invalid.errors.fiscalYear)
  assert.ok(invalid.errors.budgetAmount)

  const valid = validateBudgetForm({ departmentId: 10, fiscalYear: 2026, budgetAmount: 500000 })
  assert.equal(valid.isValid, true)
  assert.deepEqual(valid.errors, {})
})

test('validateInvoiceGeneration checks mandatory parameters per invoice type', () => {
  // INTER_INSTITUTION requires clientInstitutionId
  const missingClient = validateInvoiceGeneration({
    invoiceType: 'INTER_INSTITUTION',
    clientInstitutionId: '',
    dueDate: '2026-12-31',
  })
  assert.equal(missingClient.isValid, false)
  assert.ok(missingClient.errors.clientInstitutionId)

  // DEPARTMENT_CHARGEBACK requires departmentId
  const missingDept = validateInvoiceGeneration({
    invoiceType: 'DEPARTMENT_CHARGEBACK',
    departmentId: '',
    dueDate: '2026-12-31',
  })
  assert.equal(missingDept.isValid, false)
  assert.ok(missingDept.errors.departmentId)

  // Valid invoice request
  const valid = validateInvoiceGeneration({
    invoiceType: 'INTER_INSTITUTION',
    clientInstitutionId: 4,
    dueDate: '2026-12-31',
  })
  assert.equal(valid.isValid, true)
  assert.deepEqual(valid.errors, {})
})
