package com.aixpertsoft.dcim.rest;

import com.aixpertsoft.dcim.api.AdministratorRequests;
import com.aixpertsoft.dcim.api.ComponentDto;
import com.aixpertsoft.dcim.api.ComponentRequests;
import com.aixpertsoft.dcim.api.Page;
import com.aixpertsoft.dcim.api.RackAdministratorDto;
import com.aixpertsoft.dcim.service.ComponentService;
import com.aixpertsoft.dcim.service.RackAdministratorService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Racks, their children (1-to-N) and their administrators (M-to-N). */
@RestController
@RequestMapping("/api/racks")
public class RackController {

    private final ComponentService components;
    private final RackAdministratorService administrators;

    public RackController(ComponentService components, RackAdministratorService administrators) {
        this.components = components;
        this.administrators = administrators;
    }

    // ---- racks ----

    @GetMapping
    public Page<ComponentDto> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) String name) {
        return components.listRacks(page, Paging.size(size), name);
    }

    @GetMapping("/{id}")
    public ComponentDto get(@PathVariable UUID id) {
        return components.getRack(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComponentDto create(@Valid @RequestBody ComponentRequests.CreateRack request) {
        return components.createRack(request);
    }

    @PatchMapping("/{id}")
    public ComponentDto update(@PathVariable UUID id, @Valid @RequestBody ComponentRequests.Update request) {
        components.getRack(id);   // 404 unless it is a rack
        return components.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        components.getRack(id);
        components.delete(id);
    }

    // ---- children ----

    @PutMapping("/{rackId}/children/{componentId}")
    public ComponentDto addChild(@PathVariable UUID rackId, @PathVariable UUID componentId) {
        return components.addChild(rackId, componentId);
    }

    @DeleteMapping("/{rackId}/children/{componentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeChild(@PathVariable UUID rackId, @PathVariable UUID componentId) {
        components.removeChild(rackId, componentId);
    }

    // ---- administrators ----

    @GetMapping("/{rackId}/administrators")
    public List<RackAdministratorDto> administrators(@PathVariable UUID rackId) {
        return administrators.list(rackId);
    }

    @PostMapping("/{rackId}/administrators")
    @ResponseStatus(HttpStatus.CREATED)
    public RackAdministratorDto link(@PathVariable UUID rackId,
                                     @Valid @RequestBody AdministratorRequests.Link request) {
        return administrators.link(rackId, request.personId(), request.primaryAdministrator());
    }

    @PatchMapping("/{rackId}/administrators/{personId}")
    public RackAdministratorDto setPrimary(@PathVariable UUID rackId, @PathVariable UUID personId,
                                           @RequestBody AdministratorRequests.Update request) {
        return administrators.setPrimary(rackId, personId, request.primaryAdministrator());
    }

    @DeleteMapping("/{rackId}/administrators/{personId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable UUID rackId, @PathVariable UUID personId) {
        administrators.unlink(rackId, personId);
    }
}
