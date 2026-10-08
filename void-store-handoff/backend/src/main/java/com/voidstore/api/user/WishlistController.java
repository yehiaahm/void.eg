package com.voidstore.api.user;

import com.voidstore.api.auth.CurrentUser;
import com.voidstore.api.catalog.Product;
import com.voidstore.api.catalog.ProductRepository;
import com.voidstore.api.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Saved products of the signed-in customer. Guests keep theirs in the browser and merge on sign-in. */
@RestController
@RequestMapping("/api/v1/me/wishlist")
public class WishlistController {

	public record MergeReq(@NotNull @Size(max = 200) List<String> slugs) {}

	private final WishlistRepository wishlist;
	private final ProductRepository products;
	private final UserRepository users;

	public WishlistController(WishlistRepository wishlist, ProductRepository products, UserRepository users) {
		this.wishlist = wishlist;
		this.products = products;
		this.users = users;
	}

	/** Slugs, newest first. */
	@GetMapping
	@Transactional(readOnly = true)
	public List<String> list() {
		return wishlist.findAllForUser(CurrentUser.require().id()).stream().map(w -> w.getProduct().getSlug()).toList();
	}

	@PutMapping("/{slug}")
	@Transactional
	public List<String> add(@PathVariable String slug) {
		save(CurrentUser.require().id(), slug, true);
		wishlist.flush();
		return list();
	}

	@DeleteMapping("/{slug}")
	@Transactional
	public List<String> remove(@PathVariable String slug) {
		wishlist.findOne(CurrentUser.require().id(), slug).ifPresent(wishlist::delete);
		wishlist.flush();
		return list();
	}

	/** Called right after sign-in with the guest's saved slugs. */
	@PostMapping("/merge")
	@Transactional
	public List<String> merge(@Valid @RequestBody MergeReq req) {
		long uid = CurrentUser.require().id();
		req.slugs().stream().distinct().forEach(s -> save(uid, s, false));
		wishlist.flush();
		return list();
	}

	private void save(long userId, String slug, boolean strict) {
		if (wishlist.findOne(userId, slug).isPresent()) return;
		var product = products.findBySlug(slug).filter(p -> p.getStatus() != Product.Status.DRAFT).orElse(null);
		if (product == null) {
			if (strict) throw ApiException.notFound("Product");
			return;
		}
		wishlist.save(new WishlistItem(users.getReferenceById(userId), product));
	}
}
