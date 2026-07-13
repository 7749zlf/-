package com.shortvideo.backend.h5;

import com.shortvideo.backend.h5.dto.ApiOkResponse;
import com.shortvideo.backend.h5.dto.GuestLoginRequest;
import com.shortvideo.backend.h5.dto.H5AuthResponse;
import com.shortvideo.backend.h5.dto.LogoutRequest;
import com.shortvideo.backend.h5.dto.OauthLoginRequest;
import com.shortvideo.backend.h5.dto.PasswordLoginRequest;
import com.shortvideo.backend.h5.dto.PhoneLoginRequest;
import com.shortvideo.backend.h5.dto.RefreshTokenRequest;
import com.shortvideo.backend.h5.dto.RefreshTokenResponse;
import com.shortvideo.backend.h5.dto.RegisterRequest;
import com.shortvideo.backend.h5.dto.SmsCodeRequest;
import com.shortvideo.backend.h5.dto.SmsCodeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5/auth")
public class H5AuthController {

    private final H5UserService userService;

    public H5AuthController(H5UserService userService) {
        this.userService = userService;
    }

    @PostMapping({"/send-code", "/sms-code"})
    public SmsCodeResponse sendCode(@RequestBody(required = false) SmsCodeRequest request) {
        return userService.sendCode(request);
    }

    @PostMapping("/guest")
    public H5AuthResponse guest(@RequestBody(required = false) GuestLoginRequest request) {
        return userService.guestLogin(request);
    }

    @PostMapping({"/login-phone", "/login"})
    public H5AuthResponse phoneLogin(@RequestBody(required = false) PhoneLoginRequest request) {
        return userService.phoneLogin(request);
    }

    @PostMapping("/login-password")
    public H5AuthResponse passwordLogin(@RequestBody(required = false) PasswordLoginRequest request) {
        return userService.passwordLogin(request);
    }

    @PostMapping("/register")
    public H5AuthResponse register(@RequestBody(required = false) RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/oauth-login")
    public H5AuthResponse oauthLogin(@RequestBody(required = false) OauthLoginRequest request) {
        return userService.oauthLogin(request);
    }

    @PostMapping("/refresh")
    public RefreshTokenResponse refresh(@RequestBody(required = false) RefreshTokenRequest request) {
        return userService.refresh(request);
    }

    @PostMapping("/logout")
    public ApiOkResponse logout(@RequestBody(required = false) LogoutRequest request) {
        return userService.logout(request == null ? null : request.refreshToken());
    }
}
