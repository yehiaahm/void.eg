import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api } from '../api'
import { useAuth } from './auth'

interface WishlistState {
  slugs: string[]
  count: number
  has: (slug: string) => boolean
  /** Returns true when the product was added, false when removed. */
  toggle: (slug: string) => boolean
}

const KEY = 'void.wishlist.v1'

function loadLocal(): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(v) ? v.filter((x) => typeof x === 'string') : []
  } catch {
    return []
  }
}
function saveLocal(slugs: string[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(slugs))
  } catch {
    /* storage unavailable */
  }
}

const Ctx = createContext<WishlistState | null>(null)

/**
 * Guests: saved in this browser. Signed in: saved on the account — whatever the guest saved
 * before signing in is merged into the account once, then cleared locally.
 */
export function WishlistProvider({ children }: { children: ReactNode }) {
  const { user, ready } = useAuth()
  const [slugs, setSlugs] = useState<string[]>(loadLocal)
  const userId = user?.id ?? null
  const syncedFor = useRef<number | null>(null)

  useEffect(() => {
    if (!ready) return
    if (userId == null) {
      syncedFor.current = null
      setSlugs(loadLocal())
      return
    }
    if (syncedFor.current === userId) return
    syncedFor.current = userId
    const local = loadLocal()
    const req = local.length ? api.wishMerge(local) : api.wishlist()
    req
      .then((server) => {
        setSlugs(server)
        saveLocal([])
      })
      .catch(() => {
        /* keep what we have; next action retries */
      })
  }, [ready, userId])

  // other tabs (guest)
  useEffect(() => {
    const on = (e: StorageEvent) => e.key === KEY && userId == null && setSlugs(loadLocal())
    window.addEventListener('storage', on)
    return () => window.removeEventListener('storage', on)
  }, [userId])

  const toggle = useCallback(
    (slug: string) => {
      const adding = !slugs.includes(slug)
      const next = adding ? [slug, ...slugs] : slugs.filter((s) => s !== slug)
      setSlugs(next) // optimistic
      if (userId == null) {
        saveLocal(next)
      } else {
        ;(adding ? api.wishAdd(slug) : api.wishRemove(slug)).then(setSlugs).catch(() => setSlugs(slugs))
      }
      return adding
    },
    [slugs, userId],
  )

  const value = useMemo(() => ({ slugs, count: slugs.length, has: (s: string) => slugs.includes(s), toggle }), [slugs, toggle])
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useWishlist() {
  const v = useContext(Ctx)
  if (!v) throw new Error('useWishlist outside WishlistProvider')
  return v
}
