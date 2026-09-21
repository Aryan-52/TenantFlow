package io.github.aryan52.tenantflow.security;

import io.github.aryan52.tenantflow.entity.User;
import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthenticatedUser implements UserDetails {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID id;
	private final String email;
	private final String name;

	public static AuthenticatedUser from(User user) {
		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getName());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return Collections.emptyList();
	}

	@Override
	public String getPassword() {
		return "";
	}

	@Override
	public String getUsername() {
		return email;
	}
}
