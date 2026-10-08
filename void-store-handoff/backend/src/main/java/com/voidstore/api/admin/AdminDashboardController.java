package com.voidstore.api.admin;

import static com.voidstore.api.admin.AdminOrderController.CAIRO;

import com.voidstore.api.catalog.VariantRepository;
import com.voidstore.api.common.Localized;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.order.Order;
import com.voidstore.api.order.OrderItem;
import com.voidstore.api.order.OrderRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize(Roles.STAFF)
public class AdminDashboardController {

	public record Kpis(long revenue, long orders, long aov, long itemsSold, long cancelled) {}

	public record DayPoint(String date, long revenue, long orders) {}

	public record BestSeller(String slug, String name, long qty, long revenue) {}

	public record LowStock(long productId, String slug, Localized name, String size, int stock) {}

	public record Dashboard(int days, Kpis current, Kpis previous, List<DayPoint> series, List<BestSeller> bestSellers,
			List<LowStock> lowStock, Map<String, Long> statusCounts, List<AdminOrderController.OrderRowDto> recent) {}

	private final OrderRepository orders;
	private final VariantRepository variants;
	private final SettingsService settings;

	public AdminDashboardController(OrderRepository orders, VariantRepository variants, SettingsService settings) {
		this.orders = orders;
		this.variants = variants;
		this.settings = settings;
	}

	private static boolean counts(Order o) {
		return o.getStatus() != Order.Status.CANCELLED && o.getStatus() != Order.Status.RETURNED;
	}

	private static Kpis kpis(List<Order> list) {
		var valid = list.stream().filter(AdminDashboardController::counts).toList();
		long revenue = valid.stream().mapToLong(Order::getTotal).sum();
		long items = valid.stream().mapToLong(Order::itemCount).sum();
		long cancelled = list.size() - valid.size();
		return new Kpis(revenue, valid.size(), valid.isEmpty() ? 0 : revenue / valid.size(), items, cancelled);
	}

	@GetMapping
	@Transactional(readOnly = true)
	public Dashboard get(@RequestParam(defaultValue = "30") int days) {
		days = Math.max(1, Math.min(365, days));
		var today = LocalDate.now(CAIRO);
		Instant to = today.plusDays(1).atStartOfDay(CAIRO).toInstant();
		Instant from = today.minusDays(days - 1L).atStartOfDay(CAIRO).toInstant();
		Instant prevFrom = today.minusDays(2L * days - 1).atStartOfDay(CAIRO).toInstant();

		var current = orders.findCreatedBetween(from, to);
		var previous = orders.findCreatedBetween(prevFrom, from);

		Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
		for (int i = days - 1; i >= 0; i--) byDay.put(today.minusDays(i), new long[2]);
		for (var o : current) {
			if (!counts(o)) continue;
			var d = LocalDate.ofInstant(o.getCreatedAt(), CAIRO);
			var acc = byDay.get(d);
			if (acc != null) {
				acc[0] += o.getTotal();
				acc[1]++;
			}
		}
		List<DayPoint> series = new ArrayList<>();
		byDay.forEach((d, v) -> series.add(new DayPoint(d.toString(), v[0], v[1])));

		Map<String, long[]> sellers = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (var o : current) {
			if (!counts(o)) continue;
			for (OrderItem i : o.getItems()) {
				var acc = sellers.computeIfAbsent(i.getSlug(), k -> new long[2]);
				acc[0] += i.getQty();
				acc[1] += i.getLineTotal();
				names.putIfAbsent(i.getSlug(), i.getName());
			}
		}
		var best = sellers.entrySet().stream()
				.map(e -> new BestSeller(e.getKey(), names.get(e.getKey()), e.getValue()[0], e.getValue()[1]))
				.sorted(Comparator.comparingLong(BestSeller::qty).reversed()).limit(5).toList();

		int threshold = settings.integer("low_stock_threshold", 3);
		var low = variants.findLowStock(threshold).stream().limit(20)
				.map(v -> new LowStock(v.getProduct().getId(), v.getProduct().getSlug(),
						Localized.of(v.getProduct().getNameEn(), v.getProduct().getNameAr()), v.getSize(), v.getStock()))
				.toList();

		Map<String, Long> statusCounts = new LinkedHashMap<>();
		var enumCounts = new EnumMap<Order.Status, Long>(Order.Status.class);
		for (var s : Order.Status.values()) enumCounts.put(s, orders.countByStatus(s));
		enumCounts.forEach((k, v) -> statusCounts.put(k.name(), v));

		var recent = orders.search(null, null, null, null, PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt")))
				.map(AdminOrderController::row).getContent();

		return new Dashboard(days, kpis(current), kpis(previous), series, best, low, statusCounts, recent);
	}
}
