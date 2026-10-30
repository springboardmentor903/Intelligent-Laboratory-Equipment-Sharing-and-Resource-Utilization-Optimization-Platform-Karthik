export function getResetTokenFromHash(hash) {
  return new URLSearchParams(String(hash || '').replace(/^#/, '')).get('token') || ''
}
