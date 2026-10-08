import { createContext, useContext, useEffect, type ReactNode } from 'react'
import type { Lang, Localized } from '../api/types'
import { STRINGS, type Strings } from './strings'

export const LANGS: Lang[] = ['en', 'ar']
export const isLang = (v: string | undefined): v is Lang => v === 'en' || v === 'ar'

interface I18n {
  lang: Lang
  t: Strings
  /** Pick the current language from a localized value (falls back to English). */
  l: (v: Localized | null | undefined) => string
  /** Same path in the other language. */
  switchHref: (pathname: string) => string
}

const Ctx = createContext<I18n | null>(null)

export function I18nProvider({ lang, children }: { lang: Lang; children: ReactNode }) {
  useEffect(() => {
    const root = document.documentElement
    root.lang = lang
    root.dir = lang === 'ar' ? 'rtl' : 'ltr'
    try {
      localStorage.setItem('void.lang', lang)
    } catch {
      /* storage unavailable */
    }
  }, [lang])

  const value: I18n = {
    lang,
    t: STRINGS[lang],
    l: (v) => (v ? v[lang] || v.en : ''),
    switchHref: (pathname) => {
      const other = lang === 'en' ? 'ar' : 'en'
      return pathname.replace(/^\/(en|ar)(?=\/|$)/, `/${other}`)
    },
  }
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useI18n() {
  const v = useContext(Ctx)
  if (!v) throw new Error('useI18n outside I18nProvider')
  return v
}

export function preferredLang(): Lang {
  try {
    const saved = localStorage.getItem('void.lang') ?? undefined
    if (isLang(saved)) return saved
  } catch {
    /* storage unavailable */
  }
  return navigator.language?.toLowerCase().startsWith('ar') ? 'ar' : 'en'
}
