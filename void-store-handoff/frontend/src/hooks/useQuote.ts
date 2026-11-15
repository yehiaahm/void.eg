import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from '../api'
import { EMAIL_RE, isEgMobile } from '../lib/errors'
import type { BagLine } from '../store/bag'

/** Live prices / stock / totals for the bag from the API. */
export function useQuote(lines: BagLine[], opts: { governorate?: string; coupon?: string; email?: string; phone?: string; enabled?: boolean } = {}) {
  const items = lines.map((l) => ({ slug: l.slug, size: l.size, qty: l.qty }))
  // identifiers only count once they look complete, so typing does not fire a request per keystroke
  const email = opts.email && EMAIL_RE.test(opts.email.trim()) ? opts.email.trim() : ''
  const phone = opts.phone && isEgMobile(opts.phone) ? opts.phone : ''
  return useQuery({
    queryKey: ['quote', items, opts.governorate ?? '', opts.coupon ?? '', email, phone],
    queryFn: () => api.quote(items, opts.governorate || undefined, opts.coupon || undefined, email || undefined, phone || undefined),
    enabled: (opts.enabled ?? true) && items.length > 0,
    placeholderData: keepPreviousData,
    staleTime: 10_000,
  })
}
