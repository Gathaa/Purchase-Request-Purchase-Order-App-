package Ninova.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PoResponse(
        Long id, Long purchaseRequestId, String poNumber, LocalDate poDate,
        String supplierName, String requesterName, String department, LocalDate neededByDate,
        BigDecimal grandTotal, String status, LocalDateTime issuedAt, LocalDateTime createdAt,
        List<ItemResponse> items) {}