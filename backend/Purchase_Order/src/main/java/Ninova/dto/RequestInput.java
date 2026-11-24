package Ninova.dto;

import java.time.LocalDate;
import java.util.List;

import Ninova.enums.Department;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

// Used for both create and update. No totals here, the backend calculates them.
public record RequestInput(
        @NotBlank(message = "Requester name is required")
        @Size(min = 3, max = 80, message = "Requester name must be 3 to 80 characters")
        String requesterName,

        @NotNull(message = "Department is required (IT, Operations, Finance or HR)")
        Department department,

        @NotNull(message = "Needed-by date is required")
        LocalDate neededByDate,

        @NotBlank(message = "Supplier name is required")
        @Size(max = 150, message = "Supplier name must be at most 150 characters")
        String supplierName,

        @NotBlank(message = "Justification is required")
        @Size(min = 10, max = 1000, message = "Justification must be 10 to 1000 characters")
        String justification,

        @NotNull(message = "Items are required")
        @Size(min = 1, max = 20, message = "A request must have 1 to 20 items")
        List<@NotNull(message = "Item cannot be null") @Valid ItemInput> items,

        Long version) { // only used for update (lost-update protection)

    public RequestInput {
        if (requesterName != null) requesterName = requesterName.trim();
        if (supplierName != null) supplierName = supplierName.trim();
        if (justification != null) justification = justification.trim();
    }
}