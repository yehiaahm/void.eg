package com.voidstore.api.order;

import com.voidstore.api.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "orders")
public class Order {

	public enum Status {
		NEW, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, RETURNED;

		private static final Map<Status, Set<Status>> NEXT = Map.of(
				NEW, EnumSet.of(CONFIRMED, CANCELLED),
				CONFIRMED, EnumSet.of(SHIPPED, CANCELLED),
				SHIPPED, EnumSet.of(DELIVERED, RETURNED),
				DELIVERED, EnumSet.of(RETURNED),
				CANCELLED, EnumSet.noneOf(Status.class),
				RETURNED, EnumSet.noneOf(Status.class));

		public boolean canMoveTo(Status next) {
			return NEXT.get(this).contains(next);
		}

		public Set<Status> next() {
			return NEXT.get(this);
		}

		/** Stock goes back on the shelf when an order ends up here. */
		public boolean restocks() {
			return this == CANCELLED || this == RETURNED;
		}
	}

	public enum PaymentMethod { COD, PAYMOB }

	public enum PaymentStatus { PENDING, PAID, FAILED, REFUNDED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String number;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(nullable = false) private String lang = "en";
	@Column(nullable = false) private String email;
	@Column(nullable = false) private String name;
	@Column(nullable = false) private String phone;
	@Column(nullable = false) private String governorate;
	@Column(nullable = false) private String city;
	@Column(nullable = false) private String street;
	@Column(nullable = false) private String building = "";
	@Column(name = "address_notes", nullable = false) private String addressNotes = "";
	@Column(name = "customer_note", nullable = false) private String customerNote = "";

	@Column(nullable = false) private int subtotal;
	@Column(nullable = false) private int discount;
	@Column(nullable = false) private int shipping;
	@Column(nullable = false) private int total;
	@Column(name = "coupon_code") private String couponCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_method", nullable = false)
	private PaymentMethod paymentMethod = PaymentMethod.COD;

	@Enumerated(EnumType.STRING)
	@Column(name = "payment_status", nullable = false)
	private PaymentStatus paymentStatus = PaymentStatus.PENDING;

	@Column(name = "payment_ref") private String paymentRef;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.NEW;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", insertable = false, updatable = false)
	private Instant updatedAt;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	@BatchSize(size = 50)
	private List<OrderItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<OrderEvent> events = new ArrayList<>();

	public int itemCount() {
		return items.stream().mapToInt(OrderItem::getQty).sum();
	}

	public Long getId() { return id; }
	public String getNumber() { return number; }
	public void setNumber(String number) { this.number = number; }
	public User getUser() { return user; }
	public void setUser(User user) { this.user = user; }
	public String getLang() { return lang; }
	public void setLang(String lang) { this.lang = "ar".equals(lang) ? "ar" : "en"; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
	public String getGovernorate() { return governorate; }
	public void setGovernorate(String v) { this.governorate = v; }
	public String getCity() { return city; }
	public void setCity(String v) { this.city = v; }
	public String getStreet() { return street; }
	public void setStreet(String v) { this.street = v; }
	public String getBuilding() { return building; }
	public void setBuilding(String v) { this.building = v == null ? "" : v; }
	public String getAddressNotes() { return addressNotes; }
	public void setAddressNotes(String v) { this.addressNotes = v == null ? "" : v; }
	public String getCustomerNote() { return customerNote; }
	public void setCustomerNote(String v) { this.customerNote = v == null ? "" : v; }
	public int getSubtotal() { return subtotal; }
	public void setSubtotal(int v) { this.subtotal = v; }
	public int getDiscount() { return discount; }
	public void setDiscount(int v) { this.discount = v; }
	public int getShipping() { return shipping; }
	public void setShipping(int v) { this.shipping = v; }
	public int getTotal() { return total; }
	public void setTotal(int v) { this.total = v; }
	public String getCouponCode() { return couponCode; }
	public void setCouponCode(String v) { this.couponCode = v; }
	public PaymentMethod getPaymentMethod() { return paymentMethod; }
	public void setPaymentMethod(PaymentMethod v) { this.paymentMethod = v; }
	public PaymentStatus getPaymentStatus() { return paymentStatus; }
	public void setPaymentStatus(PaymentStatus v) { this.paymentStatus = v; }
	public String getPaymentRef() { return paymentRef; }
	public void setPaymentRef(String v) { this.paymentRef = v; }
	public Status getStatus() { return status; }
	public void setStatus(Status status) { this.status = status; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public List<OrderItem> getItems() { return items; }
	public List<OrderEvent> getEvents() { return events; }
}
