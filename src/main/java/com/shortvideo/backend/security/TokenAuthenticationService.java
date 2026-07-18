package com.shortvideo.backend.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.shortvideo.backend.admin.AdminAuthService;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import com.shortvideo.backend.h5.H5UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class TokenAuthenticationService {

    private final AdminAuthService adminAuthService;
    private final H5UserService h5UserService;

    public TokenAuthenticationService(AdminAuthService adminAuthService, H5UserService h5UserService) {
        this.adminAuthService = adminAuthService;
        this.h5UserService = h5UserService;
    }

    public Optional<Authentication> authenticate(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String legacyAdminToken = request.getHeader("X-Admin-Token");

        Optional<AdminProfileResponse> admin = adminAuthService.authenticatedProfile(authorization, legacyAdminToken);
        if (admin.isPresent()) {
            return admin.map(this::adminAuthentication);
        }

        return h5UserService.authenticatedUserId(authorization)
                .map(this::h5Authentication);
    }

    private Authentication adminAuthentication(AdminProfileResponse profile) {
        List<String> permissions = safePermissions(profile.permissions());
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        permissions.stream()
                .filter((permission) -> permission != null && !permission.isBlank())
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        AppPrincipal principal = new AppPrincipal(
                profile.id(),
                profile.username(),
                AppPrincipalType.ADMIN,
                profile.role(),
                permissions
        );
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private Authentication h5Authentication(Long userId) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_H5_USER"));
        AppPrincipal principal = new AppPrincipal(
                userId,
                "h5-user-" + userId,
                AppPrincipalType.H5_USER,
                "h5_user",
                List.of()
        );
        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    private List<String> safePermissions(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(permissions);
    }
}
