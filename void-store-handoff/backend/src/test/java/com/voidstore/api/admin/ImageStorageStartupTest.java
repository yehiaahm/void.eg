package com.voidstore.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Uploads on Railway vanish on every deploy unless a volume is mounted at UPLOADS_DIR; the start-up log says so. */
class ImageStorageStartupTest {

	@TempDir
	Path tmp;

	private Path uploads() throws Exception {
		Path root = tmp.resolve("data/uploads");
		Files.createDirectories(root.resolve("products"));
		return root;
	}

	@Test
	void noWarningsOutsideRailwayWithAWritableDirectory() throws Exception {
		assertThat(ImageStorage.startupWarnings(uploads(), Map.of())).isEmpty();
	}

	@Test
	void warnsWhenTheDirectoryIsMissing() {
		assertThat(ImageStorage.startupWarnings(tmp.resolve("nope"), Map.of()))
				.singleElement().asString().contains("not writable");
	}

	@Test
	void warnsOnRailwayWithoutAVolume() throws Exception {
		Path root = uploads();
		assertThat(ImageStorage.startupWarnings(root, Map.of("RAILWAY_PROJECT_ID", "p")))
				.singleElement().asString().contains("No Railway volume").contains(root.toString());
	}

	@Test
	void warnsOnRailwayWhenTheVolumeIsMountedElsewhere() throws Exception {
		Path root = uploads();
		var env = Map.of("RAILWAY_ENVIRONMENT_NAME", "production", "RAILWAY_VOLUME_MOUNT_PATH", tmp.resolve("other").toString());
		assertThat(ImageStorage.startupWarnings(root, env)).singleElement().asString().contains("volume is at");
	}

	@Test
	void quietOnRailwayWhenTheVolumeHoldsTheUploads() throws Exception {
		Path root = uploads();
		var exact = Map.of("RAILWAY_PROJECT_ID", "p", "RAILWAY_VOLUME_MOUNT_PATH", root.toString());
		var parent = Map.of("RAILWAY_PROJECT_ID", "p", "RAILWAY_VOLUME_MOUNT_PATH", tmp.resolve("data").toString());
		assertThat(ImageStorage.startupWarnings(root, exact)).isEmpty();
		assertThat(ImageStorage.startupWarnings(root, parent)).isEmpty();
	}
}
