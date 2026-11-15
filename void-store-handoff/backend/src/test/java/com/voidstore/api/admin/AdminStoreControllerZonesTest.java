package com.voidstore.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.voidstore.api.admin.AdminStoreController.ZoneReq;
import com.voidstore.api.auth.AuditLog;
import com.voidstore.api.auth.ClientIpResolver;
import com.voidstore.api.auth.RefreshTokenRepository;
import com.voidstore.api.common.ApiException;
import com.voidstore.api.content.PageRepository;
import com.voidstore.api.content.SettingsService;
import com.voidstore.api.order.CouponRepository;
import com.voidstore.api.order.OrderRepository;
import com.voidstore.api.shipping.ShippingZone;
import com.voidstore.api.shipping.ShippingZoneRepository;
import com.voidstore.api.user.AddressRepository;
import com.voidstore.api.user.UserRepository;
import jakarta.validation.Validation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** PV-5: shipping-zone input is validated element by element. */
class AdminStoreControllerZonesTest {

	private ShippingZoneRepository zones;
	private AdminStoreController controller;
	private ShippingZone zone;

	@BeforeEach
	void setUp() {
		zones = mock(ShippingZoneRepository.class);
		zone = mock(ShippingZone.class);
		when(zones.findById(anyLong())).thenReturn(Optional.of(zone));
		controller = new AdminStoreController(mock(UserRepository.class), mock(AddressRepository.class), mock(OrderRepository.class),
				mock(CouponRepository.class), zones, mock(SettingsService.class), mock(PageRepository.class),
				mock(PasswordEncoder.class), mock(RefreshTokenRepository.class),
				new AuditLog(new ClientIpResolver(ClientIpResolver.DEFAULT_TRUSTED)),
				Validation.buildDefaultValidatorFactory().getValidator());
	}

	private void assertRejected(List<ZoneReq> body) {
		assertThatThrownBy(() -> controller.saveZones(body)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.getCode()).isEqualTo("validation"));
		verify(zone, never()).setFee(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void anAbsurdFeeIsRejectedBeforeAnythingIsSaved() {
		assertRejected(List.of(new ZoneReq(1L, Integer.MAX_VALUE, "2-3", true)));
		assertRejected(List.of(new ZoneReq(1L, 50_00, "2-3", true), new ZoneReq(2L, 10_000_01, "2-3", true)));
	}

	@Test
	void negativeFeesAndOverlongEtaAreRejected() {
		assertRejected(List.of(new ZoneReq(1L, -1, "2-3", true)));
		assertRejected(List.of(new ZoneReq(1L, 100, "x".repeat(17), false)));
		assertRejected(List.of(new ZoneReq(null, 100, "2-3", false)));
	}

	@Test
	void emptyNullAndHugeListsAreRejected() {
		assertRejected(List.of());
		assertRejected(null);
		var many = new ArrayList<ZoneReq>();
		for (int i = 0; i < AdminStoreController.MAX_ZONES + 1; i++) many.add(new ZoneReq((long) i + 1, 100, "1", false));
		assertRejected(many);
	}

	@Test
	void aValidListIsApplied() {
		controller.saveZones(List.of(new ZoneReq(1L, 5000, "2-3", true), new ZoneReq(2L, 0, "", false)));
		verify(zone).setFee(5000);
		verify(zone).setFee(0);
	}

	@Test
	void enablingWithoutAFeeStillFails() {
		assertThatThrownBy(() -> controller.saveZones(List.of(new ZoneReq(1L, null, "2-3", true)))).isInstanceOf(ApiException.class);
	}
}
