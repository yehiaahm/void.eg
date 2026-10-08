import { useEffect, useId, useRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { ApiError } from '../api/http'
import type { Localized } from '../api/types'
import { useT, type AdminStrings } from './i18n'

export function errText(e: unknown, t: AdminStrings): string {
  if (e instanceof ApiError) {
    if (e.status === 403) return t.forbidden
    if (e.code === 'validation' && e.fields) return Object.entries(e.fields).map(([k, v]) => `${k}: ${v}`).join(' · ')
    return e.message || t.genericError
  }
  return t.genericError
}

export function PageHead({ title, sub, children }: { title: ReactNode; sub?: ReactNode; children?: ReactNode }) {
  return (
    <div className="a-head">
      <div>
        <h1>{title}</h1>
        {sub && <p className="a-sub">{sub}</p>}
      </div>
      {children && <div className="a-actions">{children}</div>}
    </div>
  )
}

export function Badge({ s, children }: { s: string; children: ReactNode }) {
  return (
    <span className="a-badge" data-s={s}>
      {children}
    </span>
  )
}

export function Field({ label, hint, ...rest }: { label: ReactNode; hint?: ReactNode } & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId()
  return (
    <label className="a-field" htmlFor={id}>
      <span>{label}</span>
      <input id={id} className="a-input" {...rest} />
      {hint && <small>{hint}</small>}
    </label>
  )
}

export function Area({ label, hint, ...rest }: { label: ReactNode; hint?: ReactNode } & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId()
  return (
    <label className="a-field" htmlFor={id}>
      <span>{label}</span>
      <textarea id={id} className="a-input" {...rest} />
      {hint && <small>{hint}</small>}
    </label>
  )
}

export function Pick({ label, hint, children, ...rest }: { label: ReactNode; hint?: ReactNode } & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId()
  return (
    <label className="a-field" htmlFor={id}>
      <span>{label}</span>
      <select id={id} className="a-input" {...rest}>
        {children}
      </select>
      {hint && <small>{hint}</small>}
    </label>
  )
}

/** English + Arabic side by side for one localized value. */
export function Pair({ label, value, onChange, multiline, rows = 4, hint }: {
  label: ReactNode
  value: Localized
  onChange: (v: Localized) => void
  multiline?: boolean
  rows?: number
  hint?: ReactNode
}) {
  const { t } = useT()
  const enId = useId()
  const arId = useId()
  const common = { className: 'a-input' }
  return (
    <div className="a-field">
      <span>{label}</span>
      <div className="a-pair">
        <div className="a-field">
          <label className="a-faint" htmlFor={enId} style={{ fontSize: 11 }}>
            {t.english}
          </label>
          {multiline ? (
            <textarea id={enId} {...common} rows={rows} dir="ltr" value={value.en} onChange={(e) => onChange({ ...value, en: e.target.value })} />
          ) : (
            <input id={enId} {...common} dir="ltr" value={value.en} onChange={(e) => onChange({ ...value, en: e.target.value })} />
          )}
        </div>
        <div className="a-field">
          <label className="a-faint" htmlFor={arId} style={{ fontSize: 11 }}>
            {t.arabic}
          </label>
          {multiline ? (
            <textarea id={arId} {...common} rows={rows} dir="rtl" lang="ar" value={value.ar} onChange={(e) => onChange({ ...value, ar: e.target.value })} />
          ) : (
            <input id={arId} {...common} dir="rtl" lang="ar" value={value.ar} onChange={(e) => onChange({ ...value, ar: e.target.value })} />
          )}
        </div>
      </div>
      {hint && <small>{hint}</small>}
    </div>
  )
}

export function Modal({ title, onClose, children }: { title: ReactNode; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null
    ref.current?.querySelector<HTMLElement>('input,select,textarea,button')?.focus()
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => {
      window.removeEventListener('keydown', onKey)
      prev?.focus?.()
    }
  }, [onClose])
  return (
    <div className="a-modal-wrap" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="a-modal" role="dialog" aria-modal="true" aria-label={typeof title === 'string' ? title : undefined} ref={ref}>
        <h2>{title}</h2>
        {children}
      </div>
    </div>
  )
}

