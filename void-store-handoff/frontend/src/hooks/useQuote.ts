import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../api'
import type { BagLine } from '../store/bag'

/** Live prices / stock / totals for the bag from the API. */
export function useQuote(lines: BagLine[], opts: { governorate?: string; coupon?: string; email?: string; enabled?: boolean } = {}) {
  const items = lines.map((l) => ({ slug: l.slug, size: l.size, qty: l.qty }))
  return useQuery({
    queryKey: ['quote', items, opts.governorate ?? '', opts.coupon ?? '', opts.email ?? ''],
    queryFn: () => api.quote(items, opts.governorate || undefined, opts.coupon || undefined, opts.email || undefined),
    enabled: (opts.enabled ?? true) && items.length > 0,
    placeholderData: keepPreviousData,
    staleTime: 10_000,
  })
}
