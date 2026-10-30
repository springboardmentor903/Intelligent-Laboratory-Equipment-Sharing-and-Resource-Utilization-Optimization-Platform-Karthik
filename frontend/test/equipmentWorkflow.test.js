import assert from 'node:assert/strict'
import test from 'node:test'
import {
  buildEquipmentSearchParams,
  EQUIPMENT_STATUSES,
  getEquipmentPermissions,
  validateCalibrationForm,
  validateCertificationForm,
  validateEquipmentForm,
} from '../src/access/equipmentWorkflow.js'

test('equipment search builds only supported, nonempty backend parameters', () => {
  assert.deepEqual(buildEquipmentSearchParams({
    name: '  centrifuge ', category: '', description: ' optics ', labLocation: '  ',
    status: ' AVAILABLE ', tag: ' Research ', institutionId: '12', unsupported: 'ignore',
  }), { name: 'centrifuge', description: 'optics', status: 'AVAILABLE', tag: 'Research', institutionId: 12 })
  assert.deepEqual(buildEquipmentSearchParams({ name: '', institutionId: 'invalid' }), {})
})

test('equipment mutation and record creation visibility matches backend roles', () => {
  assert.deepEqual(getEquipmentPermissions(['RESEARCHER_STUDENT']), {
    canCreate: false, canEdit: false, canDelete: false,
    canCreateRecords: false, canFilterInstitution: false,
  })
  assert.equal(getEquipmentPermissions(['LAB_TECHNICIAN']).canCreate, true)
  assert.equal(getEquipmentPermissions(['LAB_TECHNICIAN']).canEdit, true)
  assert.equal(getEquipmentPermissions(['LAB_TECHNICIAN']).canDelete, false)
  assert.equal(getEquipmentPermissions(['LAB_TECHNICIAN']).canCreateRecords, true)
  assert.equal(getEquipmentPermissions(['LAB_MANAGER']).canDelete, true)
  assert.equal(getEquipmentPermissions(['INSTITUTION_ADMIN']).canCreateRecords, true)
  assert.equal(getEquipmentPermissions(['SYSTEM_ADMIN']).canFilterInstitution, true)
  assert.equal(getEquipmentPermissions(['DEPARTMENT_HEAD']).canEdit, false)
  assert.equal(getEquipmentPermissions(undefined).canCreate, false)
})

test('equipment status values match the mentor inventory vocabulary', () => {
  assert.deepEqual(EQUIPMENT_STATUSES, [
    'AVAILABLE', 'BOOKED', 'UNDER_MAINTENANCE', 'OUT_OF_SERVICE', 'RETIRED',
  ])
  const invalid = validateEquipmentForm({
    name: 'scope', institutionId: '1', quantity: '1', status: 'BROKEN', specificationsText: '{}',
  })
  assert.ok(invalid.errors.some((message) => message.includes('Status must be one of')))
})

test('equipment form validates service limits and produces the entity request fields', () => {
  const { errors, body } = validateEquipmentForm({
    name: '  Microscope ', category: ' optics ', description: '', institutionId: '4',
    labLocation: ' Room A ', quantity: '2', status: '', tags: ' Imaging, optics ',
    specificationsText: '{"magnification": 40}',
  })
  assert.deepEqual(errors, [])
  assert.deepEqual(body, {
    name: 'Microscope', category: 'optics', description: null, institutionId: 4,
    labLocation: 'Room A', quantity: 2, status: null,
    specifications: { magnification: 40 }, tags: ['Imaging', 'optics'],
  })

  const invalid = validateEquipmentForm({ name: '', institutionId: '0', quantity: '0', specificationsText: '[]' })
  assert.ok(invalid.errors.some((message) => message.includes('Equipment name is required')))
  assert.ok(invalid.errors.some((message) => message.includes('institution ID')))
  assert.ok(invalid.errors.some((message) => message.includes('Quantity')))
  assert.ok(invalid.errors.some((message) => message.includes('JSON object')))
})

test('calibration request validates required dates, order, and result enum', () => {
  const valid = validateCalibrationForm({
    calibrationDate: '2026-01-10', nextDueDate: '2027-01-10', result: 'pass', notes: ' checked ',
  })
  assert.deepEqual(valid.errors, [])
  assert.deepEqual(valid.body, {
    calibrationDate: '2026-01-10', nextDueDate: '2027-01-10', result: 'PASS', notes: 'checked',
  })
  const invalid = validateCalibrationForm({
    calibrationDate: '2026-02-30', nextDueDate: '2026-02-29', result: 'UNKNOWN', notes: 'x'.repeat(1001),
  })
  assert.ok(invalid.errors.some((message) => message.includes('valid calibration date')))
  assert.ok(invalid.errors.some((message) => message.includes('after the calibration date')))
  assert.ok(invalid.errors.some((message) => message.includes('PASS, FAIL, or CONDITIONAL')))
  assert.ok(invalid.errors.some((message) => message.includes('1000 characters')))
})

test('certification request validates entity fields, date order, and reference length', () => {
  const valid = validateCertificationForm({
    certificateType: ' ISO  ', issuingOrganization: ' Lab Inc ',
    issueDate: '2026-02-01', expiryDate: '2027-02-01', documentReference: ' DOC-9 ',
  })
  assert.deepEqual(valid.errors, [])
  assert.deepEqual(valid.body, {
    certificateType: 'ISO', issuingOrganization: 'Lab Inc', issueDate: '2026-02-01',
    expiryDate: '2027-02-01', documentReference: 'DOC-9',
  })
  const invalid = validateCertificationForm({
    certificateType: '', issuingOrganization: ' ', issueDate: '2026-02-30',
    expiryDate: '2026-02-30', documentReference: 'x'.repeat(501),
  })
  assert.ok(invalid.errors.some((message) => message.includes('Certificate type is required')))
  assert.ok(invalid.errors.some((message) => message.includes('Issuing organization is required')))
  assert.ok(invalid.errors.some((message) => message.includes('valid issue date')))
  assert.ok(invalid.errors.some((message) => message.includes('after the issue date')))
  assert.ok(invalid.errors.some((message) => message.includes('500 characters')))
})
