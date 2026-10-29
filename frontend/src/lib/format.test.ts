import { describe, expect, it } from 'vitest'
import { currency, dateDisplayToIso, formatDateDisplay, formatDateTimeDisplay } from './format'

describe('commercial formatting', () => {
  it('keeps cents visible in amounts and receipts', () => {
    expect(currency.format(12.34)).toContain('12,34')
  })
  it('validates calendar dates and leap years', () => {
    expect(dateDisplayToIso('29/02/2024')).toBe('2024-02-29')
    expect(dateDisplayToIso('29/02/2026')).toBeNull()
    expect(dateDisplayToIso('31/04/2026')).toBeNull()
    expect(formatDateDisplay('2026-10-28')).toBe('28/10/2026')
  })
  it('shows UTC instants on the business date while preserving local SQL dates', () => {
    expect(formatDateDisplay('2026-10-29T01:30:00Z')).toBe('28/10/2026')
    expect(formatDateTimeDisplay('2026-10-29T01:30:00Z')).toBe('28/10/2026 - 22:30')
    expect(formatDateTimeDisplay('2026-10-28 22:30:00')).toBe('28/10/2026 - 22:30')
  })
})
