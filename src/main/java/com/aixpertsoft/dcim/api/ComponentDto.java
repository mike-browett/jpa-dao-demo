package com.aixpertsoft.dcim.api;

import com.aixpertsoft.dcim.entity.Component;
import com.aixpertsoft.dcim.entity.Rack;
import java.util.UUID;

/** A component or a rack; heightUnits is set for racks only. */
public record ComponentDto(UUID id, long version, String type, String name, String serialNumber,
                           Integer heightUnits, UUID rackId) {

    public static ComponentDto of(Component c) {
        Integer height = c instanceof Rack rack ? rack.getHeightUnits() : null;
        UUID rackId = c.getRack() != null ? c.getRack().getId() : null;
        return new ComponentDto(c.getId(), c.getVersion(), c.getType(), c.getName(), c.getSerialNumber(),
                height, rackId);
    }
}
