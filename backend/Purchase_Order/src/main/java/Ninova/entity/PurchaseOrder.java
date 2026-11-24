package Ninova.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    public static final String DRAFT = "Draft PO";
    public static final String ISSUED = "Issued";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_request_id", nullable = false, unique = true)
    private Long purchaseRequestId;

    @Column(name = "po_number", nullable = false, unique = true, length = 50)
    private String poNumber;

    @Column(name = "po_date", nullable = false)
    private LocalDate poDate;

    @Column(name = "supplier_name", nullable = false, length = 150)
    private String supplierName;

    @Column(name = "requester_name", nullable = false, length = 80)
    private String requesterName;

    @Column(name = "department", nullable = false, length = 50)
    private String department;

    @Column(name = "needed_by_date", nullable = false)
    private LocalDate neededByDate;

    @Column(name = "grand_total", nullable = false, precision = 16, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "status", nullable = false, length = 30)
    private String status = DRAFT;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Qatar"));
    }

    public Long getId() { return id; }
    public Long getPurchaseRequestId() { return purchaseRequestId; }
    public void setPurchaseRequestId(Long purchaseRequestId) { this.purchaseRequestId = purchaseRequestId; }
    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }
    public LocalDate getPoDate() { return poDate; }
    public void setPoDate(LocalDate poDate) { this.poDate = poDate; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public LocalDate getNeededByDate() { return neededByDate; }
    public void setNeededByDate(LocalDate neededByDate) { this.neededByDate = neededByDate; }
    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<PurchaseOrderItem> getItems() { return items; }
}