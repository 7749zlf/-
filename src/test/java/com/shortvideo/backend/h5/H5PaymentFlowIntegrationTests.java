package com.shortvideo.backend.h5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import com.shortvideo.backend.admin.AdminUserManagementService;
import com.shortvideo.backend.admin.AdminOrderManagementService;
import com.shortvideo.backend.admin.AdminFinanceService;
import com.shortvideo.backend.admin.dto.AdminUserDetailResponse;
import com.shortvideo.backend.h5.dto.GuestLoginRequest;
import com.shortvideo.backend.h5.dto.H5AuthResponse;
import com.shortvideo.backend.h5.dto.H5RefundRequest;
import com.shortvideo.backend.h5.dto.PaymentCallbackRequest;
import com.shortvideo.backend.h5.dto.PaymentRequest;
import com.shortvideo.backend.h5.dto.PaymentResponse;
import com.shortvideo.backend.h5.dto.PaymentSettlementResponse;
import com.shortvideo.backend.h5.dto.RechargeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class H5PaymentFlowIntegrationTests {

    @Autowired
    private H5UserService userService;

    @Autowired
    private AdminUserManagementService adminUserManagementService;

    @Autowired
    private AdminOrderManagementService adminOrderManagementService;

    @Autowired
    private AdminFinanceService adminFinanceService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void localPaymentSimulationGrantsOneStorylineAndOrder() {
        H5AuthResponse auth = userService.guestLogin(new GuestLoginRequest(
                "test-payment-" + UUID.randomUUID(),
                "test",
                "integration"
        ));

        PaymentResponse payment = userService.createPayment(new PaymentRequest(
                auth.user().deviceId(),
                1L,
                null,
                null,
                new BigDecimal("6.00"),
                "sandbox-card",
                "本地沙箱卡"
        ), bearer(auth));

        assertThat(payment.status()).isEqualTo("PENDING");
        PaymentSettlementResponse pending = userService.getPaymentSettlement(payment.paymentId(), bearer(auth));
        assertThat(pending.payment().status()).isEqualTo("PENDING");
        assertThat(pending.line()).isNull();

        PaymentSettlementResponse settlement = userService.simulatePaymentSuccess(payment.paymentId(), bearer(auth));

        assertThat(settlement.payment().status()).isEqualTo("PAID");
        assertThat(settlement.line()).isNotNull();
        assertThat(settlement.order()).isNotNull();
        assertThat(settlement.order().id()).isEqualTo(payment.paymentId());

        Integer orderCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ? AND user_id = ? AND status = 'PAID'",
                Integer.class,
                payment.paymentId(),
                auth.user().id()
        );
        Integer unlockCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_unlocks WHERE user_id = ? AND storyline_id = ? AND order_id = ?",
                Integer.class,
                auth.user().id(),
                settlement.line().id(),
                payment.paymentId()
        );
        Integer eventCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM h5_payment_events WHERE payment_id = ?",
                Integer.class,
                payment.paymentId()
        );

        assertThat(orderCount).isEqualTo(1);
        assertThat(unlockCount).isEqualTo(1);
        assertThat(eventCount).isGreaterThanOrEqualTo(3);

        PaymentSettlementResponse duplicate = userService.simulatePaymentSuccess(payment.paymentId(), bearer(auth));
        assertThat(duplicate.payment().status()).isEqualTo("PAID");
        assertThat(duplicate.line().id()).isEqualTo(settlement.line().id());

        PaymentSettlementResponse queried = userService.getPaymentSettlement(payment.paymentId(), bearer(auth));
        assertThat(queried.payment().status()).isEqualTo("PAID");
        assertThat(queried.order().id()).isEqualTo(payment.paymentId());

        AdminUserDetailResponse userDetail = adminUserManagementService.getUserDetail("U" + auth.user().id());
        assertThat(userDetail.payments())
                .extracting(AdminUserDetailResponse.PaymentItem::id)
                .contains(payment.paymentId());
        assertThat(userDetail.paymentEvents())
                .extracting(AdminUserDetailResponse.PaymentEventItem::eventType)
                .contains("CREATED", "SIMULATE_SUCCESS", "ENTITLEMENT_GRANTED");

        var refundRequest = userService.requestRefund(new H5RefundRequest(
                auth.user().deviceId(),
                payment.paymentId(),
                "integration refund"), bearer(auth));
        assertThat(refundRequest.status()).isEqualTo("PENDING_REVIEW");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ? AND status = 'PAID'",
                Integer.class,
                payment.paymentId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_unlocks WHERE order_id = ?",
                Integer.class,
                payment.paymentId())).isEqualTo(1);
        assertThatThrownBy(() -> adminOrderManagementService.refundOrder(
                payment.paymentId(),
                "bypass",
                "test-admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("requires an H5 refund request");

        assertThat(adminOrderManagementService.listRefundRequests("待审批"))
                .extracting(item -> item.id())
                .contains(refundRequest.id());

        var approved = adminOrderManagementService.approveRefundRequest(
                refundRequest.id(),
                "integration approval",
                "test-admin");
        assertThat(approved.status()).isEqualTo("已退款");
        var refunded = adminOrderManagementService.getOrder(payment.paymentId());
        assertThat(refunded.status()).isEqualTo("已退款");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE id = ? AND status = 'REFUNDED'",
                Integer.class,
                payment.paymentId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_unlocks WHERE order_id = ?",
                Integer.class,
                payment.paymentId())).isEqualTo(0);
        assertThatThrownBy(() -> adminOrderManagementService.resendEntitlement(payment.paymentId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only paid orders can resend entitlement");
        assertThatThrownBy(() -> adminOrderManagementService.updateStatus(
                payment.paymentId(), "已支付", "test-admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Refunded orders are final");

        PaymentResponse lateCallback = userService.paymentCallback(new PaymentCallbackRequest(
                payment.paymentId(),
                "late-provider-callback",
                "PAID"));
        assertThat(lateCallback.status()).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_unlocks WHERE order_id = ?",
                Integer.class,
                payment.paymentId())).isEqualTo(0);
        assertThat(adminOrderManagementService.listEvents(payment.paymentId()))
                .extracting(event -> event.eventType())
                .contains("REFUND_REQUESTED", "REFUND_APPROVED", "REFUNDED");

        assertThat(userService.requestRefund(new H5RefundRequest(
                auth.user().deviceId(),
                payment.paymentId(),
                "duplicate refund"), bearer(auth)).status()).isEqualTo("COMPLETED");

        var reconciliation = adminFinanceService.reconciliation(payment.paymentId(), "全部");
        assertThat(reconciliation.rows())
                .anySatisfy(row -> {
                    assertThat(row.paymentId()).isEqualTo(payment.paymentId());
                    assertThat(row.reconciliationStatus()).isEqualTo("已退款");
                    assertThat(row.eventCount()).isGreaterThanOrEqualTo(4);
                });
    }

    @Test
    void walletPaymentDebitsAndRefundRestoresBalance() {
        H5AuthResponse auth = userService.guestLogin(new GuestLoginRequest(
                "test-wallet-" + UUID.randomUUID(),
                "test",
                "wallet"
        ));
        BigDecimal initialBalance = jdbc.queryForObject(
                "SELECT balance FROM app_users WHERE id = ?",
                BigDecimal.class,
                auth.user().id());
        userService.recharge(new RechargeRequest(
                auth.user().deviceId(),
                new BigDecimal("10.00"),
                "balance",
                "余额"
        ), bearer(auth));

        PaymentResponse payment = userService.createPayment(new PaymentRequest(
                auth.user().deviceId(),
                1L,
                null,
                null,
                new BigDecimal("6.00"),
                "balance",
                "余额"
        ), bearer(auth));
        userService.simulatePaymentSuccess(payment.paymentId(), bearer(auth));

        BigDecimal afterPayment = jdbc.queryForObject(
                "SELECT balance FROM app_users WHERE id = ?",
                BigDecimal.class,
                auth.user().id());
        assertThat(afterPayment).isEqualByComparingTo(initialBalance.add(new BigDecimal("4.00")));

        var refundRequest = userService.requestRefund(new H5RefundRequest(
                auth.user().deviceId(),
                payment.paymentId(),
                "wallet refund"), bearer(auth));
        adminOrderManagementService.approveRefundRequest(refundRequest.id(), "approved", "test-admin");

        BigDecimal afterRefund = jdbc.queryForObject(
                "SELECT balance FROM app_users WHERE id = ?",
                BigDecimal.class,
                auth.user().id());
        assertThat(afterRefund).isEqualByComparingTo(initialBalance.add(new BigDecimal("10.00")));
    }

    private String bearer(H5AuthResponse auth) {
        return "Bearer " + auth.token();
    }
}
