import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { adminApi, egp, fmtDate, fromCairoInput, fromPiastres, toCairoInput, toPiastres, type AdminCoupon } from '../api'
import { useT } from '../i18n'
import { Badge, Field, Modal, PageHead, Pick, errText } from '../ui'

function CouponForm({ coupon, onClose }: { coupon: AdminCoupon | null; onClose: () => void }) {
  const { t } = useT()
  const qc = useQueryClient()
  const [f, setF] = useState({
    code: coupon?.code ?? '',
    type: coupon?.type ?? 'PERCENT',
    value: coupon ? (coupon.type === 'FIXED' ? fromPiastres(coupon.value) : String(coupon.value)) : '10',
    minSubtotal: coupon ? fromPiastres(coupon.minSubtotal) : '',
    startsAt: toCairoInput(coupon?.startsAt ?? null),
    endsAt: toCairoInput(coupon?.endsAt ?? null),
    maxUses: coupon?.maxUses?.toString() ?? '',
    perCustomer: coupon?.perCustomer?.toString() ?? '',
    active: coupon?.active ?? true,
  })
  const [err, setErr] = useState<string | null>(null)
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }))

  const save = useMutation({
    mutationFn: () => {
      const value = f.type === 'FIXED' ? toPiastres(f.value) ?? 0 : f.type === 'PERCENT' ? Number(f.value) : 0
      const body = {
        code: f.code.trim(),
        type: f.type as AdminCoupon['type'],
        value,
        minSubtotal: toPiastres(f.minSubtotal) ?? 0,
        startsAt: fromCairoInput(f.startsAt),
        endsAt: fromCairoInput(f.endsAt),
        maxUses: f.maxUses ? Number(f.maxUses) : null,
        perCustomer: f.perCustomer ? Number(f.perCustomer) : null,
        active: f.active,
      }
      return coupon ? adminApi.updateCoupon(coupon.id, body) : adminApi.createCoupon(body)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'coupons'] })
      onClose()
    },
    onError: (e) => setErr(errText(e, t)),
  })
  const del = useMutation({
    mutationFn: () => adminApi.deleteCoupon(coupon!.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'coupons'] })
      onClose()
    },
  })

  return (
    <Modal title={coupon ? coupon.code : t.newCoupon} onClose={onClose}>
      <form className="a-form" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <Field label={t.code} value={f.code} onChange={(e) => setF((x) => ({ ...x, code: e.target.value.toUpperCase() }))} dir="ltr" required pattern="[A-Za-z0-9_\-]+" />
        <div className="a-pair">
          <Pick label={t.type} value={f.type} onChange={set('type')}>
            {(['PERCENT', 'FIXED', 'FREE_SHIPPING'] as const).map((k) => (
              <option key={k} value={k}>
                {t.couponType[k]}
              </option>
            ))}
          </Pick>
          {f.type !== 'FREE_SHIPPING' && <Field label={f.type === 'PERCENT' ? `${t.value} (%)` : `${t.value} (EGP)`} value={f.value} onChange={set('value')} inputMode="decimal" dir="ltr" required />}
        </div>
        <Field label={t.minSubtotal} value={f.minSubtotal} onChange={set('minSubtotal')} inputMode="decimal" dir="ltr" placeholder="0" />
        <div className="a-pair">
          <Field label={t.startsOn} type="datetime-local" value={f.startsAt} onChange={set('startsAt')} />
          <Field label={t.endsOn} type="datetime-local" value={f.endsAt} onChange={set('endsAt')} />
        </div>
        <div className="a-pair">
          <Field label={t.maxUses} type="number" min={1} value={f.maxUses} onChange={set('maxUses')} placeholder={t.unlimited} />
          <Field label={t.perCustomer} type="number" min={1} value={f.perCustomer} onChange={set('perCustomer')} placeholder={t.unlimited} />
        </div>
        <label className="a-switch">
          <input type="checkbox" checked={f.active} onChange={(e) => setF((x) => ({ ...x, active: e.target.checked }))} />
          {t.active}
        </label>
        {err && <p className="a-error">{err}</p>}
        <div className="a-actions">
          <button type="submit" className="a-btn a-btn-primary" disabled={save.isPending}>
            {t.save}
          </button>
          <button type="button" className="a-btn" onClick={onClose}>
            {t.cancel}
          </button>
          {coupon && (
            <button type="button" className="a-btn a-btn-danger" style={{ marginInlineStart: 'auto' }} onClick={() => window.confirm(t.confirmDelete) && del.mutate()}>
              {t.delete}
            </button>
          )}
        </div>
      </form>
    </Modal>
  )
}

export default function Coupons() {
  const { t, lang } = useT()
  const { data = [], isLoading } = useQuery({ queryKey: ['admin', 'coupons'], queryFn: adminApi.coupons })
  const [editing, setEditing] = useState<AdminCoupon | 'new' | null>(null)
  const describe = (c: AdminCoupon) => (c.type === 'PERCENT' ? `${c.value}%` : c.type === 'FIXED' ? egp(c.value) : t.couponType.FREE_SHIPPING)
  return (
    <>
      <PageHead title={t.coupons}>
        <button type="button" className="a-btn a-btn-primary" onClick={() => setEditing('new')}>
          {t.newCoupon}
        </button>
      </PageHead>
      <div className="a-table-wrap">
        <table className="a-table">
          <thead>
            <tr>
              <th>{t.code}</th>
              <th>{t.value}</th>
              <th>{t.status}</th>
              <th className="a-hide-sm">{t.endsOn}</th>
              <th className="a-num">{t.used}</th>
            </tr>
          </thead>
          <tbody>
            {data.map((c) => (
              <tr key={c.id} data-href tabIndex={0} onClick={() => setEditing(c)} onKeyDown={(e) => e.key === 'Enter' && setEditing(c)}>
                <td className="a-mono">{c.code}</td>
                <td dir="ltr" style={{ textAlign: 'start' }}>
                  {describe(c)}
                  {c.minSubtotal > 0 && <div className="a-faint" style={{ fontSize: 12 }}>≥ {egp(c.minSubtotal)}</div>}
                </td>
                <td>
                  <Badge s={c.active ? 'on' : 'off'}>{c.active ? t.active : t.inactive}</Badge>
                </td>
                <td className="a-hide-sm a-muted">{c.endsAt ? fmtDate(c.endsAt, lang) : '—'}</td>
                <td className="a-num">
                  {c.usedCount}
                  {c.maxUses ? ` / ${c.maxUses}` : ''}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {!isLoading && data.length === 0 && <p className="a-empty">{t.empty}</p>}
      </div>
      {editing && <CouponForm coupon={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}
