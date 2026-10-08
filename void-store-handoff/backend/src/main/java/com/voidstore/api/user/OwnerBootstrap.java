package com.voidstore.api.user;

import com.voidstore.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first OWNER account from OWNER_EMAIL / OWNER_PASSWORD when none exists yet.
 * Credentials come from the environment only — nothing is hard-coded.
 */
@Component
public class OwnerBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final AppProperties props;

	public OwnerBootstrap(UserRepository users, PasswordEncoder encoder, AppProperties props) {
		this.users = users;
		this.encoder = encoder;
		this.props = props;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (users.existsByRole(User.Role.OWNER)) return;
		var b = props.bootstrap();
		if (b == null || isBlank(b.ownerEmail()) || isBlank(b.ownerPassword())) {
			log.warn("No OWNER account yet. Set OWNER_EMAIL and OWNER_PASSWORD and restart to create one.");
			return;
		}
		if (b.ownerPassword().length() < 10) {
			log.error("OWNER_PASSWORD must be at least 10 characters — owner not created.");
			return;
		}
		var existing = users.findByEmail(User.normalizeEmail(b.ownerEmail()));
		var owner = existing.orElseGet(() -> new User(b.ownerEmail(), "", isBlank(b.ownerName()) ? "Owner" : b.ownerName(), User.Role.OWNER));
		owner.setRole(User.Role.OWNER);
		owner.setPasswordHash(encoder.encode(b.ownerPassword()));
		owner.setEnabled(true);
		users.save(owner);
		log.info("Created OWNER account {}", owner.getEmail());
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
