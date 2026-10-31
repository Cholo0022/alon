import { afterEach, expect, test, vi } from 'vitest'
import { shopOrderKey } from './orderKey'
afterEach(() => {
  vi.unstubAllGlobals()
})
/** Conserva identificadores seguros y válidos para los pedidos desde HTTP en el celular. */
test('generates UUID order keys when randomUUID is unavailable on Wi-Fi HTTP', () => {
  const original = globalThis.crypto
  vi.stubGlobal('crypto', { getRandomValues: original.getRandomValues.bind(original) })
  const keys = Array.from({ length: 30 }, () => shopOrderKey())
  expect(new Set(keys).size).toBe(30)
  for (const key of keys)
    expect(key).toMatch(/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/)
})
