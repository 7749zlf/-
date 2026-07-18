package com.shortvideo.backend.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public record AdminUserDetailResponse(
        AdminUserResponse user,
        Wallet wallet,
        List<RechargeItem> recharges,
        List<OrderItem> orders,
        List<UnlockItem> unlocks,
        List<WatchItem> watchHistory,
        List<DramaItem> follows,
        List<LikeItem> likes,
        List<PaymentItem> payments,
        List<PaymentEventItem> paymentEvents
) {
    public record Wallet(
            BigDecimal balance,
            String balanceText,
            BigDecimal paidAmount,
            String paidText,
            Integer rechargeCount,
            Integer orderCount,
            Integer unlockCount,
            Integer watchCount,
            Integer followCount,
            Integer likeCount
    ) {
    }

    public record RechargeItem(
            String id,
            BigDecimal amount,
            String amountText,
            String methodKey,
            String methodName,
            String status,
            String createdAt
    ) {
    }

    public record OrderItem(
            String id,
            String title,
            BigDecimal amount,
            String amountText,
            String paymentMethod,
            String status,
            String paidAt,
            String createdAt
    ) {
    }

    public record UnlockItem(
            String storylineId,
            String storylineName,
            String dramaTitle,
            String orderId,
            String createdAt
    ) {
    }

    public record WatchItem(
            String id,
            String dramaTitle,
            String episodeTitle,
            String storylineName,
            Integer episodeNumber,
            Integer progressSeconds,
            Integer durationSeconds,
            String updatedAt
    ) {
    }

    public record DramaItem(
            Long id,
            String title,
            String tag,
            String createdAt
    ) {
    }

    public record LikeItem(
            String episodeId,
            String dramaTitle,
            String episodeTitle,
            String storylineName,
            String createdAt
    ) {
    }

    public record PaymentItem(
            String id,
            BigDecimal amount,
            String amountText,
            String methodName,
            String status,
            String providerTradeNo,
            String createdAt,
            String paidAt
    ) {
    }

    public record PaymentEventItem(
            Long id,
            String paymentId,
            String eventType,
            String status,
            String providerTradeNo,
            String message,
            String createdAt
    ) {
    }
}
