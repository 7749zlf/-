package com.shortvideo.backend.h5;

import java.util.List;

import com.shortvideo.backend.h5.dto.ApiOkResponse;
import com.shortvideo.backend.h5.dto.BindPhoneRequest;
import com.shortvideo.backend.h5.dto.BindPhoneResponse;
import com.shortvideo.backend.h5.dto.ChangePasswordRequest;
import com.shortvideo.backend.h5.dto.H5PreferencesRequest;
import com.shortvideo.backend.h5.dto.H5PreferencesResponse;
import com.shortvideo.backend.h5.dto.H5ProfileResponse;
import com.shortvideo.backend.h5.dto.RechargeRequest;
import com.shortvideo.backend.h5.dto.RechargeResponse;
import com.shortvideo.backend.h5.dto.UpdateProfileRequest;
import com.shortvideo.backend.h5.dto.UploadSignatureRequest;
import com.shortvideo.backend.h5.dto.UploadSignatureResponse;
import com.shortvideo.backend.h5.dto.WalletResponse;
import com.shortvideo.backend.h5.dto.WatchHistoryRequest;
import com.shortvideo.backend.h5.dto.WatchHistoryResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/h5")
public class H5MeController {

    private final H5UserService userService;

    public H5MeController(H5UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public H5ProfileResponse me(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.currentUser(authorization, deviceId);
    }

    @GetMapping("/me/profile")
    public H5ProfileResponse profile(@RequestParam(required = false) String deviceId) {
        return userService.profileByDevice(deviceId);
    }

    @PutMapping("/me/profile")
    public H5ProfileResponse updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) UpdateProfileRequest request
    ) {
        return userService.updateProfile(request, authorization);
    }

    @PostMapping("/me/avatar/upload-signature")
    public UploadSignatureResponse avatarUploadSignature(@RequestBody(required = false) UploadSignatureRequest request) {
        return userService.avatarUploadSignature(request);
    }

    @PostMapping("/me/bind-phone")
    public BindPhoneResponse bindPhone(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) BindPhoneRequest request
    ) {
        return userService.bindPhone(
                authorization,
                request == null ? null : request.deviceId(),
                request == null ? null : request.phone()
        );
    }

    @PutMapping("/me/password")
    public ApiOkResponse changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ChangePasswordRequest request
    ) {
        return userService.changePassword(authorization);
    }

    @GetMapping("/me/wallet")
    public WalletResponse wallet(@RequestParam(required = false) String deviceId) {
        return userService.wallet(deviceId);
    }

    @GetMapping("/me/preferences")
    public H5PreferencesResponse preferences(@RequestParam(required = false) String deviceId) {
        return userService.preferences(deviceId);
    }

    @PutMapping("/me/preferences")
    public H5PreferencesResponse updatePreferences(@RequestBody(required = false) H5PreferencesRequest request) {
        return userService.updatePreferences(request);
    }

    @GetMapping("/me/watch-history")
    public List<WatchHistoryResponse> watchHistory(@RequestParam(required = false) String deviceId) {
        return userService.listWatchHistory(deviceId);
    }

    @PostMapping("/me/watch-history")
    public WatchHistoryResponse saveWatchHistory(@RequestBody(required = false) WatchHistoryRequest request) {
        return userService.saveWatchHistory(request);
    }

    @DeleteMapping("/me/watch-history")
    public ApiOkResponse clearWatchHistory(@RequestParam(required = false) String deviceId) {
        return userService.clearWatchHistory(deviceId);
    }

    @GetMapping({"/me/recharges", "/me/recharge"})
    public List<RechargeResponse> recharges(@RequestParam(required = false) String deviceId) {
        return userService.listRecharges(deviceId);
    }

    @PostMapping({"/me/recharges", "/me/recharge"})
    public RechargeResponse recharge(@RequestBody(required = false) RechargeRequest request) {
        return userService.recharge(request);
    }
}
