import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, type OrderStatus, type OrderSummary } from '../api'
import { useAuth } from './auth'

/**
 * The orders this device knows about, with their live status.
 *
 * Guests: the number + private tracking token of each order placed (or opened from the e-mailed link) here are kept in
 * localStorage, so the customer sees the order and its status the next time the store opens, without the e-mail.
 * The token is the same secret as the e-mailed link, so keeping it grants nothing new — but it is the customer's
 * only key, hence "forget" for shared devices. Signed-in customers also get their account orders.
 */
export interface MyOrder extends OrderSummary {
  /** Private tracking token — present only for orders remembered on this device. */
  token?: string
}

interface Stored {
  number: string
  token: string
  at: number
}

interface MyOrdersState {
  orders: MyOrder[]
  /** Orders still on their way: received, confirmed or shipped. */
  active: MyOrder[]
  loading: boolean
  /** Numbers of the signed-in customer's own orders (empty when signed out). */
  accountNumbers: string[]
  /** Orders remembered on this device with their private token that aren't in the signed-in account yet. */
  unclaimed: MyOrder[]
  /** Add orders to the signed-in account (by their private tokens). Resolves with the numbers that were added. */
  claim: (numbers?: string[]) => Promise<string[]>
  remember: (number: string, token: string) => void
  forget: (number: string) => void
}

const KEY = 'void.orders.v1'
const MAX_KEPT = 10
const MAX_AGE = 90 * 24 * 3600 * 1000
export const ACTIVE: OrderStatus[] = ['NEW', 'CONFIRMED', 'SHIPPED']

function load(): Stored[] {
  try {
    const raw = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    if (!Array.isArray(raw)) return []
    const now = Date.now()
    return raw
      .filter((e): e is Stored => !!e && typeof e.number === 'string' && typeof e.token === 'string' && typeof e.at === 'number' && now - e.at < MAX_AGE)
      .slice(0, MAX_KEPT)
  } catch {
    return []
  }
}

const Ctx = createContext<MyOrdersState | null>(null)

export function MyOrdersProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [kept, setKept] = useState<Stored[]>(load)

  useEffect(() => {
    try {
      localStorage.setItem(KEY, JSON.stringify(kept))
    } catch {
      /* storage unavailable: the orders still work for this visit */
    }
  }, [kept])

  // Keep tabs in sync.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === KEY) setKept(load())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const remember = useCallback((number: string, token: string) => {
    const n = number.trim().toUpperCase()
    if (!n || !token) return
    setKept((ks) => [{ number: n, token, at: Date.now() }, ...ks.filter((k) => k.number !== n)].slice(0, MAX_KEPT))
  }, [])

  const forget = useCallback((number: string) => setKept((ks) => ks.filter((k) => k.number !== number)), [])

  // One call for every remembered order. Re-checked when the tab regains focus, so the status stays fresh.
  const numbers = kept.map((k) => k.number).join(',')
  const device = useQuery({
    queryKey: ['device-orders', numbers],
    placeholderData: keepPreviousData, // adding an order must not blank the strip while the new answer loads
    queryFn: async () => {
      const found = await api.trackBatch(kept.map((k) => ({ number: k.number, token: k.token })))
      return { requested: kept.map((k) => k.number), found }
    },
    enabled: kept.length > 0,
    staleTime: 60_000,
    refetchOnWindowFocus: true,
  })

  // Orders the server no longer recognises (deleted, or links rotated) are dropped — only ones that were in the request.
  const answered = device.data
  useEffect(() => {
    if (!answered) return
    const known = new Set(answered.found.map((o) => o.number))
    const gone = answered.requested.filter((n) => !known.has(n))
    if (gone.length) setKept((ks) => ks.filter((k) => !gone.includes(k.number)))
  }, [answered])

  const account = useQuery({ queryKey: ['my-orders'], queryFn: api.myOrders, enabled: !!user, staleTime: 60_000 })

  const accountNumbers = useMemo(() => (user ? (account.data ?? []).map((o) => o.number) : []), [user, account.data])
  const unclaimed = useMemo(
    () => (user && account.data ? (device.data?.found ?? []).filter((o) => !accountNumbers.includes(o.number)).map((o) => ({ ...o, token: kept.find((k) => k.number === o.number)?.token })).filter((o) => !!o.token) : []),
    [user, account.data, device.data, accountNumbers, kept],
  )

  const qc = useQueryClient()
  const claim = useCallback(
    async (numbers?: string[]) => {
      const entries = kept.filter((k) => !numbers || numbers.includes(k.number)).map((k) => ({ number: k.number, token: k.token }))
      if (!entries.length) return []
      const res = await api.claimOrders(entries)
      await qc.invalidateQueries({ queryKey: ['my-orders'] })
      return res.claimed
    },
    [kept, qc],
  )

  const orders = useMemo(() => {
    const byNumber = new Map<string, MyOrder>()
    for (const o of device.data?.found ?? []) byNumber.set(o.number, { ...o, token: kept.find((k) => k.number === o.number)?.token })
    // The account's own list is authoritative for its orders (no token needed: they open under /account).
    for (const o of user ? (account.data ?? []) : []) byNumber.set(o.number, { ...o })
    return [...byNumber.values()].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
  }, [device.data, account.data, kept, user])

  const value = useMemo<MyOrdersState>(
    () => ({
      orders,
      active: orders.filter((o) => ACTIVE.includes(o.status)),
      loading: (device.isLoading && kept.length > 0) || (!!user && account.isLoading),
      accountNumbers,
      unclaimed,
      claim,
      remember,
      forget,
    }),
    [orders, device.isLoading, account.isLoading, kept.length, user, accountNumbers, unclaimed, claim, remember, forget],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useMyOrders() {
  const v = useContext(Ctx)
  if (!v) throw new Error('useMyOrders outside MyOrdersProvider')
  return v
}

/** Where a remembered order opens: the account page for account orders, the private-link view otherwise. */
export function orderHref(lang: string, o: MyOrder) {
  return o.token ? `/${lang}/track?order=${o.number}&t=${encodeURIComponent(o.token)}` : `/${lang}/account/orders/${o.number}`
}
