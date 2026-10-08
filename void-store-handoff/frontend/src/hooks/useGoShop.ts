import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useI18n } from '../i18n'

export const scrollToShop = (smooth = true) => {
  const el = document.getElementById('shop')
  if (!el) return false
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  el.scrollIntoView({ behavior: smooth && !reduce ? 'smooth' : 'auto', block: 'start' })
  return true
}

/** Scroll to the product grid, navigating home first when needed. */
export function useGoShop() {
  const { lang } = useI18n()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  return useCallback(
    (e?: { preventDefault(): void }) => {
      e?.preventDefault()
      const home = `/${lang}`
      if (pathname === home || pathname === `${home}/`) scrollToShop()
      else navigate(home, { state: { scrollTo: 'shop' } })
    },
    [lang, navigate, pathname],
  )
}
