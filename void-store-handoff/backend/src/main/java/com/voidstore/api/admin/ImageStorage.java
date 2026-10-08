package com.voidstore.api.admin;

import com.voidstore.api.common.ApiException;
import com.voidstore.api.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores product photos on disk under {uploads}/products/{sha256}.{ext}, served at /uploads/products/...
 * The type is checked from the file's magic bytes, not the client-supplied content type.
 * Swap this class for an S3 / R2 implementation when moving to object storage.
 */
@Service
public class ImageStorage {

	private static final long MAX_BYTES = 8L * 1024 * 1024;

	private final Path root;

	public ImageStorage(AppProperties props) {
		this.root = Path.of(props.uploadsDir()).toAbsolutePath().normalize();
	}

	public String store(MultipartFile file) {
		if (file == null || file.isEmpty()) throw ApiException.badRequest("no_file", "No file uploaded");
		if (file.getSize() > MAX_BYTES) throw ApiException.badRequest("too_large", "Images must be 8 MB or smaller");
		try {
			byte[] bytes = file.getBytes();
			String ext = sniff(bytes);
			if (ext == null) throw ApiException.badRequest("bad_type", "Upload a JPEG, PNG, WebP or AVIF image");
			String name = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 32) + "." + ext;
			Path dir = root.resolve("products");
			Files.createDirectories(dir);
			Path target = dir.resolve(name);
			if (!Files.exists(target)) {
				Path tmp = Files.createTempFile(dir, "up-", ".tmp");
				try (InputStream in = file.getInputStream()) {
					Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
				}
				Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			return "/uploads/products/" + name;
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("Could not store upload", e);
		}
	}

	/** Deletes the file if no other image row still points at it (caller checks). */
	public void delete(String url) {
		if (url == null || !url.startsWith("/uploads/products/")) return;
		Path p = root.resolve("products").resolve(url.substring("/uploads/products/".length())).normalize();
		if (!p.startsWith(root)) return;
		try {
			Files.deleteIfExists(p);
		} catch (IOException ignored) {
			// best effort
		}
	}

	private static String sniff(byte[] b) {
		if (b.length < 12) return null;
		if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
		if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
		if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
		if (b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p' && b[8] == 'a' && b[9] == 'v' && b[10] == 'i') return "avif";
		return null;
	}
}
