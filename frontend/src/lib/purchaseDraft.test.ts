import { beforeEach, expect, it } from 'vitest'
import {
  emptyPurchaseHeader,
  purchaseDraftKey,
  readPurchaseDraft,
  validPurchaseLine,
} from './purchaseDraft'
beforeEach(() => localStorage.clear())
it('preserves unfinished edits in a draft but rejects them as a final receipt', () => {
  const line = {
    articleId: 1,
    label: 'Producto',
    quantity: 0,
    quantityText: '',
    unitCost: 0,
    unitCostText: '',
  }
  localStorage.setItem(
    purchaseDraftKey('1:1'),
    JSON.stringify({ version: 1, header: emptyPurchaseHeader(), lines: [line], extraCosts: '' }),
  )
  expect(readPurchaseDraft('1:1')?.lines[0]).toEqual(line)
  expect(readPurchaseDraft('1:2')).toBeNull()
  expect(validPurchaseLine(line)).toBe(false)
  expect(
    validPurchaseLine({ articleId: 1, label: '', quantity: 1.5, unitCost: 10, wholeUnits: true }),
  ).toBe(false)
})
it('ignores malformed drafts', () => {
  localStorage.setItem(purchaseDraftKey('1:1'), '{broken')
  expect(readPurchaseDraft('1:1')).toBeNull()
  localStorage.setItem(
    purchaseDraftKey('1:1'),
    JSON.stringify({
      version: 1,
      header: emptyPurchaseHeader(),
      lines: [{ articleId: 'wrong' }],
      extraCosts: '0',
    }),
  )
  expect(readPurchaseDraft('1:1')).toBeNull()
})
