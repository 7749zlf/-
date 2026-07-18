package com.shortvideo.backend.h5;

import java.util.List;

import com.shortvideo.backend.h5.dto.ApiOkResponse;
import com.shortvideo.backend.h5.dto.BindPhoneRequest;
import com.shortvideo.backend.h5.dto.BindPhoneResponse;
import com.shortvideo.backend.h5.dto.ChangePasswordRequest;
import com.shortvideo.backend.h5.dto.H5PreferencesRequest;
import com.shortvideo.backend.h5.dto.H5PreferencesResponse;
import com.shortvideo.backend.h5.dto.H5ProfileResponse;
import com.shortvideo.backend.h5.dto.H5ProfileSummaryResponse;
import com.shortvideo.backend.h5.dto.H5RefundRequest;
import com.shortvideo.backend.h5.dto.H5RefundRequestResponse;
import com.shortvideo.backend.h5.dto.RechargeRequest;
import com.shortvideo.backend.h5.dto.RechargeResponse;
import com.shortvideo.backend.h5.dto.UpdateProfileRequest;
import com.shortvideo.backend.h5.dto.UploadSignatureRequest;
import com.shortvideo.backend.h5.dto.UploadSignatureResponse;
import com.shortvideo.backend.h5.dto.WalletResponse;
import com.shortvideo.backend.h5.dto.WatchHistoryRequest;
import com.shortvideo.backend.h5.dto.WatchHistoryResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    public H5ProfileResponse profile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.currentUser(authorization, deviceId);
    }

    @GetMapping("/me/summary")
    public H5ProfileSummaryResponse summary(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.profileSummary(authorization, deviceId);
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

    @PostMapping(value = "/me/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadSignatureResponse uploadAvatar(@RequestPart("file") MultipartFile file) {
        return userService.uploadAvatar(file);
    }

    @PostMapping("/me/bind-phone")
    public BindPhoneResponse bindPhone(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) BindPhoneRequest request
    ) {
        return userService.bindPhone(
                authorization,
                request == null ? null : request.deviceId(),
                request == null ? null : request.phone(),
                request == null ? null : request.code()
        );
    }

    @PutMapping("/me/password")
    public ApiOkResponse changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) ChangePasswordRequest request
    ) {
        return userService.changePassword(request, authorization);
    }

    @GetMapping("/me/wallet")
    public WalletResponse wallet(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.wallet(authorization, deviceId);
    }

    @GetMapping("/me/preferences")
    public H5PreferencesResponse preferences(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.preferences(authorization, deviceId);
    }

    @PutMapping("/me/preferences")
    public H5PreferencesResponse updatePreferences(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) H5PreferencesRequest request
    ) {
        return userService.updatePreferences(request, authorization);
    }

    @GetMapping("/me/watch-history")
    public List<WatchHistoryResponse> watchHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.listWatchHistory(authorization, deviceId);
    }

    @PostMapping("/me/watch-history")
    public WatchHistoryResponse saveWatchHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) WatchHistoryRequest request
    ) {
        return userService.saveWatchHistory(request, authorization);
    }

    @DeleteMapping("/me/watch-history")
    public ApiOkResponse clearWatchHistory(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.clearWatchHistory(authorization, deviceId);
    }

    @GetMapping({"/me/recharges", "/me/recharge"})
    public List<RechargeResponse> recharges(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.listRecharges(authorization, deviceId);
    }

    @PostMapping({"/me/recharges", "/me/recharge"})
    public RechargeResponse recharge(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) RechargeRequest request
    ) {
        return userService.recharge(request, authorization);
    }

    @GetMapping("/me/refund-requests")
    public List<H5RefundRequestResponse> refundRequests(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String deviceId
    ) {
        return userService.listRefundRequests(authorization, deviceId);
    }

    @PostMapping("/me/refund-requests")
    public H5RefundRequestResponse requestRefund(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) H5RefundRequest request
    ) {
        return userService.requestRefund(request, authorization);
    }
}
