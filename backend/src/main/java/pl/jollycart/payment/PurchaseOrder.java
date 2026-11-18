package pl.jollycart.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Wiersz tabeli orders (odpowiednik encji PurchaseOrder ze Spring Boot).
 *
 * Zwykla klasa z getterami i setterami, a nie rekord: serwis zamowien zmienia w niej
 * kolejne pola w trakcie cyklu zycia zamowienia, a OrderDao.update() zapisuje calosc.
 */
public class PurchaseOrder {
    private Long id;
    private Long offerId;
    private String offerTitle;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal amount;
    private BigDecimal shippingCost;
    private BigDecimal serviceFee;
    private BigDecimal paymentFee;
    private BigDecimal totalAmount;
    private String currency;
    private OrderStatus status;
    private String stripeSessionId;
    private String stripeCheckoutUrl;
    private String stripePaymentIntentId;
    private String stripeChargeId;
    private String stripeTransferId;
    private LocalDateTime checkoutExpiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime shipDeadline;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime confirmDeadline;
    private LocalDateTime completedAt;
    private String disputeReason;
    private String shippingCity;
    private String shippingStreet;
    private String shippingPostalCode;
    private String shippingPhone;
    private boolean buyerUnread;
    private boolean sellerUnread;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOfferId() {
        return offerId;
    }

    public void setOfferId(Long offerId) {
        this.offerId = offerId;
    }

    public String getOfferTitle() {
        return offerTitle;
    }

    public void setOfferTitle(String offerTitle) {
        this.offerTitle = offerTitle;
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(Long buyerId) {
        this.buyerId = buyerId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getShippingCost() {
        return shippingCost;
    }

    public void setShippingCost(BigDecimal shippingCost) {
        this.shippingCost = shippingCost;
    }

    public BigDecimal getServiceFee() {
        return serviceFee;
    }

    public void setServiceFee(BigDecimal serviceFee) {
        this.serviceFee = serviceFee;
    }

    public BigDecimal getPaymentFee() {
        return paymentFee;
    }

    public void setPaymentFee(BigDecimal paymentFee) {
        this.paymentFee = paymentFee;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public String getStripeCheckoutUrl() {
        return stripeCheckoutUrl;
    }

    public void setStripeCheckoutUrl(String stripeCheckoutUrl) {
        this.stripeCheckoutUrl = stripeCheckoutUrl;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public void setStripePaymentIntentId(String stripePaymentIntentId) {
        this.stripePaymentIntentId = stripePaymentIntentId;
    }

    public String getStripeChargeId() {
        return stripeChargeId;
    }

    public void setStripeChargeId(String stripeChargeId) {
        this.stripeChargeId = stripeChargeId;
    }

    public String getStripeTransferId() {
        return stripeTransferId;
    }

    public void setStripeTransferId(String stripeTransferId) {
        this.stripeTransferId = stripeTransferId;
    }

    public LocalDateTime getCheckoutExpiresAt() {
        return checkoutExpiresAt;
    }

    public void setCheckoutExpiresAt(LocalDateTime checkoutExpiresAt) {
        this.checkoutExpiresAt = checkoutExpiresAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getShipDeadline() {
        return shipDeadline;
    }

    public void setShipDeadline(LocalDateTime shipDeadline) {
        this.shipDeadline = shipDeadline;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public LocalDateTime getConfirmDeadline() {
        return confirmDeadline;
    }

    public void setConfirmDeadline(LocalDateTime confirmDeadline) {
        this.confirmDeadline = confirmDeadline;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getDisputeReason() {
        return disputeReason;
    }

    public void setDisputeReason(String disputeReason) {
        this.disputeReason = disputeReason;
    }

    public String getShippingCity() {
        return shippingCity;
    }

    public void setShippingCity(String shippingCity) {
        this.shippingCity = shippingCity;
    }

    public String getShippingStreet() {
        return shippingStreet;
    }

    public void setShippingStreet(String shippingStreet) {
        this.shippingStreet = shippingStreet;
    }

    public String getShippingPostalCode() {
        return shippingPostalCode;
    }

    public void setShippingPostalCode(String shippingPostalCode) {
        this.shippingPostalCode = shippingPostalCode;
    }

    public String getShippingPhone() {
        return shippingPhone;
    }

    public void setShippingPhone(String shippingPhone) {
        this.shippingPhone = shippingPhone;
    }

    public boolean isBuyerUnread() {
        return buyerUnread;
    }

    public void setBuyerUnread(boolean buyerUnread) {
        this.buyerUnread = buyerUnread;
    }

    public boolean isSellerUnread() {
        return sellerUnread;
    }

    public void setSellerUnread(boolean sellerUnread) {
        this.sellerUnread = sellerUnread;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Kwota dla sprzedawcy po zakonczeniu zamowienia: cena towaru + dostawa (bez naszych oplat). */
    public BigDecimal getSellerPayout() {
        return amount.add(shippingCost == null ? BigDecimal.ZERO : shippingCost);
    }
}
