import { expect, test } from 'vitest'
import { suggestedSalePrice } from './articlePricing'

test.each([
  ['100', '10', '0', true, 110],
  ['100', '10', '0', false, 110],
  ['100', '10', '20', true, 140],
  ['100', '10', '20', false, 132],
  ['0.05', '10', '10', false, 0.07],
  ['0', '10', '10', true, 0],
  ['9.99', '0', '0', true, 10],
  ['10.01', '0', '0', true, 20],
])(
  'calculates cost %s freight %s margin %s round %s as %s',
  (cost, freight, margin, round, expected) => {
    expect(suggestedSalePrice(cost, freight, margin, round)).toBe(expected)
  },
)
