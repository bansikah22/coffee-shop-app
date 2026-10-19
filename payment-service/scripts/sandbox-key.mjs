export function isSandboxSecretKey(value) {
  return typeof value === 'string' && /^(?:sk_test_|rk_test_|rkcs_test_)[A-Za-z0-9]+$/.test(value)
}