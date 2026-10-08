package com.voidstore.api.web;

import com.voidstore.api.catalog.Product;
import com.voidstore.api.catalog.ProductImage;
import com.voidstore.api.catalog.ProductRepository;
import com.voidstore.api.config.AppProperties;
import com.voidstore.api.content.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Serves the built storefront (static/index.html) for every page route, with per-page title,
 * description, Open Graph and hreflang tags injected server-side — so links shared on Instagram,
 * WhatsApp or Facebook (which don't run JavaScript) get a proper preview. Only active in
 * production builds, where the frontend's dist/ is packaged into classpath:/static/.
 */
@RestController
public class SpaController {

	private static final String HOME_DESC_EN = "VOID — Egyptian streetwear in limited drops. Oversized tees & hoodies, technical jackets, unisex basics.";
	private static final String HOME_DESC_AR = "VOID — ستريت وير مصري في دروبات محدودة. تيشيرتات وهوديز أوفرسايز، جواكت تقنية، وأساسيات للجنسين.";

	private final ProductRepository products;
	private final AppProperties props;
	private final SettingsService settings;
	private volatile String template;

	public SpaController(ProductRepository products, AppProperties props, SettingsService settings) {
		this.products = products;
		this.props = props;
		this.settings = settings;
	}

	private Optional<String> template() {
		if (template == null) {
			var res = new ClassPathResource("static/index.html");
			if (!res.exists()) return Optional.empty();
			try {
				template = res.getContentAsString(StandardCharsets.UTF_8);
			} catch (IOException e) {
				return Optional.empty();
			}
		}
		return Optional.of(template);
	}

	private String origin() {
		return props.frontendUrl().split(",")[0].trim().replaceAll("/$", "");
	}

	@GetMapping({"/{lang:en|ar}", "/{lang:en|ar}/", "/{lang:en|ar}/**"})
	@Transactional(readOnly = true)
	public ResponseEntity<String> page(@PathVariable String lang, HttpServletRequest req) {
		String path = req.getRequestURI();
		String rest = path.substring(3); // after /en or /ar
		boolean ar = "ar".equals(lang);
		String title = ar ? "VOID — المتجر" : "VOID — Shop";
		String desc = ar ? HOME_DESC_AR : HOME_DESC_EN;
		String image = origin() + "/assets/void-logo-original.jpg";
		String type = "website";
		HttpStatus status = HttpStatus.OK;

		if (rest.startsWith("/product/")) {
			String slug = rest.substring("/product/".length()).replaceAll("/$", "");
			var p = products.findBySlug(slug).filter(x -> x.getStatus() == Product.Status.ACTIVE);
			if (p.isPresent()) {
				var prod = p.get();
				title = (ar && !prod.getNameAr().isBlank() ? prod.getNameAr() : prod.getNameEn()) + " — VOID";
				String d = ar && !prod.getDescriptionAr().isBlank() ? prod.getDescriptionAr() : prod.getDescriptionEn();
				desc = d.length() > 200 ? d.substring(0, 197) + "…" : d;
				image = prod.getImages().stream().filter(i -> "front".equals(i.getKind())).map(ProductImage::getUrl).findFirst()
						.or(() -> prod.getImages().stream().map(ProductImage::getUrl).findFirst())
						.map(u -> u.startsWith("http") ? u : origin() + u).orElse(image);
				type = "product";
			} else {
				status = HttpStatus.NOT_FOUND;
			}
		}
		boolean privatePage = rest.startsWith("/account") || rest.startsWith("/checkout") || rest.startsWith("/order/");
		return render(lang, title, desc, image, type, rest, status, privatePage);
	}

	/** "/" redirects to the shopper's language (Accept-Language), defaulting to English. */
	@GetMapping("/")
	public ResponseEntity<Void> root(HttpServletRequest req) {
		String al = req.getHeader("Accept-Language");
		String lang = al != null && al.trim().toLowerCase().startsWith("ar") ? "ar" : "en";
		return ResponseEntity.status(HttpStatus.FOUND).header("Location", "/" + lang).build();
	}

	@GetMapping({"/admin", "/admin/", "/admin/**"})
	public ResponseEntity<String> admin() {
		return render("en", "Admin — VOID", "", null, "website", null, HttpStatus.OK, true);
	}

	private ResponseEntity<String> render(String lang, String title, String desc, String image, String type, String rest,
			HttpStatus status, boolean noindex) {
		var tpl = template();
		if (tpl.isEmpty()) return ResponseEntity.notFound().build();
		boolean ar = "ar".equals(lang);
		// Lets the storefront show the "we'll be back" page on first paint instead of flashing the shop.
		String closed = rest != null && settings.storeClosed() ? " data-closed=\"1\"" : "";
		String o = origin();
		var head = new StringBuilder();
		head.append("<title>").append(esc(title)).append("</title>\n");
		if (!desc.isBlank()) head.append("<meta name=\"description\" content=\"").append(esc(desc)).append("\" />\n");
		if (noindex) head.append("<meta name=\"robots\" content=\"noindex\" />\n");
		if (rest != null) {
			head.append("<link rel=\"canonical\" href=\"").append(esc(o + "/" + lang + rest)).append("\" />\n");
			head.append("<link rel=\"alternate\" hreflang=\"en\" href=\"").append(esc(o + "/en" + rest)).append("\" />\n");
			head.append("<link rel=\"alternate\" hreflang=\"ar\" href=\"").append(esc(o + "/ar" + rest)).append("\" />\n");
			head.append("<link rel=\"alternate\" hreflang=\"x-default\" href=\"").append(esc(o + "/en" + rest)).append("\" />\n");
			head.append("<meta property=\"og:url\" content=\"").append(esc(o + "/" + lang + rest)).append("\" />\n");
		}
		head.append("<meta property=\"og:type\" content=\"").append(type).append("\" />\n");
		head.append("<meta property=\"og:title\" content=\"").append(esc(title)).append("\" />\n");
		if (!desc.isBlank()) head.append("<meta property=\"og:description\" content=\"").append(esc(desc)).append("\" />\n");
		if (image != null) head.append("<meta property=\"og:image\" content=\"").append(esc(image)).append("\" />\n");
		head.append("<meta property=\"og:locale\" content=\"").append(ar ? "ar_EG" : "en_US").append("\" />\n");
		head.append("<meta name=\"twitter:card\" content=\"summary_large_image\" />\n");

		String html = tpl.get()
				// drop the template's generic tags; ours replace them
				.replaceAll("(?s)<title>.*?</title>", "")
				.replaceAll("<meta name=\"description\"[^>]*>", "")
				.replaceAll("<meta property=\"og:(title|type|image)\"[^>]*>", "")
				.replace("<html lang=\"en\" dir=\"ltr\">", "<html lang=\"" + lang + "\" dir=\"" + (ar ? "rtl" : "ltr") + "\"" + closed + ">")
				.replace("</head>", head + "</head>");
		return ResponseEntity.status(status)
				.contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
				.cacheControl(CacheControl.noCache())
				.body(html);
	}

	private static String esc(String s) {
		return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
	}
}
