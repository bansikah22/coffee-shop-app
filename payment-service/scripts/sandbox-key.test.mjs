import assert from 'node:assert/strict'
import test from 'node:test'
import { isSandboxSecretKey } from './sandbox-key.mjs'

test('accepts documented sandbox secret key families using synthetic values', () => {
  for (const prefix of ['sk_test_', 'rk_test_', 'rkcs_test_']) {
    assert.equal(isSandboxSecretKey(`${prefix}synthetic123`), true)
  }
})

test('rejects live, publishable, blank, malformed, and missing keys', () => {
  for (const value of [undefined, null, '', 'sk_test_', 'rk_test_', 'rkcs_test_', 'sk_live_synthetic123',
    'rk_live_synthetic123', 'rkcs_live_synthetic123', 'pk_test_synthetic123', 'whsec_synthetic123',
    'rkcs_test_synthetic123\n', ' rkcs_test_synthetic123', 'rkcs_test_bad-key']) {
    assert.equal(isSandboxSecretKey(value), false)
  }
})