export function Pager({ page, total, size, onPage }: { page: number; total: number; size: number; onPage: (p: number) => void }) {
  const { t } = useT()
  const pages = Math.max(1, Math.ceil(total / size))
  if (total <= size) return <div className="a-pager">{t.results(total)}</div>
  return (
    <div className="a-pager">
      <span>
        {t.results(total)} · {t.pageOf(page + 1, pages)}
      </span>
      <span className="a-actions">
        <button type="button" className="a-btn a-btn-sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          {t.prev}
        </button>
        <button type="button" className="a-btn a-btn-sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
          {t.next}
        </button>
      </span>
    </div>
  )
}

function I({ children, size = 18 }: { children: ReactNode; size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {children}
    </svg>
  )
}
export const Icons = {
  dashboard: () => (
    <I>
      <path d="M4 13h6V4H4zM14 20h6v-9h-6zM4 20h6v-4H4zM14 4v4h6V4z" />
    </I>
  ),
  orders: () => (
    <I>
      <path d="M5 8.5h14l-1.2 11.5H6.2L5 8.5z" />
      <path d="M9 8.5V7a3 3 0 0 1 6 0v1.5" />
    </I>
  ),
  products: () => (
    <I>
      <path d="M9 3 4 5.5 2.5 11l3 1L7 9v12h10V9l1.5 3 3-1L20 5.5 15 3c-.8 1.6-5.2 1.6-6 0z" />
    </I>
  ),
  customers: () => (
    <I>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c.8-3.5 3.6-5.5 7-5.5s6.2 2 7 5.5" />
    </I>
  ),
  drops: () => (
    <I>
      <circle cx="12" cy="12" r="3" />
      <ellipse cx="12" cy="12" rx="9" ry="3.2" transform="rotate(-13.5 12 12)" />
    </I>
  ),
  coupons: () => (
    <I>
      <path d="M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4z" />
      <path d="M10 9.5l4 5M10 14.5h.01M14 9.5h.01" />
    </I>
  ),
  shipping: () => (
    <I>
      <path d="M3 7h11v9H3zM14 10h4l3 3v3h-7z" />
      <circle cx="7" cy="17.5" r="1.5" />
      <circle cx="17" cy="17.5" r="1.5" />
    </I>
  ),
  returns: () => (
    <I>
      <path d="M9 14 4 9l5-5" />
      <path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11" />
    </I>
  ),
  content: () => (
    <I>
      <path d="M6 3h9l4 4v14H6z" />
      <path d="M9 11h7M9 15h7M9 7h4" />
    </I>
  ),
  waitlist: () => (
    <I>
      <path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z" />
      <path d="M10 20.5a2 2 0 0 0 4 0" />
    </I>
  ),
  messages: () => (
    <I>
      <path d="M4 6h16v12H4z" />
      <path d="m4 7 8 6 8-6" />
    </I>
  ),
  staff: () => (
    <I>
      <circle cx="9" cy="8" r="3" />
      <path d="M3 19c.6-3 3-4.8 6-4.8s5.4 1.8 6 4.8M16 5a3 3 0 0 1 0 6M18 14.4c1.6.6 2.7 2 3 4.6" />
    </I>
  ),
  menu: () => (
    <I size={22}>
      <path d="M4 9h16M4 15h16" />
    </I>
  ),
  up: () => (
    <I size={14}>
      <path d="M12 19V5M6 11l6-6 6 6" />
    </I>
  ),
  down: () => (
    <I size={14}>
      <path d="M12 5v14M6 13l6 6 6-6" />
    </I>
  ),
  trash: () => (
    <I size={14}>
      <path d="M5 7h14M10 7V5h4v2M7 7l1 13h8l1-13" />
    </I>
  ),
  upload: () => (
    <I size={22}>
      <path d="M12 16V4M7 9l5-5 5 5M5 20h14" />
    </I>
  ),
}
