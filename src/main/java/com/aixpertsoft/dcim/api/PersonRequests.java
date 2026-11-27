package com.aixpertsoft.dcim.api;

import com.aixpertsoft.dcim.entity.PersonContact;
import com.aixpertsoft.dcim.entity.PersonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PersonRequests {

    private PersonRequests() {
    }

    public record Create(@NotBlank @Size(max = 120) String name,
                         @NotNull PersonType personType,
                         PersonContact contact) {
    }

    /** Partial update: null fields stay unchanged. Send "version" to get optimistic locking. */
    public record Update(Long version,
                         @Size(max = 120) String name,
                         PersonType personType,
                         PersonContact contact) {
    }
}
