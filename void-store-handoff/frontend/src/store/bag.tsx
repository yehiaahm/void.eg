import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { Size } from '../api/types'

export interface BagLine {
  slug: string
  size: Size
  qty: number
}

interface BagState {
  lines: BagLine[]
  count: number
  add: (slug: string, size: Size, qty: number) => void
  setQty: (slug: string, size: Size, qty: number) => void
  remove: (slug: string, size: Size) => void
  clear: () => void
  isOpen: boolean
  setOpen: (open: boolean) => void
}

const KEY = 'void.bag.v1'
export const MAX_QTY = 10

function load(): BagLine[] {
  try {
    const raw = JSON.parse(localStorage.getItem(KEY) ?? '[]')
    return Array.isArray(raw)
      ? raw.filter((l) => l && typeof l.slug === 'string' && typeof l.size === 'string' && l.qty > 0)
      : []
  } catch {
    return []
  }
}

const Ctx = createContext<BagState | null>(null)

export function BagProvider({ children }: { children: ReactNode }) {
  const [lines, setLines] = useState<BagLine[]>(load)
  const [isOpen, setOpen] = useState(false)

  useEffect(() => {
    try {
      localStorage.setItem(KEY, JSON.stringify(lines))
    } catch {
      /* storage unavailable */
    }
  }, [lines])

  // Keep tabs in sync.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key === KEY) setLines(load())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const add = useCallback((slug: string, size: Size, qty: number) => {
    setLines((ls) => {
      const i = ls.findIndex((l) => l.slug === slug && l.size === size)
      if (i < 0) return [...ls, { slug, size, qty: Math.min(MAX_QTY, qty) }]
      const next = [...ls]
      next[i] = { ...next[i], qty: Math.min(MAX_QTY, next[i].qty + qty) }
      return next
    })
  }, [])

  const setQty = useCallback((slug: string, size: Size, qty: number) => {
    setLines((ls) =>
      qty <= 0
        ? ls.filter((l) => !(l.slug === slug && l.size === size))
        : ls.map((l) => (l.slug === slug && l.size === size ? { ...l, qty: Math.min(MAX_QTY, qty) } : l)),
    )
  }, [])

  const remove = useCallback((slug: string, size: Size) => {
    setLines((ls) => ls.filter((l) => !(l.slug === slug && l.size === size)))
  }, [])

  const clear = useCallback(() => setLines([]), [])

  const value = useMemo(
    () => ({ lines, count: lines.reduce((n, l) => n + l.qty, 0), add, setQty, remove, clear, isOpen, setOpen }),
    [lines, add, setQty, remove, clear, isOpen],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useBag() {
  const v = useContext(Ctx)
  if (!v) throw new Error('useBag outside BagProvider')
  return v
}
