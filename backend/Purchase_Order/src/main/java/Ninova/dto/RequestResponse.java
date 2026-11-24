package Ninova.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import Ninova.enums.Department;
import Ninova.enums.RequestStatus;

// history is only filled in the detail view, so NON_NULL hides it in lists
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RequestResponse(
        Long id, String requesterName, Department department, LocalDate neededByDate,
        String supplierName, String justification, RequestStatus status,
        BigDecimal grandTotal, Long version, LocalDateTime createdAt, LocalDateTime updatedAt,
        List<ItemResponse> items,
        List<HistoryResponse> history) {

    public record HistoryResponse(Long id, RequestStatus fromStatus, RequestStatus toStatus,
                                  String comments, LocalDateTime changedAt) {}
}