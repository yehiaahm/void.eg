import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import type { Localized } from '../../api/types'
import { useToast } from '../../store/toast'
import { adminApi, type AdminPage } from '../api'
import { useT } from '../i18n'
import { Field, PageHead, Pair, errText } from '../ui'

function SettingsCard() {
  const { t } = useT()
  const qc = useQueryClient()
  const flash = useToast()
  const { data } = useQuery({ queryKey: ['admin', 'settings'], queryFn: adminApi.settings })
  const [s, setS] = useState<Record<string, string>>({})
  const [err, setErr] = useState<string | null>(null)
  useEffect(() => {
    if (data) setS(data)
  }, [data])
  const save = useMutation({
    mutationFn: () => {
      const keys = ['shipping_returns_en', 'shipping_returns_ar', 'instagram_url', 'tiktok_url', 'contact_email', 'contact_phone', 'cod_enabled', 'low_stock_threshold', 'return_window_days']
      return adminApi.saveSettings(Object.fromEntries(keys.map((k) => [k, s[k] ?? ''])))
    },
    onSuccess: (v) => {
      qc.setQueryData(['admin', 'settings'], v)
      qc.invalidateQueries({ queryKey: ['settings'] })
      flash(t.saved)
      setErr(null)
    },
    onError: (e) => setErr(errText(e, t)),
  })
  const v = (k: string) => s[k] ?? ''
  const set = (k: string) => (e: { target: { value: string } }) => setS((x) => ({ ...x, [k]: e.target.value }))
  return (
    <form className="a-card a-form" onSubmit={(e) => { e.preventDefault(); save.mutate() }}>
      <h2 className="a-card-title">{t.settings}</h2>
      <div className="a-pair">
        <Field label={t.instagram} type="url" value={v('instagram_url')} onChange={set('instagram_url')} placeholder="https://instagram.com/…" dir="ltr" />
        <Field label={t.tiktok} type="url" value={v('tiktok_url')} onChange={set('tiktok_url')} placeholder="https://tiktok.com/@…" dir="ltr" />
      </div>
      <div className="a-pair">
        <Field label={t.contactEmail} type="email" value={v('contact_email')} onChange={set('contact_email')} dir="ltr" />
        <Field label={t.contactPhone} type="tel" value={v('contact_phone')} onChange={set('contact_phone')} dir="ltr" />
      </div>
      <Pair
        label={t.shippingReturnsText}
        multiline
        value={{ en: v('shipping_returns_en'), ar: v('shipping_returns_ar') }}
        onChange={(l: Localized) => setS((x) => ({ ...x, shipping_returns_en: l.en, shipping_returns_ar: l.ar }))}
      />
      <div className="a-pair">
        <Field label={t.lowStockThreshold} type="number" min={0} value={v('low_stock_threshold')} onChange={set('low_stock_threshold')} />
        <Field label={t.returnWindow} type="number" min={0} max={365} value={v('return_window_days')} onChange={set('return_window_days')} />
        <label className="a-switch" style={{ alignSelf: 'end', height: 38 }}>
          <input type="checkbox" checked={v('cod_enabled') !== 'false'} onChange={(e) => setS((x) => ({ ...x, cod_enabled: String(e.target.checked) }))} />
          {t.codEnabled}
        </label>
      </div>
      {err && <p className="a-error">{err}</p>}
      <div>
        <button type="submit" className="a-btn a-btn-primary" disabled={save.isPending}>
          {save.isPending ? t.saving : t.save}
        </button>
      </div>
    </form>
  )
}

function PageCard({ page }: { page: AdminPage }) {
  const { t } = useT()
  const qc = useQueryClient()
  const flash = useToast()
  const [title, setTitle] = useState(page.title)
  const [body, setBody] = useState(page.body)
  const [err, setErr] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => adminApi.savePage(page.slug, title, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'pages'] })
      qc.invalidateQueries({ queryKey: ['page', page.slug] })
      flash(t.saved)
    },
    onError: (e) => setErr(errText(e, t)),
  })
  return (
    <details className="a-card">
      <summary style={{ cursor: 'pointer', display: 'flex', justifyContent: 'space-between' }}>
        <span>{page.title.en}</span>
        <span className="a-faint a-mono">/pages/{page.slug}</span>
      </summary>
      <form className="a-form" style={{ marginTop: 16 }} onSubmit={(e) => { e.preventDefault(); save.mutate() }}>
        <Pair label={t.title} value={title} onChange={setTitle} />
        <Pair label={t.body} value={body} onChange={setBody} multiline rows={10} hint={t.bodyHint} />
        {err && <p className="a-error">{err}</p>}
        <div>
          <button type="submit" className="a-btn a-btn-primary" disabled={save.isPending}>
            {save.isPending ? t.saving : t.save}
          </button>
        </div>
      </form>
    </details>
  )
}

export default function Content() {
  const { t } = useT()
  const { data: pages = [] } = useQuery({ queryKey: ['admin', 'pages'], queryFn: adminApi.pages })
  return (
    <>
      <PageHead title={t.content} />
      <div className="a-stack">
        <SettingsCard />
        <h2 style={{ margin: '12px 0 0', font: '500 15px/1.3 var(--f-body)' }}>{t.pages}</h2>
        {pages.map((p) => (
          <PageCard key={p.slug} page={p} />
        ))}
      </div>
    </>
  )
}
