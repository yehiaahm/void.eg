import { http, request } from '../api/http'
import type { Localized } from '../api/types'

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface Kpis {
  revenue: number
  orders: number
  aov: number
  itemsSold: number
  cancelled: number
}
export interface Dashboard {
  days: number
  current: Kpis
  previous: Kpis
  series: { date: string; revenue: number; orders: number }[]
  bestSellers: { slug: string; name: string; qty: number; revenue: number }[]
  lowStock: { productId: number; slug: string; name: Localized; size: string; stock: number }[]
  statusCounts: Record<string, number>
  recent: OrderRow[]
}

export interface OrderRow {
  id: number
  number: string
  status: string
  paymentMethod: string
  paymentStatus: string
  createdAt: string
  name: string
  phone: string
  governorate: string
  itemCount: number
  total: number
}

export interface AdminOrder {
  id: number
  number: string
  status: string
  nextStatuses: string[]
  paymentMethod: string
  paymentStatus: string
  paymentRef: string | null
  createdAt: string
  updatedAt: string
  lang: string
  customerId: number | null
  email: string
  name: string
  phone: string
  governorate: string
  city: string
  street: string
  building: string
  addressNotes: string
  customerNote: string
  items: { slug: string; name: string; size: string; sku: string | null; image: string | null; unitPrice: number; qty: number; lineTotal: number }[]
  subtotal: number
  discount: number
  shipping: number
  total: number
  couponCode: string | null
  events: { type: string; fromStatus: string | null; toStatus: string | null; note: string; actor: string; at: string }[]
}

export interface AdminVariant {
  id?: number | null
  size: string
  sku: string | null
  stock: number
}
export interface AdminImage {
  id: number
  url: string
  kind: string
  position: number
  alt: Localized
}
export interface AdminProduct {
  id: number
  slug: string
  dropId: number | null
  status: string
  category: Localized
  name: Localized
  description: Localized
  price: number | null
  compareAtPrice: number | null
  fit: Localized
  fabric: Localized
  weight: Localized
  colour: Localized
  care: Localized
  sizeFit: Localized
  sortOrder: number
  variants: AdminVariant[]
  images: AdminImage[]
  createdAt: string
}
export type ProductInput = Omit<AdminProduct, 'id' | 'images' | 'createdAt'>

export interface ProductRow {
  id: number
  slug: string
  status: string
  name: Localized
  price: number | null
  totalStock: number
  lowSizes: number
  saves: number
  image: string | null
  dropNumber: number | null
  sortOrder: number
}

export interface AdminDrop {
  id: number
  number: number
  name: Localized
  startsAt: string | null
  status: string
  productCount: number
}

export interface CustomerRow {
  id: number
  name: string
  email: string
  phone: string | null
  createdAt: string
  orders: number
  spent: number
}
export interface CustomerDetail {
  id: number
  name: string
  email: string
  phone: string | null
  createdAt: string
  lastLoginAt: string | null
  enabled: boolean
  orderCount: number
  spent: number
  orders: { number: string; status: string; createdAt: string; itemCount: number; total: number }[]
  addresses: { name: string; phone: string; governorate: string; city: string; street: string; building: string; isDefault: boolean }[]
}

export interface AdminCoupon {
  id: number
  code: string
  type: 'PERCENT' | 'FIXED' | 'FREE_SHIPPING'
  value: number
  minSubtotal: number
  startsAt: string | null
  endsAt: string | null
  maxUses: number | null
  perCustomer: number | null
  usedCount: number
  active: boolean
  createdAt: string
}
export type CouponInput = Omit<AdminCoupon, 'id' | 'usedCount' | 'createdAt'>

export interface AdminZone {
  id: number
  code: string
  name: Localized
  fee: number | null
  etaDays: string
  enabled: boolean
}

export interface AdminPage {
  slug: string
  title: Localized
  body: Localized
  updatedAt: string
}

export interface ReturnRow {
  number: string
  orderNumber: string
  type: string
  status: string
  reason: string
  customer: string
  phone: string
  pieces: number
  value: number
  createdAt: string
}
export interface ReturnDetail {
  number: string
  orderNumber: string
  type: string
  status: string
  nextStatuses: string[]
  reason: string
  note: string
  refundAmount: number | null
  itemsValue: number
  createdAt: string
  customer: string
  phone: string
  email: string
  items: { item: { orderItemId: number; slug: string; name: string; size: string; image: string | null; unitPrice: number; qty: number; exchangeSize: string | null }; exchangeStock: number | null }[]
  events: { fromStatus: string | null; toStatus: string; note: string; actor: string; at: string }[]
}

