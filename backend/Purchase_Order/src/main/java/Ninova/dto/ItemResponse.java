package Ninova.dto;

import java.math.BigDecimal;
public record ItemResponse(Long id, String name, Integer quantity,
                           BigDecimal unitPrice, BigDecimal lineTotal) {}