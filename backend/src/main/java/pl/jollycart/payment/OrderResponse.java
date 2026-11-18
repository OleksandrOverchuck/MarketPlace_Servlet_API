package pl.jollycart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Zamowienie widziane przez kupujacego lub sprzedawcow (dane adresowe tylko gdy wolno je pokazac). */
public record OrderResponse(
        Long id,
        Long offerId,
        String offerTitle,
        BigDecimal amount,
        BigDecimal shippingCost,
        BigDecimal serviceFee,
        BigDecimal paymentFee,
        BigDecimal totalAmount,
        BigDecimal sellerPayout,
        String currency,
        OrderStatus status,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime shipDeadline,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime confirmDeadline,
        LocalDateTime completedAt,
        String disputeReason,
        String checkoutUrl,
        String shippingCity,
        String shippingStreet,
        String shippingPostalCode,
        String shippingPhone
) {
}
