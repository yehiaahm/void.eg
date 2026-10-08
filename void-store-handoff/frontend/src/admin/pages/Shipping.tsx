import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useToast } from '../../store/toast'
import { adminApi, fromPiastres, toPiastres, type AdminZone } from '../api'
import { useT } from '../i18n'
import { PageHead, errText } from '../ui'

interface Row {
  id: number
  code: string
  name: AdminZone['name']
  fee: string
  etaDays: string
  enabled: boolean
}

export default function Shipping() {
  const { t, lang } = useT()
  const qc = useQueryClient()
  const flash = useToast()
  const { data } = useQuery({ queryKey: ['admin', 'zones'], queryFn: adminApi.zones })
  const [rows, setRows] = useState<Row[]>([])
  const [bulk, setBulk] = useState('')
  const [err, setErr] = useState<string | null>(null)

  useEffect(() => {
    if (data) setRows(data.map((z) => ({ id: z.id, code: z.code, name: z.name, fee: fromPiastres(z.fee), etaDays: z.etaDays, enabled: z.enabled })))
  }, [data])

  const save = useMutation({
    mutationFn: () => {
      const payload = rows.map((r) => {
        const fee = toPiastres(r.fee)
        if (Number.isNaN(fee)) throw new Error(`${r.name.en}: ${t.fee}`)
        return { id: r.id, fee, etaDays: r.etaDays, enabled: r.enabled }
      })
      return adminApi.saveZones(payload)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'zones'] })
      qc.invalidateQueries({ queryKey: ['zones'] })
      flash(t.saved)
      setErr(null)
    },
    onError: (e) => setErr(e instanceof Error && !('status' in e) ? e.message : errText(e, t)),
  })

  const set = (i: number, patch: Partial<Row>) => setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  const enabledCount = rows.filter((r) => r.enabled).length

  return (
    <>
      <PageHead title={t.shipping} sub={`${enabledCount} / ${rows.length}`}>
        <button type="button" className="a-btn a-btn-primary" onClick={() => save.mutate()} disabled={save.isPending}>
          {save.isPending ? t.saving : t.save}
        </button>
      </PageHead>
      <p className="a-info" style={{ marginBottom: 16 }}>
        {t.shippingHint}
      </p>
      <div className="a-filters">
        <label className="a-field" style={{ width: 160 }}>
          <span>{t.fee}</span>
          <input className="a-input" inputMode="decimal" dir="ltr" value={bulk} onChange={(e) => setBulk(e.target.value)} />
        </label>
        <button type="button" className="a-btn" disabled={!bulk.trim()} onClick={() => setRows((rs) => rs.map((r) => ({ ...r, fee: bulk.trim() })))}>
          {t.setAll}
        </button>
      </div>
      {err && <p className="a-error" style={{ marginBottom: 12 }}>{err}</p>}
      <div className="a-table-wrap">
        <table className="a-table">
          <thead>
            <tr>
              <th>{t.governorate}</th>
              <th>{t.fee}</th>
              <th className="a-hide-sm">{t.eta}</th>
              <th>{t.enabled}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={r.id}>
                <td>{lang === 'ar' ? r.name.ar : r.name.en}</td>
                <td style={{ width: 130 }}>
                  <input className="a-input" inputMode="decimal" dir="ltr" value={r.fee} onChange={(e) => set(i, { fee: e.target.value })} aria-label={`${t.fee} ${r.name.en}`} />
                </td>
                <td className="a-hide-sm" style={{ width: 130 }}>
                  <input className="a-input" dir="ltr" value={r.etaDays} placeholder="2-4" onChange={(e) => set(i, { etaDays: e.target.value })} aria-label={`${t.eta} ${r.name.en}`} />
                </td>
                <td style={{ width: 90 }}>
                  <label className="a-switch">
                    <input type="checkbox" checked={r.enabled} onChange={(e) => set(i, { enabled: e.target.checked })} aria-label={`${t.enabled} ${r.name.en}`} />
                  </label>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
