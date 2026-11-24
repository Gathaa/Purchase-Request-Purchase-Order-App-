package Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Ninova.entity.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByPurchaseRequestId(Long purchaseRequestId);

    boolean existsByPurchaseRequestId(Long purchaseRequestId);

    // Optional filters on status and on the PO date range
    @Query("select p from PurchaseOrder p " +
           "where (:status is null or p.status = :status) " +
           "and (:fromDate is null or p.poDate >= :fromDate) " +
           "and (:toDate is null or p.poDate <= :toDate)")
    Page<PurchaseOrder> search(@Param("status") String status,
                               @Param("fromDate") LocalDate fromDate,
                               @Param("toDate") LocalDate toDate,
                               Pageable pageable);

    // Atomic "issue only once": the WHERE status = Draft PO makes a second call update 0 rows
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PurchaseOrder p set p.status = :issued, p.issuedAt = :now " +
           "where p.id = :id and p.status = :draft")
    int markIssued(@Param("id") Long id, @Param("issued") String issued,
                   @Param("draft") String draft, @Param("now") LocalDateTime now);
}