export interface StoreStatus {
  closed: boolean
  message: Localized
  /** Waitlist entries nobody has contacted yet. */
  waiting: number
}
export interface WaitlistEntry {
  id: number
  name: string
  phone: string
  email: string | null
  notes: string
  lang: string
  contacted: boolean
  createdAt: string
}

export interface ContactMessage {
  id: number
  name: string
  email: string
  phone: string | null
  message: string
  lang: string
  handled: boolean
  createdAt: string
}

export interface StaffMember {
  id: number
  name: string
  email: string
  role: string
  enabled: boolean
  lastLoginAt: string | null
  createdAt: string
}

const qs = (o: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams()
  Object.entries(o).forEach(([k, v]) => v !== undefined && v !== null && v !== '' && p.set(k, String(v)))
  const s = p.toString()
  return s ? `?${s}` : ''
}

type Json = Record<string, unknown>
const j = (v: unknown) => v as Json

export const adminApi = {
  dashboard: (days: number) => http.get<Dashboard>(`/admin/dashboard${qs({ days })}`, true),

  orders: (f: { status?: string; q?: string; from?: string; to?: string; page?: number }) =>
    http.get<PageResult<OrderRow>>(`/admin/orders${qs({ ...f, size: 25 })}`, true),
  order: (n: string) => http.get<AdminOrder>(`/admin/orders/${encodeURIComponent(n)}`, true),
  setStatus: (n: string, status: string, note?: string) => http.post<AdminOrder>(`/admin/orders/${n}/status`, { status, note }, true),
  addNote: (n: string, note: string) => http.post<AdminOrder>(`/admin/orders/${n}/notes`, { note }, true),
  editAddress: (n: string, a: Json) => http.put<AdminOrder>(`/admin/orders/${n}/address`, a),
  exportCsv: async (f: { status?: string; q?: string; from?: string; to?: string }) => {
    const r = await request<Response>('GET', `/admin/orders/export.csv${qs(f)}`, undefined, { auth: true, raw: true })
    return r.blob()
  },

  products: () => http.get<ProductRow[]>('/admin/products', true),
  product: (id: number) => http.get<AdminProduct>(`/admin/products/${id}`, true),
  createProduct: (p: ProductInput) => http.post<AdminProduct>('/admin/products', j(p), true),
  updateProduct: (id: number, p: ProductInput) => http.put<AdminProduct>(`/admin/products/${id}`, j(p)),
  updateStock: (id: number, variants: AdminVariant[]) => http.put<AdminProduct>(`/admin/products/${id}/stock`, { variants }),
  deleteProduct: (id: number) => http.del<void>(`/admin/products/${id}`),
  uploadImage: (id: number, file: File, kind: string) => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('kind', kind)
    return http.post<AdminProduct>(`/admin/products/${id}/images`, fd, true)
  },
  updateImage: (id: number, imageId: number, kind: string, alt: Localized) =>
    http.put<AdminProduct>(`/admin/products/${id}/images/${imageId}`, { kind, alt }),
  reorderImages: (id: number, ids: number[]) => http.put<AdminProduct>(`/admin/products/${id}/images/order`, { ids }),
  deleteImage: (id: number, imageId: number) => http.del<AdminProduct>(`/admin/products/${id}/images/${imageId}`),

  drops: () => http.get<AdminDrop[]>('/admin/drops', true),
  createDrop: (d: Json) => http.post<AdminDrop>('/admin/drops', d, true),
  updateDrop: (id: number, d: Json) => http.put<AdminDrop>(`/admin/drops/${id}`, d),
  deleteDrop: (id: number) => http.del<void>(`/admin/drops/${id}`),

  customers: (q: string, page: number) => http.get<PageResult<CustomerRow>>(`/admin/customers${qs({ q, page, size: 25 })}`, true),
  customer: (id: number) => http.get<CustomerDetail>(`/admin/customers/${id}`, true),
  setCustomerEnabled: (id: number, value: boolean) => http.put<void>(`/admin/customers/${id}/enabled?value=${value}`),

  coupons: () => http.get<AdminCoupon[]>('/admin/coupons', true),
  createCoupon: (c: CouponInput) => http.post<AdminCoupon>('/admin/coupons', j(c), true),
  updateCoupon: (id: number, c: CouponInput) => http.put<AdminCoupon>(`/admin/coupons/${id}`, j(c)),
  deleteCoupon: (id: number) => http.del<void>(`/admin/coupons/${id}`),

  zones: () => http.get<AdminZone[]>('/admin/shipping-zones', true),
  saveZones: (z: { id: number; fee: number | null; etaDays: string; enabled: boolean }[]) => http.put<AdminZone[]>('/admin/shipping-zones', z),

  settings: () => http.get<Record<string, string>>('/admin/settings', true),
  saveSettings: (s: Record<string, string>) => http.put<Record<string, string>>('/admin/settings', s),
  pages: () => http.get<AdminPage[]>('/admin/pages', true),
  savePage: (slug: string, title: Localized, body: Localized) => http.put<AdminPage>(`/admin/pages/${slug}`, { title, body }),

  returns: (f: { status?: string; q?: string; page?: number }) => http.get<PageResult<ReturnRow>>(`/admin/returns${qs(f)}`, true),
  returnDetail: (n: string) => http.get<ReturnDetail>(`/admin/returns/${encodeURIComponent(n)}`, true),
  setReturnStatus: (n: string, status: string, note?: string, refundAmount?: number | null) =>
    http.post<ReturnDetail>(`/admin/returns/${n}/status`, { status, note, refundAmount }, true),
  returnsForOrder: (orderNumber: string) => http.get<ReturnRow[]>(`/admin/returns/by-order/${orderNumber}`, true),

  storeStatus: () => http.get<StoreStatus>('/admin/store-status', true),
  setStoreStatus: (closed: boolean, message?: Localized) => http.put<StoreStatus>('/admin/store-status', { closed, message }),
  waitlist: (f: { status?: string; q?: string; page?: number }) => http.get<PageResult<WaitlistEntry>>(`/admin/waitlist${qs({ ...f, size: 25 })}`, true),
  setContacted: (id: number, value: boolean) => http.put<WaitlistEntry>(`/admin/waitlist/${id}/contacted?value=${value}`),
  deleteWaitlist: (id: number) => http.del<void>(`/admin/waitlist/${id}`),
  exportWaitlist: async (f: { status?: string; q?: string }) => {
    const r = await request<Response>('GET', `/admin/waitlist/export.csv${qs(f)}`, undefined, { auth: true, raw: true })
    return r.blob()
  },

  messages: (f: { status?: string; q?: string; page?: number }) => http.get<PageResult<ContactMessage>>(`/admin/messages${qs({ ...f, size: 25 })}`, true),
  unreadMessages: () => http.get<number>('/admin/messages/unread', true),
  setHandled: (id: number, value: boolean) => http.put<ContactMessage>(`/admin/messages/${id}/handled?value=${value}`),
  deleteMessage: (id: number) => http.del<void>(`/admin/messages/${id}`),

  staff: () => http.get<StaffMember[]>('/admin/staff', true),
  createStaff: (s: Json) => http.post<StaffMember>('/admin/staff', s, true),
  updateStaff: (id: number, s: Json) => http.put<StaffMember>(`/admin/staff/${id}`, s),
}

