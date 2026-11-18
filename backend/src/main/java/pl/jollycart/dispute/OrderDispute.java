package pl.jollycart.dispute;

import java.time.LocalDateTime;

/**
 * Wiersz tabeli order_disputes: zgloszenie problemu z zamowieniem (jedno na zamowienie).
 * Zwykla klasa z setterami - rozstrzygniecie uzupelnia pola resolver*.
 */
public class OrderDispute {
    private Long id;
    private Long orderId;
    private Long reporterId;
    private String title;
    private String description;
    private DisputeStatus status;
    private DisputeResolution resolution;
    private Long resolverId;
    private String resolverNickname;
    private String resolverNote;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public void setReporterId(Long reporterId) {
        this.reporterId = reporterId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    public void setStatus(DisputeStatus status) {
        this.status = status;
    }

    public DisputeResolution getResolution() {
        return resolution;
    }

    public void setResolution(DisputeResolution resolution) {
        this.resolution = resolution;
    }

    public Long getResolverId() {
        return resolverId;
    }

    public void setResolverId(Long resolverId) {
        this.resolverId = resolverId;
    }

    public String getResolverNickname() {
        return resolverNickname;
    }

    public void setResolverNickname(String resolverNickname) {
        this.resolverNickname = resolverNickname;
    }

    public String getResolverNote() {
        return resolverNote;
    }

    public void setResolverNote(String resolverNote) {
        this.resolverNote = resolverNote;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
