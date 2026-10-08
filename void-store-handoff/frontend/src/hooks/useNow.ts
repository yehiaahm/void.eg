import { useEffect, useState } from 'react'

/** Current timestamp, refreshed every `ms` (aligned to the second boundary). */
export function useNow(ms = 1000) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    let id: number
    const tick = () => {
      setNow(Date.now())
      id = window.setTimeout(tick, ms - (Date.now() % ms))
    }
    id = window.setTimeout(tick, ms - (Date.now() % ms))
    return () => window.clearTimeout(id)
  }, [ms])
  return now
}
