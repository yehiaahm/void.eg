package com.voidstore.api.admin;

/** @PreAuthorize expressions for the admin panel. Roles are cumulative: OWNER ⊃ ADMIN ⊃ STAFF. */
public final class Roles {
	private Roles() {}

	public static final String STAFF = "hasAnyRole('STAFF','ADMIN','OWNER')";
	public static final String ADMIN = "hasAnyRole('ADMIN','OWNER')";
	public static final String OWNER = "hasRole('OWNER')";
}
