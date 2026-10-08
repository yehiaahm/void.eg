import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import type { Localized } from '../api/types'
import { useAuth } from '../store/auth'
import { useToast } from '../store/toast'
import { adminApi, type StoreStatus } from './api'
import { useT } from './i18n'
import { Modal, Pair, errText } from './ui'

export const useStoreStatus = () =>
  useQuery({ queryKey: ['admin', 'store-status'], queryFn: adminApi.storeStatus, refetchInterval: 60_000, refetchOnWindowFocus: true })

function useSaveStatus(onDone?: () => void) {
  const { t } = useT()
  const qc = useQueryClient()
  const flash = useToast()
  return useMutation({
    mutationFn: (v: { closed: boolean; message?: Localized }) => adminApi.setStoreStatus(v.closed, v.message),
    onSuccess: (s) => {
      const was = qc.getQueryData<StoreStatus>(['admin', 'store-status'])?.closed
      qc.setQueryData(['admin', 'store-status'], s)
      qc.invalidateQueries({ queryKey: ['settings'] })
      flash(was === s.closed ? t.saved : s.closed ? t.storeClosedToast : t.storeOpenedToast)
      onDone?.()
    },
  })
}

/** Close the store (with an optional message), or just edit the message while it's closed. */
function CloseDialog({ status, closing, onClose }: { status: StoreStatus; closing: boolean; onClose: () => void }) {
  const { t } = useT()
  const [message, setMessage] = useState<Localized>(status.message)
  const save = useSaveStatus(onClose)
  const submit = (e: FormEvent) => {
    e.preventDefault()
    save.mutate({ closed: closing || status.closed, message })
  }
  return (
    <Modal title={closing ? t.closeStoreTitle : t.closedMessage} onClose={onClose}>
      <form className="a-form" onSubmit={submit}>
        {closing && <p className="a-muted" style={{ margin: 0, lineHeight: 1.6 }}>{t.closeStoreBody}</p>}
        <Pair label={t.closedMessage} value={message} onChange={setMessage} multiline rows={3} hint={t.closedMessageHint} />
        {save.error && <p className="a-error">{errText(save.error, t)}</p>}
        <div className="a-actions" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="a-btn" onClick={onClose}>
            {t.cancel}
          </button>
          <button type="submit" className={closing ? 'a-btn a-btn-danger' : 'a-btn a-btn-primary'} disabled={save.isPending}>
            {save.isPending ? t.saving : closing ? t.closeNow : t.save}
          </button>
        </div>
      </form>
    </Modal>
  )
}

/**
 * The owner's "close the store" switch. While closed, shoppers get the waitlist page
 * (storefront pages/ClosedPage) and the API refuses their orders.
 */
export function StoreSwitch({ showWaitlistLink = true }: { showWaitlistLink?: boolean }) {
  const { t } = useT()
  const { user } = useAuth()
  const { data } = useStoreStatus()
  const [dialog, setDialog] = useState<null | 'close' | 'message'>(null)
  const closeDialog = useCallback(() => setDialog(null), [])
  const reopen = useSaveStatus()
  const canEdit = user?.role === 'ADMIN' || user?.role === 'OWNER'

  // Staff only need to hear about it when the store is closed.
  if (!data || (!canEdit && !data.closed)) return null
  const msg = data.message.en || data.message.ar

  return (
    <section className="a-card a-store" data-closed={data.closed} aria-label={t.storeStatus}>
      <div className="a-store-info">
        <span className="a-store-dot" aria-hidden="true" />
        <div>
          <h2>{data.closed ? t.storeClosed : t.storeOpen}</h2>
          <p>{data.closed ? t.storeClosedText : t.storeOpenText}</p>
          {data.closed && msg && (
            <blockquote>
              {data.message.en && (
                <div>
                  <bdi>“{data.message.en}”</bdi>
                </div>
              )}
              {data.message.ar && (
                <div>
                  <bdi lang="ar">«{data.message.ar}»</bdi>
                </div>
              )}
            </blockquote>
          )}
        </div>
      </div>
      <div className="a-actions">
        {data.closed && showWaitlistLink && (
          <Link className="a-btn" to="/admin/waitlist">
            {t.waitlist}
            <span className="a-mono a-muted">{data.waiting}</span>
          </Link>
        )}
        {canEdit && data.closed && (
          <button type="button" className="a-btn" onClick={() => setDialog('message')}>
            {t.editMessage}
          </button>
        )}
        {canEdit &&
          (data.closed ? (
            <button type="button" className="a-btn a-btn-primary" disabled={reopen.isPending} onClick={() => reopen.mutate({ closed: false })}>
              {reopen.isPending ? t.saving : t.openStore}
            </button>
          ) : (
            <button type="button" className="a-btn a-btn-danger" onClick={() => setDialog('close')}>
              {t.closeStore}
            </button>
          ))}
      </div>
      {reopen.error && <p className="a-error">{errText(reopen.error, t)}</p>}
      {dialog && <CloseDialog status={data} closing={dialog === 'close'} onClose={closeDialog} />}
    </section>
  )
}
