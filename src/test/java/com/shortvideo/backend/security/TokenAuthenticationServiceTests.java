package com.shortvideo.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.shortvideo.backend.admin.AdminAuthService;
import com.shortvideo.backend.admin.dto.AdminProfileResponse;
import com.shortvideo.backend.h5.H5UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

class TokenAuthenticationServiceTests {

    @Test
    void routesAdminBearerTokenWithoutCheckingH5Tokens() {
        AdminAuthService adminAuthService = mock(AdminAuthService.class);
        H5UserService h5UserService = mock(H5UserService.class);
        TokenAuthenticationService service = new TokenAuthenticationService(adminAuthService, h5UserService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer adm_token");

        when(adminAuthService.authenticatedProfile("Bearer adm_token", null))
                .thenReturn(Optional.of(new AdminProfileResponse(
                        7L,
                        "admin",
                        "Admin",
                        "administrator",
                        List.of("dashboard")
                )));

        Authentication authentication = service.authenticate(request).orElseThrow();

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "dashboard");
        verify(adminAuthService).authenticatedProfile("Bearer adm_token", null);
        verifyNoInteractions(h5UserService);
    }

    @Test
    void routesH5BearerTokenWithoutCheckingAdminTokens() {
        AdminAuthService adminAuthService = mock(AdminAuthService.class);
        H5UserService h5UserService = mock(H5UserService.class);
        TokenAuthenticationService service = new TokenAuthenticationService(adminAuthService, h5UserService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer h5_token");

        when(h5UserService.authenticatedUserId("Bearer h5_token"))
                .thenReturn(Optional.of(42L));

        Authentication authentication = service.authenticate(request).orElseThrow();

        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_H5_USER");
        verify(h5UserService).authenticatedUserId("Bearer h5_token");
        verifyNoInteractions(adminAuthService);
    }
}
