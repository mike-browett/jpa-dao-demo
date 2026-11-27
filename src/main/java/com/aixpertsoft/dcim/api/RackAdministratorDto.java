package com.aixpertsoft.dcim.api;

import com.aixpertsoft.dcim.entity.RackAdministrator;
import java.util.UUID;

public record RackAdministratorDto(UUID rackId, boolean primaryAdministrator, PersonDto person) {

    public static RackAdministratorDto of(RackAdministrator ra) {
        return new RackAdministratorDto(ra.getRack().getId(), ra.isPrimaryAdministrator(), PersonDto.of(ra.getPerson()));
    }
}
