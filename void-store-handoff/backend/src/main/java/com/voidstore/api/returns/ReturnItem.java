package com.voidstore.api.returns;

import com.voidstore.api.order.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "return_items")
public class ReturnItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id")
	private ReturnRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_item_id")
	private OrderItem orderItem;

	@Column(nullable = false)
	private int qty;

	/** Size wanted instead (EXCHANGE only). */
	@Column(name = "exchange_size")
	private String exchangeSize;

	protected ReturnItem() {}

	public ReturnItem(ReturnRequest request, OrderItem orderItem, int qty, String exchangeSize) {
		this.request = request;
		this.orderItem = orderItem;
		this.qty = qty;
		this.exchangeSize = exchangeSize;
	}

	public Long getId() { return id; }
	public OrderItem getOrderItem() { return orderItem; }
	public int getQty() { return qty; }
	public String getExchangeSize() { return exchangeSize; }
}
