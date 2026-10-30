import { hasValidRoles } from './roleAccess.js'

export const PENDING_MEMBERSHIPS_URL = '/api/users/pending-membership'

export function canManageMemberships(roles) {
  return hasValidRoles(roles) && roles.includes('INSTITUTION_ADMIN')
}

export function membershipApprovalError(error) {
  if (error?.response?.status === 401) {
    return 'Your session has expired. Sign in again to continue.'
  }
  if (error?.response?.status === 403) {
    return 'Access denied. Only an approved institution administrator can manage membership requests.'
  }
  return error?.response?.data?.message
    || error?.response?.data?.error
    || error?.message
    || 'Membership request failed.'
}
