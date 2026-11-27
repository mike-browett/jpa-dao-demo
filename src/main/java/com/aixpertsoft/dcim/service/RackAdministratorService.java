package com.aixpertsoft.dcim.service;

import com.aixpertsoft.datastack.web.ApiException;
import com.aixpertsoft.dcim.api.RackAdministratorDto;
import com.aixpertsoft.dcim.dao.PersonDao;
import com.aixpertsoft.dcim.dao.RackAdministratorDao;
import com.aixpertsoft.dcim.dao.RackDao;
import com.aixpertsoft.dcim.entity.RackAdministrator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for the M-to-N relation Rack <-> Person (administrators). */
@Service
@Transactional
public class RackAdministratorService {

    private final RackDao racks;
    private final PersonDao persons;
    private final RackAdministratorDao links;

    public RackAdministratorService(RackDao racks, PersonDao persons, RackAdministratorDao links) {
        this.racks = racks;
        this.persons = persons;
        this.links = links;
    }

    @Transactional(readOnly = true)
    public List<RackAdministratorDto> list(UUID rackId) {
        racks.get(rackId);   // 404 if the rack does not exist
        return links.findByRack(rackId).stream().map(RackAdministratorDto::of).toList();
    }

    public RackAdministratorDto link(UUID rackId, UUID personId, boolean primaryAdministrator) {
        if (links.find(rackId, personId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "dcim.duplicateAdministrator",
                    "This person already administers this rack");
        }
        RackAdministrator link = new RackAdministrator(racks.get(rackId), persons.get(personId), primaryAdministrator);
        return RackAdministratorDto.of(links.create(link));   // runs SinglePrimaryAdministrator
    }

    public RackAdministratorDto setPrimary(UUID rackId, UUID personId, boolean primaryAdministrator) {
        RackAdministrator link = find(rackId, personId);
        link.setPrimaryAdministrator(primaryAdministrator);
        links.update(link);                                    // runs SinglePrimaryAdministrator
        return RackAdministratorDto.of(link);
    }

    public void unlink(UUID rackId, UUID personId) {
        links.delete(find(rackId, personId));
    }

    private RackAdministrator find(UUID rackId, UUID personId) {
        return links.find(rackId, personId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "dcim.notAnAdministrator", "Person " + personId + " does not administer rack " + rackId));
    }
}
