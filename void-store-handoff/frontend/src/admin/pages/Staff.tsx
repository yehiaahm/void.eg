import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useAuth } from '../../store/auth'
import { adminApi, fmtDate, type StaffMember } from '../api'
import { useT } from '../i18n'
import { Badge, Field, Modal, PageHead, Pick, errText } from '../ui'

function StaffForm({ member, onClose }: { member: StaffMember | null; onClose: () => void }) {
  const { t } = useT()
  const qc = useQueryClient()
  const [f, setF] = useState({ name: member?.name ?? '', email: member?.email ?? '', role: member?.role ?? 'STAFF', enabled: member?.enabled ?? true, password: '' })
  const [err, setErr] = useState<string | null>(null)
  const save = useMutation({
    mutationFn: () =>
      member
        ? adminApi.updateStaff(member.id, { role: f.role, enabled: f.enabled, password: f.password || null })
        : adminApi.createStaff({ name: f.name, email: f.email, role: f.role, password: f.password }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'staff'] })
      onClose()
    },
    onError: (e) => setErr(errText(e, t)),
  })
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if ((!member || f.password) && f.password.length < 10) return setErr(t.tempPassword)
    save.mutate()
  }
  return (
    <Modal title={member ? member.name : t.newStaff} onClose={onClose}>
      <form className="a-form" onSubmit={submit}>
        {!member && (
          <>
            <Field label={t.name} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} required />
            <Field label={t.email} type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} dir="ltr" required />
          </>
        )}
        <Pick label={t.role} hint={t.roleHint} value={f.role} onChange={(e) => setF({ ...f, role: e.target.value })}>
          {['STAFF', 'ADMIN', ...(member ? ['CUSTOMER'] : [])].map((r) => (
            <option key={r} value={r}>
              {t.roles[r]}
            </option>
          ))}
        </Pick>
        {member && (
          <label className="a-switch">
            <input type="checkbox" checked={f.enabled} onChange={(e) => setF({ ...f, enabled: e.target.checked })} />
            {t.active}
          </label>
        )}
        <Field label={member ? t.newPassword : t.tempPassword} type="password" autoComplete="new-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
        {err && <p className="a-error">{err}</p>}
        <div className="a-actions">
          <button type="submit" className="a-btn a-btn-primary" disabled={save.isPending}>
            {member ? t.save : t.create}
          </button>
          <button type="button" className="a-btn" onClick={onClose}>
            {t.cancel}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default function Staff() {
  const { t, lang } = useT()
  const { user } = useAuth()
  const { data = [] } = useQuery({ queryKey: ['admin', 'staff'], queryFn: adminApi.staff })
  const [editing, setEditing] = useState<StaffMember | 'new' | null>(null)
  return (
    <>
      <PageHead title={t.staff} sub={t.roleHint}>
        <button type="button" className="a-btn a-btn-primary" onClick={() => setEditing('new')}>
          {t.newStaff}
        </button>
      </PageHead>
      <div className="a-table-wrap">
        <table className="a-table">
          <thead>
            <tr>
              <th>{t.name}</th>
              <th>{t.role}</th>
              <th className="a-hide-sm">{t.lastLogin}</th>
              <th>{t.status}</th>
            </tr>
          </thead>
          <tbody>
            {data.map((m) => {
              const self = m.id === user?.id
              const editable = !self && m.role !== 'OWNER'
              return (
                <tr key={m.id} data-href={editable ? '' : undefined} tabIndex={editable ? 0 : -1} onClick={() => editable && setEditing(m)} onKeyDown={(e) => editable && e.key === 'Enter' && setEditing(m)}>
                  <td>
                    {m.name} {self && <span className="a-faint">({t.you})</span>}
                    <div className="a-faint" dir="ltr" style={{ textAlign: 'start', fontSize: 12 }}>
                      {m.email}
                    </div>
                  </td>
                  <td>{t.roles[m.role]}</td>
                  <td className="a-hide-sm a-muted">{fmtDate(m.lastLoginAt, lang, true)}</td>
                  <td>
                    <Badge s={m.enabled ? 'on' : 'off'}>{m.enabled ? t.active : t.disabled}</Badge>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
      {editing && <StaffForm member={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </>
  )
}
