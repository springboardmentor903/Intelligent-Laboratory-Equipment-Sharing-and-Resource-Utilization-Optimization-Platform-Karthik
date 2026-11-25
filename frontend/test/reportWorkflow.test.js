import assert from 'node:assert/strict'
import test from 'node:test'
import {
  canViewReports,
  getAllowedReportTypes,
  formatReportCellValue,
  REPORT_TYPES,
} from '../src/access/reportWorkflow.js'

test('canViewReports allows institution admin, lab manager, department head, and system admin', () => {
  assert.equal(canViewReports(['INSTITUTION_ADMIN']), true)
  assert.equal(canViewReports(['SYSTEM_ADMIN']), true)
  assert.equal(canViewReports(['LAB_MANAGER']), true)
  assert.equal(canViewReports(['DEPARTMENT_HEAD']), true)

  assert.equal(canViewReports(['RESEARCHER_STUDENT']), false)
  assert.equal(canViewReports(['LAB_TECHNICIAN']), false)
  assert.equal(canViewReports([]), false)
  assert.equal(canViewReports(null), false)
  assert.equal(canViewReports(undefined), false)
})

test('getAllowedReportTypes returns appropriate subsets of reports per role', () => {
  const adminReports = getAllowedReportTypes(['INSTITUTION_ADMIN']).map((r) => r.type)
  assert.deepEqual(adminReports, [
    'UTILIZATION',
    'DEPARTMENT_USAGE',
    'MAINTENANCE_DOWNTIME',
    'INTER_INSTITUTION_SHARING',
    'PROCUREMENT_COST',
  ])

  const managerReports = getAllowedReportTypes(['LAB_MANAGER']).map((r) => r.type)
  assert.deepEqual(managerReports, ['UTILIZATION', 'MAINTENANCE_DOWNTIME'])

  const deptHeadReports = getAllowedReportTypes(['DEPARTMENT_HEAD']).map((r) => r.type)
  assert.deepEqual(deptHeadReports, ['UTILIZATION', 'DEPARTMENT_USAGE'])

  const studentReports = getAllowedReportTypes(['RESEARCHER_STUDENT']).map((r) => r.type)
  assert.deepEqual(studentReports, [])
})

test('formatReportCellValue correctly formats currency, percentage, numbers, and dates', () => {
  assert.equal(formatReportCellValue(12500, 'CURRENCY'), '₹12,500.00')
  assert.equal(formatReportCellValue(75.54, 'PERCENTAGE'), '75.5%')
  assert.equal(formatReportCellValue(1500, 'NUMBER'), '1,500')
  assert.equal(formatReportCellValue(null, 'CURRENCY'), '—')
  assert.equal(formatReportCellValue(undefined, 'PERCENTAGE'), '—')
  assert.equal(formatReportCellValue('Available', 'TEXT'), 'Available')
})
