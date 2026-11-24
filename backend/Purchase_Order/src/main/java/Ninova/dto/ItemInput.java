package Ninova.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;

public record ItemInput(
        @NotBlank(message = "Item name is required")
        @Size(max = 200, message = "Item name must be at most 200 characters")
        String name,

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 999, message = "Quantity must be at most 999")
        Integer quantity,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "Unit price must be more than 0")
        @Digits(integer = 8, fraction = 2, message = "Unit price must have at most 8 digits and 2 decimals")
        BigDecimal unitPrice) {

    public ItemInput {
        if (name != null) name = name.trim();
    }
}