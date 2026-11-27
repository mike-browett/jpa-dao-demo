package com.aixpertsoft.dcim.view;

import com.aixpertsoft.dcim.entity.PersonContact;
import com.aixpertsoft.dcim.entity.PersonType;
import java.util.UUID;

/**
 * Read-only records handed to GraphQL. Views never expose JPA entities, so there is no lazy loading
 * outside a transaction and no accidental N+1.
 */
public final class ViewNodes {

    private ViewNodes() {
    }

    public record RackNode(UUID id, String name, String serialNumber, Integer heightUnits) {
    }

    public record ComponentNode(UUID id, String type, String name, String serialNumber) {
    }

    public record PersonNode(UUID id, String name, PersonType personType, PersonContact contact) {
    }

    public record AdministratorEdge(boolean primaryAdministrator, PersonNode person) {
    }
}
