import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import type { Localized } from '../../api/types'
import { adminApi, fmtDate, fromCairoInput, toCairoInput, type AdminDrop } from '../api'
import { useT } from '../i18n'
import { Badge, Field, Modal, PageHead, Pair, Pick, errText } from '../ui'

interface Form {
  number: number
  name: Localized
  startsAt: string
  status: string
}

function DropForm({ drop, nextNumber, onClose }: { drop: AdminDrop | null; nextNumber: number; onClose: () => void }) {
  const { t } = useT()
  const qc = useQueryClient()
  const [f, setF] = useState<Form>(
    drop
      ? { number: drop.number, name: drop.name, startsAt: toCairoInput(drop.startsAt), status: drop.status }
      : { number: nextNumber, name: { en: '', ar: '' }, startsAt: '', status: 'UPCOMING' },
  )
  const [err, setErr] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () => {
      const body = { number: f.number, name: f.name, startsAt: fromCairoInput(f.startsAt), status: f.status }
      return drop ? adminApi.updateDrop(drop.id, body) : adminApi.createDrop(body)
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'drops'] })
      qc.invalidateQueries({ queryKey: ['settings'] })
      onClose()
    },
    onError: (e) => setErr(errText(e, t)),
  })
  const del = useMutation({
    mutationFn: () => adminApi.deleteDrop(drop!.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'drops'] })
      onClose()
    },
    onError: (e) => setErr(errText(e, t)),
  })
  return (
    <Modal title={drop ? `${t.drop} ${String(drop.number).padStart(2, '0')}` : t.newDrop} onClose={onClose}>
      <form className="a-form" onSubmit={(e: FormEvent) => { e.preventDefault(); save.mutate() }}>
        <Field label={t.number} type="number" min={1} value={f.number} onChange={(e) => setF({ ...f, number: Number(e.target.value) })} required />
        <Pair label={t.name} value={f.name} onChange={(name) => setF({ ...f, name })} />
        <Pick label={t.status} value={f.status} onChange={(e) => setF({ ...f, status: e.target.value })}>
          {['UPCOMING', 'LIVE', 'CLOSED'].map((s) => (
            <option key={s} value={s}>
              {t.dropStatus[s]}
            </option>
          ))}
        </Pick>
        <Field label={t.startsAt} hint={t.startsAtHint} type="datetime-local" value={f.startsAt} onChange={(e) => setF({ ...f, startsAt: e.target.value })} />
        {err && <p className="a-error">{err}</p>}
        <div className="a-actions">
          <button type="submit" className="a-btn a-btn-primary" disabled={save.isPending}>
            {t.save}
          </button>
          <button type="button" className="a-btn" onClick={onClose}>
            {t.cancel}
          </button>
          {drop && (
            <button type="button" className="a-btn a-btn-danger" style={{ marginInlineStart: 'auto' }} onClick={() => window.confirm(t.confirmDelete) && del.mutate()}>
              {t.delete}
            </button>
          )}
        </div>
      </form>
    </Modal>
  )
}

export default function Drops() {
  const { t, lang } = useT()
  const { data = [], isLoading } = useQuery({ queryKey: ['admin', 'drops'], queryFn: adminApi.drops })
  const [editing, setEditing] = useState<AdminDrop | 'new' | null>(null)
  return (
    <>
      <PageHead title={t.drops}>
        <button type="button" className="a-btn a-btn-primary" onClick={() => setEditing('new')}>
          {t.newDrop}
        </button>
      </PageHead>
      <div className="a-table-wrap">
        <table className="a-table">
          <thead>
            <tr>
              <th>{t.number}</th>
              <th>{t.name}</th>
              <th>{t.status}</th>
              <th className="a-hide-sm">{t.startsOn}</th>
              <th className="a-num">{t.productCount}</th>
            </tr>
          </thead>
          <tbody>
            {data.map((d) => (
              <tr key={d.id} data-href tabIndex={0} onClick={() => setEditing(d)} onKeyDown={(e) => e.key === 'Enter' && setEditing(d)}>
                <td className="a-mono">{String(d.number).padStart(2, '0')}</td>
                <td>
                  {d.name.en}
                  <div className="a-faint" lang="ar">{d.name.ar}</div>
                </td>
                <td>
                  <Badge s={d.status}>{t.dropStatus[d.status]}</Badge>
                </td>
                <td className="a-hide-sm a-muted">{fmtDate(d.startsAt, lang, true)}</td>
                <td className="a-num">{d.productCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {!isLoading && data.length === 0 && <p className="a-empty">{t.empty}</p>}
      </div>
      {editing && <DropForm drop={editing === 'new' ? null : editing} nextNumber={Math.max(0, ...data.map((d) => d.number)) + 1} onClose={() => setEditing(null)} />}
    </>
  )
}
