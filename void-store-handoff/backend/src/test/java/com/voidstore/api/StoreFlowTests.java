package com.voidstore.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end checks of the money paths against a real MySQL (Testcontainers):
 * checkout, stock locking under concurrency, coupons, cancellation, tracking, auth and roles.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"void.bootstrap.owner-email=owner@test.local",
		"void.bootstrap.owner-password=owner-test-password",
		"void.rate-limit-enabled=false",
		"void.jwt.secret=test-secret-0123456789abcdef0123456789abcdef",
		"spring.mail.host=localhost",
		"spring.mail.port=1"
})
class StoreFlowTests {

	@Container
	@ServiceConnection
	static MySQLContainer mysql = new MySQLContainer("mysql:8.4").withCommand("--default-time-zone=+00:00");

	@Value("${local.server.port}")
	int port;

	@Autowired
	JdbcTemplate db;

	final HttpClient http = HttpClient.newHttpClient();
	final JsonMapper json = JsonMapper.builder().build();

	record Res(int status, JsonNode body, HttpResponse<String> raw) {}

	Res call(String method, String path, Object body, String token, String cookie) throws Exception {
		var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
				.header("Content-Type", "application/json")
				.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
						: HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : json.writeValueAsString(body)));
		if (token != null) b.header("Authorization", "Bearer " + token);
		if (cookie != null) b.header("Cookie", cookie);
		var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
		return new Res(r.statusCode(), r.body().isBlank() ? null : json.readTree(r.body()), r);
	}

	Res post(String path, Object body) throws Exception {
		return call("POST", path, body, null, null);
	}

	String order(String slug, String size, int qty, String phone, String coupon) {
		return """
				{"items":[{"slug":"%s","size":"%s","qty":%d}],
				 "contact":{"email":"buyer@example.com","name":"Buyer","phone":"%s"},
				 "address":{"governorate":"cairo","city":"Nasr City","street":"1 Test St"},
				 "couponCode":%s,"paymentMethod":"COD","lang":"en"}
				""".formatted(slug, size, qty, phone, coupon == null ? "null" : "\"" + coupon + "\"");
	}

	int stock(String slug, String size) {
		return db.queryForObject("select v.stock from product_variants v join products p on p.id = v.product_id where p.slug = ? and v.size = ?",
				Integer.class, slug, size);
	}

	String login(String email, String password) throws Exception {
		var r = post("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
		assertThat(r.status()).isEqualTo(200);
		return r.body().get("accessToken").asText();
	}

	@BeforeEach
	void data() {
		db.update("update products set price = 100000 where slug = 'singularity-tee'");
		db.update("update product_variants v join products p on p.id = v.product_id set v.stock = 10 where p.slug = 'singularity-tee'");
		db.update("update shipping_zones set fee = 5000, enabled = true where code = 'cairo'");
		db.update("delete from coupon_redemptions");
		db.update("delete from coupons");
	}

	@Test
	void catalogShowsActiveProductsAndCapsStock() throws Exception {
		db.update("update product_variants v join products p on p.id = v.product_id set v.stock = 250 where p.slug = 'singularity-tee' and v.size = 'S'");
		var r = call("GET", "/products", null, null, null);
		assertThat(r.status()).isEqualTo(200);
		assertThat(r.body().size()).isEqualTo(4);
		var tee = findSlug(r.body(), "singularity-tee");
		assertThat(tee.get("price").asInt()).isEqualTo(100000);
		assertThat(tee.get("variants").get(0).get("stock").asInt()).isEqualTo(10); // exact inventory not revealed
		assertThat(findSlug(r.body(), "null-sweatpant").get("price").isNull()).isTrue(); // [PRICE] placeholder
	}

	@Test
	void placingAnOrderTotalsCorrectlyAndTakesStock() throws Exception {
		var r = post("/orders", order("singularity-tee", "M", 2, "+20 100 123 4567", null));
		assertThat(r.status()).isEqualTo(200);
		assertThat(r.body().get("total").asInt()).isEqualTo(2 * 100000 + 5000);
		assertThat(r.body().get("number").asText()).startsWith("VOID-");
		assertThat(stock("singularity-tee", "M")).isEqualTo(8);

		var tracked = post("/orders/track", "{\"number\":\"" + r.body().get("number").asText() + "\",\"phone\":\"01001234567\"}");
		assertThat(tracked.status()).isEqualTo(200);
		assertThat(tracked.body().get("status").asText()).isEqualTo("NEW");
		var wrongPhone = post("/orders/track", "{\"number\":\"" + r.body().get("number").asText() + "\",\"phone\":\"01111111111\"}");
		assertThat(wrongPhone.status()).isEqualTo(404);
	}

	@Test
	void ordersAreRejectedWhenTheyCannotBeFulfilled() throws Exception {
		assertThat(post("/orders", order("singularity-tee", "L", 11, "01001234567", null)).status()).isEqualTo(400); // qty > 10
		db.update("update product_variants v join products p on p.id = v.product_id set v.stock = 1 where p.slug = 'singularity-tee' and v.size = 'L'");
		var tooMany = post("/orders", order("singularity-tee", "L", 2, "01001234567", null));
		assertThat(tooMany.status()).isEqualTo(409);
		assertThat(tooMany.body().get("lines").get(0).get("issue").asText()).isEqualTo("insufficient_stock");
		var unpriced = post("/orders", order("null-sweatpant", "M", 1, "01001234567", null));
		assertThat(unpriced.body().get("lines").get(0).get("issue").asText()).isEqualTo("no_price");
		db.update("update shipping_zones set enabled = false where code = 'cairo'");
		assertThat(post("/orders", order("singularity-tee", "S", 1, "01001234567", null)).body().get("code").asText())
				.isEqualTo("shipping_unavailable");
		assertThat(post("/orders", order("singularity-tee", "S", 1, "12345", null)).body().get("code").asText()).isEqualTo("invalid_phone");
	}

	@Test
	void concurrentCheckoutsNeverOversell() throws Exception {
		db.update("update product_variants v join products p on p.id = v.product_id set v.stock = 3 where p.slug = 'singularity-tee' and v.size = 'XL'");
		var pool = Executors.newFixedThreadPool(8);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			Callable<Integer> c = () -> post("/orders", order("singularity-tee", "XL", 1, "01001234567", null)).status();
			results.add(pool.submit(c));
		}
		int ok = 0;
		for (var f : results) if (f.get() == 200) ok++;
		pool.shutdown();
		assertThat(ok).isEqualTo(3);
		assertThat(stock("singularity-tee", "XL")).isZero();
	}

	@Test
	void couponsApplyAndRespectPerCustomerLimit() throws Exception {
		db.update("insert into coupons (code, type, value, per_customer, active) values ('TEN', 'PERCENT', 10, 1, true)");
		var quote = post("/orders/quote", "{\"items\":[{\"slug\":\"singularity-tee\",\"size\":\"S\",\"qty\":1}],\"governorate\":\"cairo\",\"couponCode\":\"ten\"}");
		assertThat(quote.body().get("discount").asInt()).isEqualTo(10000);
		assertThat(quote.body().get("total").asInt()).isEqualTo(100000 - 10000 + 5000);

		assertThat(post("/orders", order("singularity-tee", "S", 1, "01001234567", "TEN")).status()).isEqualTo(200);
		var again = post("/orders", order("singularity-tee", "S", 1, "01001234567", "TEN"));
		assertThat(again.status()).isEqualTo(400);
		assertThat(again.body().get("code").asText()).isEqualTo("coupon_already_used");
	}

	@Test
	void cancellingRestocksAndStatusFlowIsEnforced() throws Exception {
		String owner = login("owner@test.local", "owner-test-password");
		var placed = post("/orders", order("singularity-tee", "M", 3, "01001234567", null));
		String number = placed.body().get("number").asText();
		assertThat(stock("singularity-tee", "M")).isEqualTo(7);

		var bad = call("POST", "/admin/orders/" + number + "/status", "{\"status\":\"DELIVERED\"}", owner, null);
		assertThat(bad.status()).isEqualTo(400); // NEW → DELIVERED is not allowed
		var cancelled = call("POST", "/admin/orders/" + number + "/status", "{\"status\":\"CANCELLED\",\"note\":\"test\"}", owner, null);
		assertThat(cancelled.status()).isEqualTo(200);
		assertThat(stock("singularity-tee", "M")).isEqualTo(10);
	}

	@Test
	void adminNeedsAStaffRole() throws Exception {
		assertThat(call("GET", "/admin/orders", null, null, null).status()).isEqualTo(401);
		var reg = post("/auth/register", "{\"name\":\"Cust\",\"email\":\"cust-" + System.nanoTime() + "@example.com\",\"password\":\"cust-password\"}");
		String customer = reg.body().get("accessToken").asText();
		assertThat(call("GET", "/admin/orders", null, customer, null).status()).isEqualTo(403);
		String owner = login("owner@test.local", "owner-test-password");
		assertThat(call("GET", "/admin/orders", null, owner, null).status()).isEqualTo(200);
		assertThat(call("GET", "/admin/dashboard?days=7", null, owner, null).status()).isEqualTo(200);
	}

	@Test
	void refreshTokensRotateAndReuseRevokesTheSession() throws Exception {
		var login = post("/auth/login", "{\"email\":\"owner@test.local\",\"password\":\"owner-test-password\"}");
		String first = cookie(login.raw());
		var refreshed = call("POST", "/auth/refresh", null, null, first);
		assertThat(refreshed.status()).isEqualTo(200);
		String second = cookie(refreshed.raw());
		assertThat(second).isNotEqualTo(first);
		// replaying the old token = theft signal → every session of the user is revoked
		assertThat(call("POST", "/auth/refresh", null, null, first).status()).isEqualTo(401);
		assertThat(call("POST", "/auth/refresh", null, null, second).status()).isEqualTo(401);
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		var r = post("/auth/login", "{\"email\":\"owner@test.local\",\"password\":\"nope-nope\"}");
		assertThat(r.status()).isEqualTo(401);
		assertThat(r.body().get("code").asText()).isEqualTo("bad_credentials");
	}

	@Test
	void guestCanCancelANewOrderOnly() throws Exception {
		String number = post("/orders", order("singularity-tee", "S", 2, "01001234567", null)).body().get("number").asText();
		assertThat(stock("singularity-tee", "S")).isEqualTo(8);
		var wrong = post("/orders/cancel", "{\"number\":\"" + number + "\",\"phone\":\"01111111111\"}");
		assertThat(wrong.status()).isEqualTo(404);
		var ok = post("/orders/cancel", "{\"number\":\"" + number + "\",\"phone\":\"01001234567\"}");
		assertThat(ok.status()).isEqualTo(200);
		assertThat(ok.body().get("status").asText()).isEqualTo("CANCELLED");
		assertThat(stock("singularity-tee", "S")).isEqualTo(10);
		assertThat(post("/orders/cancel", "{\"number\":\"" + number + "\",\"phone\":\"01001234567\"}").status()).isEqualTo(400);
	}

	@Test
	void exchangeAndReturnMoveStockExactlyOnce() throws Exception {
		String owner = login("owner@test.local", "owner-test-password");
		String number = post("/orders", order("singularity-tee", "M", 3, "01001234567", null)).body().get("number").asText();
		var track = post("/orders/track", "{\"number\":\"" + number + "\",\"phone\":\"01001234567\"}");
		long itemId = track.body().get("items").get(0).get("id").asLong();
		String guest = "\"number\":\"" + number + "\",\"phone\":\"01001234567\"";

		// not delivered yet → no returns
		var early = post("/orders/returns", "{" + guest + ",\"type\":\"EXCHANGE\",\"reason\":\"SIZE\",\"items\":[{\"itemId\":" + itemId + ",\"qty\":1,\"exchangeSize\":\"L\"}]}");
		assertThat(early.body().get("code").asText()).isEqualTo("not_returnable");

		for (String s : List.of("CONFIRMED", "SHIPPED", "DELIVERED")) {
			assertThat(call("POST", "/admin/orders/" + number + "/status", "{\"status\":\"" + s + "\"}", owner, null).status()).isEqualTo(200);
		}
		assertThat(stock("singularity-tee", "M")).isEqualTo(7);

		var tooMany = post("/orders/returns", "{" + guest + ",\"type\":\"RETURN\",\"reason\":\"DEFECT\",\"items\":[{\"itemId\":" + itemId + ",\"qty\":4}]}");
		assertThat(tooMany.body().get("code").asText()).isEqualTo("qty_too_high");

		// refunds only when the mistake is ours — a size change is an exchange
		var sizeRefund = post("/orders/returns", "{" + guest + ",\"type\":\"RETURN\",\"reason\":\"SIZE\",\"items\":[{\"itemId\":" + itemId + ",\"qty\":1}]}");
		assertThat(sizeRefund.body().get("code").asText()).isEqualTo("refund_not_allowed");

		// exchange 1 × M for L
		var ex = post("/orders/returns", "{" + guest + ",\"type\":\"EXCHANGE\",\"reason\":\"SIZE\",\"items\":[{\"itemId\":" + itemId + ",\"qty\":1,\"exchangeSize\":\"L\"}]}");
		assertThat(ex.status()).isEqualTo(200);
		assertThat(ex.body().get("items").get(0).get("returnableQty").asInt()).isEqualTo(2);
		String exNo = ex.body().get("returns").get(0).get("number").asText();
		assertThat(call("POST", "/admin/returns/" + exNo + "/status", "{\"status\":\"APPROVED\"}", owner, null).status()).isEqualTo(200);
		assertThat(stock("singularity-tee", "L")).isEqualTo(9); // replacement reserved
		assertThat(call("POST", "/admin/returns/" + exNo + "/status", "{\"status\":\"RECEIVED\"}", owner, null).status()).isEqualTo(200);
		assertThat(stock("singularity-tee", "M")).isEqualTo(8); // returned piece back on the shelf
		assertThat(call("POST", "/admin/returns/" + exNo + "/status", "{\"status\":\"COMPLETED\"}", owner, null).status()).isEqualTo(200);

		// return the other 2 → order becomes RETURNED without restocking twice
		var ret = post("/orders/returns", "{" + guest + ",\"type\":\"RETURN\",\"reason\":\"WRONG_ITEM\",\"items\":[{\"itemId\":" + itemId + ",\"qty\":2}]}");
		String retNo = ret.body().get("returns").get(0).get("number").asText();
		for (String s : List.of("APPROVED", "RECEIVED", "COMPLETED")) {
			assertThat(call("POST", "/admin/returns/" + retNo + "/status", "{\"status\":\"" + s + "\"}", owner, null).status()).isEqualTo(200);
		}
		assertThat(stock("singularity-tee", "M")).isEqualTo(10);
		var done = call("GET", "/admin/returns/" + retNo, null, owner, null);
		assertThat(done.body().get("refundAmount").asInt()).isEqualTo(2 * 100000);
		var after = post("/orders/track", "{" + guest + "}");
		assertThat(after.body().get("status").asText()).isEqualTo("RETURNED");
		assertThat(after.body().get("returns").size()).isEqualTo(2);
	}

	@Test
	void wishlistFollowsTheAccount() throws Exception {
		var reg = post("/auth/register", "{\"name\":\"Wish\",\"email\":\"wish-" + System.nanoTime() + "@example.com\",\"password\":\"wish-password\"}");
		String token = reg.body().get("accessToken").asText();
		assertThat(call("PUT", "/me/wishlist/singularity-tee", null, token, null).body().size()).isEqualTo(1);
		var merged = call("POST", "/me/wishlist/merge", "{\"slugs\":[\"singularity-tee\",\"null-sweatpant\",\"does-not-exist\"]}", token, null);
		assertThat(merged.body().size()).isEqualTo(2);
		assertThat(call("DELETE", "/me/wishlist/singularity-tee", null, token, null).body().get(0).asText()).isEqualTo("null-sweatpant");
		assertThat(call("PUT", "/me/wishlist/nope", null, token, null).status()).isEqualTo(404);
		assertThat(call("GET", "/me/wishlist", null, null, null).status()).isEqualTo(401);
	}

	@Test
	void closedStoreTakesNoOrdersAndCollectsTheWaitlist() throws Exception {
		String owner = login("owner@test.local", "owner-test-password");
		db.update("delete from waitlist_entries");
		try {
			var closed = call("PUT", "/admin/store-status", "{\"closed\":true,\"message\":{\"en\":\"Back on Friday\",\"ar\":\"\"}}", owner, null);
			assertThat(closed.status()).isEqualTo(200);
			var settings = call("GET", "/settings", null, null, null).body();
			assertThat(settings.get("storeClosed").asBoolean()).isTrue();
			assertThat(settings.get("closedMessage").get("en").asText()).isEqualTo("Back on Friday");

			var guestOrder = post("/orders", order("singularity-tee", "M", 1, "01001234567", null));
			assertThat(guestOrder.status()).isEqualTo(503);
			assertThat(guestOrder.body().get("code").asText()).isEqualTo("store_closed");
			assertThat(stock("singularity-tee", "M")).isEqualTo(10);
			assertThat(call("POST", "/orders", order("singularity-tee", "M", 1, "01001234567", null), owner, null).status())
					.isEqualTo(200); // staff can still test checkout

			assertThat(post("/waitlist", "{\"name\":\"Mona\",\"phone\":\"123\"}").body().get("code").asText()).isEqualTo("invalid_phone");
			assertThat(post("/waitlist", "{\"name\":\"Mona\",\"phone\":\"01001234567\",\"email\":\"nope\"}").body().get("fields").has("email")).isTrue();
			assertThat(post("/waitlist", "{\"name\":\"Mona\",\"phone\":\"+20 100 123 4567\",\"lang\":\"ar\"}").status()).isEqualTo(204);
			// same number again updates the entry instead of adding a duplicate
			assertThat(post("/waitlist", "{\"name\":\"Mona A.\",\"phone\":\"01001234567\",\"email\":\"Mona@Example.com\",\"notes\":\"Hoodie in L\"}").status()).isEqualTo(204);

			var list = call("GET", "/admin/waitlist?status=NEW", null, owner, null).body();
			assertThat(list.get("total").asInt()).isEqualTo(1);
			var entry = list.get("items").get(0);
			assertThat(entry.get("name").asText()).isEqualTo("Mona A.");
			assertThat(entry.get("phone").asText()).isEqualTo("01001234567");
			assertThat(entry.get("email").asText()).isEqualTo("mona@example.com");
			assertThat(entry.get("notes").asText()).isEqualTo("Hoodie in L");
			assertThat(call("GET", "/admin/store-status", null, owner, null).body().get("waiting").asInt()).isEqualTo(1);

			var done = call("PUT", "/admin/waitlist/" + entry.get("id").asLong() + "/contacted?value=true", null, owner, null);
			assertThat(done.body().get("contacted").asBoolean()).isTrue();
			assertThat(call("GET", "/admin/waitlist?status=NEW", null, owner, null).body().get("total").asInt()).isZero();
		} finally {
			call("PUT", "/admin/store-status", "{\"closed\":false}", owner, null);
		}
		assertThat(call("GET", "/settings", null, null, null).body().get("storeClosed").asBoolean()).isFalse();
		assertThat(call("GET", "/settings", null, null, null).body().get("closedMessage").get("en").asText()).isEqualTo("Back on Friday");
	}

	@Test
	void contactFormLandsInAdminMessages() throws Exception {
		String owner = login("owner@test.local", "owner-test-password");
		db.update("delete from contact_messages");
		assertThat(post("/contact", "{\"name\":\"Mona\",\"email\":\"nope\",\"message\":\"Hi\"}").body().get("fields").has("email")).isTrue();
		assertThat(post("/contact", "{\"name\":\"Mona\",\"email\":\"mona@example.com\",\"message\":\" \"}").body().get("fields").has("message")).isTrue();
		assertThat(post("/contact", "{\"name\":\"Mona\",\"email\":\"Mona@Example.com\",\"phone\":\"+20 100 123 4567\",\"message\":\"Is the hoodie back in L?\",\"lang\":\"ar\"}").status())
				.isEqualTo(204);

		var list = call("GET", "/admin/messages?status=NEW", null, owner, null).body();
		assertThat(list.get("total").asInt()).isEqualTo(1);
		var m = list.get("items").get(0);
		assertThat(m.get("email").asText()).isEqualTo("mona@example.com");
		assertThat(m.get("phone").asText()).isEqualTo("+20 100 123 4567");
		assertThat(m.get("lang").asText()).isEqualTo("ar");
		assertThat(call("GET", "/admin/messages/unread", null, owner, null).body().asInt()).isEqualTo(1);

		assertThat(call("PUT", "/admin/messages/" + m.get("id").asLong() + "/handled?value=true", null, owner, null).body().get("handled").asBoolean())
				.isTrue();
		assertThat(call("GET", "/admin/messages/unread", null, owner, null).body().asInt()).isZero();
		assertThat(call("GET", "/admin/messages", null, null, null).status()).isEqualTo(401);
	}

	private static String cookie(HttpResponse<String> r) {
		return r.headers().allValues("set-cookie").stream().filter(c -> c.startsWith("void_rt=")).findFirst()
				.map(c -> c.substring(0, c.indexOf(';'))).orElseThrow();
	}

	private static JsonNode findSlug(JsonNode arr, String slug) {
		for (var n : arr) if (slug.equals(n.get("slug").asText())) return n;
		throw new AssertionError("missing " + slug);
	}
}
