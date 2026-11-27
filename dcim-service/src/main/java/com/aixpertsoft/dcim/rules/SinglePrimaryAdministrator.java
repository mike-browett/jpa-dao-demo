package com.aixpertsoft.dcim.rules;

import static com.aixpertsoft.datastack.rules.Event.BEFORE_INSERT;
import static com.aixpertsoft.datastack.rules.Event.BEFORE_UPDATE;

import com.aixpertsoft.datastack.rules.BeforeSave;
import com.aixpertsoft.datastack.rules.Rule;
import com.aixpertsoft.dcim.dao.RackAdministratorDao;
import com.aixpertsoft.dcim.entity.RackAdministrator;
import java.util.Objects;

/**
 * Custom business rule: making someone primary administrator demotes the previous primary instead of failing.
 * The database constraint generated from "uniqueWhere" is checked at commit, so the order of writes does not matter.
 */
@Rule(on = RackAdministrator.class, events = {BEFORE_INSERT, BEFORE_UPDATE}, order = 100)
public class SinglePrimaryAdministrator {

    private final RackAdministratorDao links;

    public SinglePrimaryAdministrator(RackAdministratorDao links) {
        this.links = links;
    }

    public void apply(BeforeSave<RackAdministrator> event) {
        RackAdministrator link = event.entity();
        if (!link.isPrimaryAdministrator()) {
            return;
        }
        links.findByRackAndPrimary(link.getRack().getId(), true).stream()
             .filter(other -> !Objects.equals(other.getId(), link.getId()))
             .forEach(other -> other.setPrimaryAdministrator(false));
    }
}
