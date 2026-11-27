package com.aixpertsoft.dcim.demo;

import com.aixpertsoft.dcim.api.ComponentRequests;
import com.aixpertsoft.dcim.api.PersonRequests;
import com.aixpertsoft.dcim.api.RackAdministratorRequests;
import com.aixpertsoft.dcim.api.RackRequests;
import com.aixpertsoft.dcim.entity.PersonContact;
import com.aixpertsoft.dcim.entity.PersonType;
import com.aixpertsoft.dcim.service.ComponentService;
import com.aixpertsoft.dcim.service.PersonService;
import com.aixpertsoft.dcim.service.RackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Custom code: fills an empty database through the generated services, so the business rules run too.
 * Switch off with demo.seed=false.
 */
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true", matchIfMissing = true)
public class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final ComponentService components;
    private final RackService racks;
    private final PersonService persons;

    public DemoData(ComponentService components, RackService racks, PersonService persons) {
        this.components = components;
        this.racks = racks;
        this.persons = persons;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (components.list(0, 1).total() > 0) {
            return;
        }
        var claire = persons.create(new PersonRequests.Create("Claire Martin", PersonType.HUMAN,
                new PersonContact("claire.martin@example.com", "+33 4 00 00 00 01", null)));
        var jean = persons.create(new PersonRequests.Create("Jean Dupont", PersonType.HUMAN,
                new PersonContact("jean.dupont@example.com", null, null)));
        var agent = persons.create(new PersonRequests.Create("Ops Agent", PersonType.AI,
                new PersonContact(null, null, "https://agents.example.com/ops")));

        var lyo1 = racks.create(new RackRequests.Create("LYO-R01", "RK-0117", 42, null));
        var lyo2 = racks.create(new RackRequests.Create("LYO-R02", "RK-0118", 42, null));
        var ste1 = racks.create(new RackRequests.Create("STE-R01", "RK-0201", 24, null));

        components.create(new ComponentRequests.Create("srv-db-01", "SN-88123", lyo1.id()));
        components.create(new ComponentRequests.Create("srv-app-01", "SN-88124", lyo1.id()));
        components.create(new ComponentRequests.Create("sw-core-01", "SN-90001", lyo2.id()));
        components.create(new ComponentRequests.Create("patch-panel-01", "PP-0001", ste1.id()));
        components.create(new ComponentRequests.Create("srv-spare-01", "SN-88999", null));

        racks.addAdministrator(lyo1.id(), new RackAdministratorRequests.Link(claire.id(), true));
        racks.addAdministrator(lyo1.id(), new RackAdministratorRequests.Link(agent.id(), false));
        racks.addAdministrator(lyo2.id(), new RackAdministratorRequests.Link(jean.id(), true));
        racks.addAdministrator(ste1.id(), new RackAdministratorRequests.Link(agent.id(), true));

        log.info("Demo data created: 3 racks, 5 components, 3 persons");
    }
}
