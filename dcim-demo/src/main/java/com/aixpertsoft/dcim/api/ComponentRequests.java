package com.aixpertsoft.dcim.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ComponentRequests {

    private ComponentRequests() {
    }

    /** rackId is optional: the component is created inside that rack. */
    public record CreateComponent(@NotBlank @Size(max = 120) String name,
                                  @Size(max = 60) String serialNumber,
                                  UUID rackId) {
    }

    /** Racks can sit inside other racks, so rackId is optional here too. */
    public record CreateRack(@NotBlank @Size(max = 120) String name,
                             @Size(max = 60) String serialNumber,
                             @Min(1) @Max(60) Integer heightUnits,
                             UUID rackId) {
    }

    /** Partial update: null fields stay unchanged. Move between racks with /racks/{id}/children. */
    public record Update(Long version,
                         @Size(max = 120) String name,
                         @Size(max = 60) String serialNumber,
                         @Min(1) @Max(60) Integer heightUnits) {
    }
}
