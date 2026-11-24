package Ninova.entity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import Ninova.enums.Department;
import Ninova.enums.RequestStatus;

import jakarta.persistence.*;
@Entity
@Table(name= "purchase_requests")
public class PurchaseRequest {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	
	@Column(name ="requester_name",nullable=false,length=80)
	private String requesterName;
	
	@Enumerated(EnumType.STRING)
	@Column(name="department", nullable=false)
	private Department department;
	 @Column(name = "needed_by_date", nullable = false)
	    private LocalDate neededByDate;

	    @Column(name = "supplier_name", nullable = false, length = 150)
	    private String supplierName;

	    @Column(name = "justification", nullable = false, length = 1000)
	    private String justification;

	    @Enumerated(EnumType.STRING)
	    @Column(name = "status", nullable = false, length = 30)
	    private RequestStatus status = RequestStatus.Draft;

	    @Column(name = "grand_total", nullable = false, precision = 16, scale = 2)
	    private BigDecimal grandTotal = BigDecimal.ZERO;

	    @Version
	    @Column(name = "version", nullable = false)
	    private Long version;

	    @Column(name = "created_at")
	    private LocalDateTime createdAt;

	    @Column(name = "updated_at")
	    private LocalDateTime updatedAt;

	    @OneToMany(mappedBy = "purchaseRequest", cascade = CascadeType.ALL, orphanRemoval = true)
	    @OrderBy("id ASC")
	    private List<PurchaseRequestItem> items = new ArrayList<>();

	    @OneToMany(mappedBy = "purchaseRequest", cascade = CascadeType.ALL)
	    @OrderBy("id ASC")
	    private List<StatusHistory> history = new ArrayList<>();

	    @PrePersist
	    void onCreate() {
	        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Qatar"));
	        createdAt = now;
	        updatedAt = now;
	    }

	    public void addHistory(RequestStatus from, RequestStatus to, String comment) {
	        StatusHistory h = new StatusHistory();
	        h.setPurchaseRequest(this);
	        h.setFromStatus(from);
	        h.setToStatus(to);
	        h.setComments(comment);
	        history.add(h);
	    }

	    public Long getId() { return id; }
	    public String getRequesterName() { return requesterName; }
	    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }
	    public Department getDepartment() { return department; }
	    public void setDepartment(Department department) { this.department = department; }
	    public LocalDate getNeededByDate() { return neededByDate; }
	    public void setNeededByDate(LocalDate neededByDate) { this.neededByDate = neededByDate; }
	    public String getSupplierName() { return supplierName; }
	    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
	    public String getJustification() { return justification; }
	    public void setJustification(String justification) { this.justification = justification; }
	    public RequestStatus getStatus() { return status; }
	    public void setStatus(RequestStatus status) { this.status = status; }
	    public BigDecimal getGrandTotal() { return grandTotal; }
	    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }
	    public Long getVersion() { return version; }
	    public LocalDateTime getCreatedAt() { return createdAt; }
	    public LocalDateTime getUpdatedAt() { return updatedAt; }
	    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
	    public List<PurchaseRequestItem> getItems() { return items; }
	    public List<StatusHistory> getHistory() { return history; }
	
}
