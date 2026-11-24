package Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import Ninova.entity.PurchaseRequest;
import Ninova.enums.Department;
import Ninova.enums.RequestStatus;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long> {

    // Every filter is optional: a null parameter means "do not filter on this"
    @Query("select r from PurchaseRequest r " +
           "where (:status is null or r.status = :status) " +
           "and (:department is null or r.department = :department) " +
           "and (:createdFrom is null or r.createdAt >= :createdFrom) " +
           "and (:createdTo is null or r.createdAt < :createdTo) " +
           "and (:neededFrom is null or r.neededByDate >= :neededFrom) " +
           "and (:neededTo is null or r.neededByDate <= :neededTo)")
    Page<PurchaseRequest> search(@Param("status") RequestStatus status,
                                 @Param("department") Department department,
                                 @Param("createdFrom") LocalDateTime createdFrom,
                                 @Param("createdTo") LocalDateTime createdTo,
                                 @Param("neededFrom") LocalDate neededFrom,
                                 @Param("neededTo") LocalDate neededTo,
                                 Pageable pageable);
}