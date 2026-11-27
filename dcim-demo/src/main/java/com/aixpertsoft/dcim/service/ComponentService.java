package com.aixpertsoft.dcim.service;

import com.aixpertsoft.datastack.web.ApiException;
import com.aixpertsoft.dcim.api.ComponentDto;
import com.aixpertsoft.dcim.api.ComponentRequests;
import com.aixpertsoft.dcim.api.Page;
import com.aixpertsoft.dcim.dao.ComponentDao;
import com.aixpertsoft.dcim.dao.RackDao;
import com.aixpertsoft.dcim.entity.Component;
import com.aixpertsoft.dcim.entity.Rack;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for components and racks, including putting components into racks and taking them out. */
@Service
@Transactional
public class ComponentService {

    private final ComponentDao components;
    private final RackDao racks;

    public ComponentService(ComponentDao components, RackDao racks) {
        this.components = components;
        this.racks = racks;
    }

    // ---- components (racks included) ----

    @Transactional(readOnly = true)
    public Page<ComponentDto> list(int page, int size) {
        return new Page<>(page, size, components.count(),
                components.list(page, size).stream().map(ComponentDto::of).toList());
    }

    @Transactional(readOnly = true)
    public ComponentDto get(UUID id) {
        return ComponentDto.of(components.get(id));
    }

    public ComponentDto createComponent(ComponentRequests.CreateComponent request) {
        Component component = new Component(request.name(), request.serialNumber());
        if (request.rackId() != null) {
            component.setRack(racks.get(request.rackId()));
        }
        return ComponentDto.of(components.create(component));
    }

    public ComponentDto update(UUID id, ComponentRequests.Update request) {
        Component component = components.get(id);
        Versions.check(request.version(), component.getVersion());
        if (request.name() != null) component.setName(request.name());
        if (request.serialNumber() != null) component.setSerialNumber(request.serialNumber());
        if (request.heightUnits() != null) {
            if (!(component instanceof Rack rack)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "dcim.notARack",
                        "heightUnits can only be set on a rack");
            }
            rack.setHeightUnits(request.heightUnits());
        }
        components.update(component);
        components.flush();
        return ComponentDto.of(component);
    }

    /** Deleting a rack that still has children is refused; its administrator links go with it. */
    public void delete(UUID id) {
        Component component = components.get(id);
        if (component instanceof Rack && components.countChildren(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "dcim.rackNotEmpty",
                    "The rack still contains components; take them out first");
        }
        components.delete(component);
    }

    // ---- racks ----

    @Transactional(readOnly = true)
    public Page<ComponentDto> listRacks(int page, int size, String namePattern) {
        return new Page<>(page, size, racks.count(namePattern),
                racks.list(page, size, namePattern).stream().map(ComponentDto::of).toList());
    }

    @Transactional(readOnly = true)
    public ComponentDto getRack(UUID id) {
        return ComponentDto.of(racks.get(id));
    }

    public ComponentDto createRack(ComponentRequests.CreateRack request) {
        Rack rack = new Rack(request.name(), request.serialNumber(),
                request.heightUnits() != null ? request.heightUnits() : 42);
        if (request.rackId() != null) {
            rack.setRack(racks.get(request.rackId()));
        }
        return ComponentDto.of(components.create(rack));
    }

    // ---- RackChildren relation ----

    /** Puts a component into a rack, moving it out of any rack it was in before. */
    public ComponentDto addChild(UUID rackId, UUID componentId) {
        Rack rack = racks.get(rackId);
        Component component = components.get(componentId);
        component.setRack(rack);
        components.update(component);   // runs NoContainmentCycle
        components.flush();
        return ComponentDto.of(component);
    }

    /** Takes a component out of a rack. The component itself is kept. */
    public void removeChild(UUID rackId, UUID componentId) {
        Component component = components.get(componentId);
        if (component.getRack() == null || !Objects.equals(component.getRack().getId(), rackId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "dcim.notInRack",
                    "Component " + componentId + " is not in rack " + rackId);
        }
        component.setRack(null);
        components.update(component);
    }
}