// ---- money helpers: the API speaks piastres, people type pounds

export const toPiastres = (egp: string): number | null => {
  const v = egp.trim().replace(/,/g, '')
  if (!v) return null
  const n = Number(v)
  return Number.isFinite(n) && n >= 0 ? Math.round(n * 100) : NaN
}
export const fromPiastres = (p: number | null | undefined) => (p == null ? '' : String(p / 100))

const nf = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 })
export const egp = (p: number | null | undefined) => (p == null ? '—' : `${nf.format(p / 100)} EGP`)
export const compact = (p: number) => {
  const v = p / 100
  return v >= 1_000_000 ? `${nf.format(Math.round(v / 100_000) / 10)}M` : v >= 10_000 ? `${nf.format(Math.round(v / 100) / 10)}K` : nf.format(Math.round(v))
}

export const fmtDate = (iso: string | null | undefined, lang: string, withTime = false) =>
  iso
    ? new Intl.DateTimeFormat(lang === 'ar' ? 'ar-EG-u-nu-latn' : 'en-GB', {
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}),
        timeZone: 'Africa/Cairo',
      }).format(new Date(iso))
    : '—'

/** ISO instant ↔ value for <input type="datetime-local"> in Cairo time. */
export function toCairoInput(iso: string | null): string {
  if (!iso) return ''
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Africa/Cairo', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(new Date(iso))
  const g = (t: string) => parts.find((p) => p.type === t)?.value ?? '00'
  return `${g('year')}-${g('month')}-${g('day')}T${g('hour')}:${g('minute')}`
}
export function fromCairoInput(v: string): string | null {
  if (!v) return null
  // Find the UTC instant whose Cairo wall-clock equals v (handles DST).
  const asUtc = Date.parse(`${v}:00Z`)
  const offsetAt = (ms: number) => {
    const s = toCairoInput(new Date(ms).toISOString())
    return Date.parse(`${s}:00Z`) - ms
  }
  const guess = asUtc - offsetAt(asUtc)
  return new Date(asUtc - offsetAt(guess)).toISOString()
}
