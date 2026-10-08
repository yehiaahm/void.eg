// Shapes returned by the Spring Boot API (/api/v1). Money is integer piastres (EGP × 100).

export type Lang = 'en' | 'ar'
export type Size = 'S' | 'M' | 'L' | 'XL'

export interface Localized {
  en: string
  ar: string
}

export type ImageKind = 'front' | 'back' | 'detail' | 'body'

export interface ProductImage {
  url: string
  kind: ImageKind
  alt?: Localized | null
}

export interface Variant {
  size: Size
  sku: string | null
  /** Capped at 10 by the API. */
  stock: number
}

export interface ProductDetails {
  fit: Localized
  fabric: Localized
  weight: Localized
  colour: Localized
  care: Localized
}

export interface Product {
  id: number
  slug: string
  dropId: number | null
  category: Localized
  name: Localized
  description: Localized
  /** `null` = not priced yet → shown as the [PRICE] placeholder, can't be ordered. */
  price: number | null
  compareAtPrice: number | null
  variants: Variant[]
  images: ProductImage[]
  details: ProductDetails
  sizeFit: Localized
}

export type DropStatus = 'upcoming' | 'live' | 'closed'

export interface Drop {
  id: number
  number: number
  name: Localized
  startsAt: string | null
  status: DropStatus
}

export interface StoreSettings {
  currentDrop: Drop | null
  nextDrop: Drop | null
  shippingReturns: Localized
  instagramUrl: string | null
  tiktokUrl: string | null
  contactEmail: string | null
  contactPhone: string | null
  codEnabled: boolean
  returnWindowDays: number
  /** The owner closed the store: shoppers get the waitlist page instead of the shop. */
  storeClosed: boolean
  /** Optional owner message for the closed page (empty = default copy). */
  closedMessage: Localized
}

export interface WaitlistInput {
  name: string
  phone: string
  email?: string
  notes?: string
  lang: Lang
}

export interface ContactInput {
  name: string
  email: string
  phone?: string
  message: string
  lang: Lang
}

export interface ShippingZone {
  code: string
  name: Localized
  fee: number
  etaDays: string
}

export interface StorePage {
  slug: string
  title: Localized
  body: Localized
  updatedAt: string
}

// ---- checkout

export interface LineInput {
  slug: string
  size: string
  qty: number
}

export type LineIssue = 'unavailable' | 'no_price' | 'out_of_stock' | 'insufficient_stock'

export interface QuoteLine {
  slug: string
  size: string
  qty: number
  name: Localized | null
  unitPrice: number | null
  lineTotal: number
  available: number
  image: string | null
  issue: LineIssue | null
}

export interface Quote {
  items: QuoteLine[]
  subtotal: number
  discount: number
  shipping: number | null
  total: number | null
  couponCode: string | null
  couponError: string | null
  freeShipping: boolean
  paymentMethods: string[]
}

export interface PlaceOrderInput {
  items: LineInput[]
  contact: { email: string; name: string; phone: string }
  address: { governorate: string; city: string; street: string; building?: string; notes?: string }
  note?: string
  couponCode?: string
  paymentMethod: 'COD'
  lang: Lang
  saveAddress?: boolean
}

export interface PlaceOrderResult {
  number: string
  total: number
  paymentMethod: string
  redirectUrl: string | null
}

export type OrderStatus = 'NEW' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'RETURNED'

export interface OrderItem {
  id: number
  slug: string
  name: string
  size: string
  sku: string | null
  image: string | null
  unitPrice: number
  qty: number
  lineTotal: number
  returnedQty: number
  /** Pieces the customer can still ask to return / exchange. */
  returnableQty: number
}

export interface OrderEvent {
  type: string
  fromStatus: OrderStatus | null
  toStatus: OrderStatus | null
  note: string
  actor: string
  at: string
}

export interface Order {
  number: string
  status: OrderStatus
  paymentMethod: string
  paymentStatus: string
  createdAt: string
  name: string
  phone: string
  email: string
  governorate: string
  city: string
  street: string
  building: string
  addressNotes: string
  items: OrderItem[]
  subtotal: number
  discount: number
  shipping: number
  total: number
  couponCode: string | null
  timeline: OrderEvent[]
  canCancel: boolean
  /** Deadline for return / exchange requests; null when not eligible. */
  returnUntil: string | null
  returns: ReturnRequest[]
}

export type ReturnType = 'RETURN' | 'EXCHANGE'
export type ReturnReason = 'SIZE' | 'WRONG_ITEM' | 'DEFECT' | 'NOT_AS_DESCRIBED' | 'CHANGED_MIND' | 'OTHER'
export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'RECEIVED' | 'COMPLETED' | 'CANCELLED'

export interface ReturnRequest {
  number: string
  type: ReturnType
  status: ReturnStatus
  reason: ReturnReason
  note: string
  refundAmount: number | null
  createdAt: string
  items: { orderItemId: number; slug: string; name: string; size: string; image: string | null; unitPrice: number; qty: number; exchangeSize: string | null }[]
  timeline: { fromStatus: string | null; toStatus: string; at: string }[]
}

export interface ReturnInput {
  type: ReturnType
  reason: ReturnReason
  note?: string
  items: { itemId: number; qty: number; exchangeSize?: string }[]
}

export interface OrderSummary {
  number: string
  status: OrderStatus
  createdAt: string
  itemCount: number
  total: number
}

export interface Address {
  id: number
  name: string
  phone: string
  governorate: string
  city: string
  street: string
  building: string
  notes: string
  isDefault: boolean
}

export type AddressInput = Omit<Address, 'id'>
