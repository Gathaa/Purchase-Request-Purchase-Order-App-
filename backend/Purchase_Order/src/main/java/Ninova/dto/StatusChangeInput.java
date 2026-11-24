package Ninova.dto;

import Ninova.enums.RequestStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusChangeInput(
        @NotNull(message = "New status is required")
        RequestStatus newStatus,

        @NotBlank(message = "Comment is required")
        @Size(min = 5, max = 1000, message = "Comment must be 5 to 1000 characters")
        String comment,

        Long version) {

    public StatusChangeInput {
        if (comment != null) comment = comment.trim();
    }
}