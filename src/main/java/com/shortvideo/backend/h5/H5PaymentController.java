package com.shortvideo.backend.h5;

import com.shortvideo.backend.h5.dto.PaymentCallbackRequest;
import com.shortvideo.backend.h5.dto.PaymentRequest;
import com.shortvideo.backend.h5.dto.PaymentResponse;
import com.shortvideo.backend.h5.dto.PaymentSettlementResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/h5/payments")
public class H5PaymentController {

    private final H5UserService userService;
    private final String callbackToken;

    public H5PaymentController(
            H5UserService userService,
            @Value("${app.security.payment-callback-token:}") String callbackToken
    ) {
        this.userService = userService;
        this.callbackToken = callbackToken == null ? "" : callbackToken.trim();
    }

    @PostMapping
    public PaymentResponse createPayment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) PaymentRequest request
    ) {
        return userService.createPayment(request, authorization);
    }

    @GetMapping("/{paymentId}")
    public PaymentSettlementResponse payment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String paymentId
    ) {
        return userService.getPaymentSettlement(paymentId, authorization);
    }

    @PostMapping("/{paymentId}/simulate-success")
    public PaymentSettlementResponse simulateSuccess(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String paymentId
    ) {
        return userService.simulatePaymentSuccess(paymentId, authorization);
    }

    @PostMapping("/callback")
    public PaymentResponse callback(
            @RequestHeader(value = "X-Payment-Callback-Token", required = false) String token,
            @RequestBody(required = false) PaymentCallbackRequest request
    ) {
        requireCallbackToken(token);
        return userService.paymentCallback(request);
    }

    private void requireCallbackToken(String token) {
        if (callbackToken.isBlank()) {
            return;
        }

        if (!callbackToken.equals(token == null ? "" : token.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Payment callback token is invalid");
        }
    }
}
