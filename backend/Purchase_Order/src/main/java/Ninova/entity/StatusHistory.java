package Ninova.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;

import Ninova.enums.RequestStatus;

import jakarta.persistence.*;

@Entity
@Table(name = "status_histories")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_request_id", nullable = false)
    private PurchaseRequest purchaseRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private RequestStatus toStatus;

    @Column(name = "comments", nullable = false, length = 1000)
    private String comments;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    @PrePersist
    void onCreate() {
        changedAt = LocalDateTime.now(ZoneId.of("Asia/Qatar"));
    }

    public Long getId() { return id; }
    public PurchaseRequest getPurchaseRequest() { return purchaseRequest; }
    public void setPurchaseRequest(PurchaseRequest purchaseRequest) { this.purchaseRequest = purchaseRequest; }
    public RequestStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(RequestStatus fromStatus) { this.fromStatus = fromStatus; }
    public RequestStatus getToStatus() { return toStatus; }
    public void setToStatus(RequestStatus toStatus) { this.toStatus = toStatus; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public LocalDateTime getChangedAt() { return changedAt; }
}