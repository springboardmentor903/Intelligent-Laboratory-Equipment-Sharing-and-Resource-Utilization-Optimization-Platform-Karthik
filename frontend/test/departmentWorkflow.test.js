import assert from 'node:assert/strict'
import test from 'node:test'
import {
  canEditUserDepartment,
  canManageDepartments,
  validateDepartmentName,
} from '../src/access/departmentWorkflow.js'

test('department management and editing are restricted strictly to administrator roles', () => {
  assert.equal(canManageDepartments(['INSTITUTION_ADMIN']), true)
  assert.equal(canManageDepartments(['SYSTEM_ADMIN']), true)
  assert.equal(canManageDepartments(['RESEARCHER_STUDENT']), false)
  assert.equal(canManageDepartments(['LAB_TECHNICIAN']), false)
  assert.equal(canManageDepartments(['LAB_MANAGER']), false)
  assert.equal(canManageDepartments(['DEPARTMENT_HEAD']), false)
  assert.equal(canManageDepartments([]), false)

  assert.equal(canEditUserDepartment(['INSTITUTION_ADMIN']), true)
  assert.equal(canEditUserDepartment(['SYSTEM_ADMIN']), true)
  assert.equal(canEditUserDepartment(['RESEARCHER_STUDENT']), false)
  assert.equal(canEditUserDepartment(['LAB_TECHNICIAN']), false)
  assert.equal(canEditUserDepartment(['LAB_MANAGER']), false)
})

test('validateDepartmentName enforces non-empty, max length, and unique department name', () => {
  const existing = [
    { id: 1, name: 'Biochemistry' },
    { id: 2, name: 'Physics' },
  ]

  assert.equal(validateDepartmentName(''), 'Department name is required.')
  assert.equal(validateDepartmentName('   '), 'Department name is required.')
  assert.equal(validateDepartmentName('A'.repeat(151)), 'Department name must be 150 characters or fewer.')

  // Duplicate name checks (case-insensitive)
  assert.equal(
    validateDepartmentName('Biochemistry', existing),
    'A department with this name already exists in this institution.',
  )
  assert.equal(
    validateDepartmentName('biochemistry', existing),
    'A department with this name already exists in this institution.',
  )
  assert.equal(
    validateDepartmentName('  PHYSICS  ', existing),
    'A department with this name already exists in this institution.',
  )

  // Editing existing department should allow keeping its own name
  assert.equal(validateDepartmentName('Biochemistry', existing, 1), null)
  assert.equal(validateDepartmentName('Chemistry', existing), null)
})
