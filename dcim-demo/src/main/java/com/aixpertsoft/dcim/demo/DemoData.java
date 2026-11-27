package com.aixpertsoft.dcim.demo;

import com.aixpertsoft.dcim.api.ComponentDto;
import com.aixpertsoft.dcim.api.ComponentRequests;
import com.aixpertsoft.dcim.api.PersonDto;
import com.aixpertsoft.dcim.api.PersonRequests;
import com.aixpertsoft.dcim.dao.ComponentDao;
import com.aixpertsoft.dcim.entity.PersonContact;
import com.aixpertsoft.dcim.entity.PersonType;
import com.aixpertsoft.dcim.service.ComponentService;
import com.aixpertsoft.dcim.service.PersonService;
import com.aixpertsoft.dcim.service.RackAdministratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fills an empty database with a few racks, components and persons, through the normal services,
 * so the business rules run on the demo data too. Switch off with demo.seed=false.
 */
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true", matchIfMissing = true)
public class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final ComponentDao componentDao;
    private final ComponentService components;
    private final PersonService persons;
    private final RackAdministratorService administrators;
    private final TransactionTemplate tx;

    public DemoData(ComponentDao componentDao, ComponentService components, PersonService persons,
                    RackAdministratorService administrators, TransactionTemplate tx) {
        this.componentDao = componentDao;
        this.components = components;
        this.persons = persons;
        this.administrators = administrators;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long existing = tx.execute(status -> componentDao.count());
        if (existing != null && existing > 0) {
            return;
        }
        PersonDto claire = persons.create(new PersonRequests.Create("Claire Martin", PersonType.HUMAN,
                new PersonContact("claire.martin@example.com", "+33 4 00 00 00 01", null)));
        PersonDto jean = persons.create(new PersonRequests.Create("Jean Dupont", PersonType.HUMAN,
                new PersonContact("jean.dupont@example.com", null, null)));
        PersonDto agent = persons.create(new PersonRequests.Create("Ops Agent", PersonType.AI,
                new PersonContact(null, null, "https://agents.example.com/ops")));

        ComponentDto lyo1 = components.createRack(new ComponentRequests.CreateRack("LYO-R01", "RK-0117", 42, null));
        ComponentDto lyo2 = components.createRack(new ComponentRequests.CreateRack("LYO-R02", "RK-0118", 42, null));
        ComponentDto ste1 = components.createRack(new ComponentRequests.CreateRack("STE-R01", "RK-0201", 24, null));

        components.createComponent(new ComponentRequests.CreateComponent("srv-db-01", "SN-88123", lyo1.id()));
        components.createComponent(new ComponentRequests.CreateComponent("srv-app-01", "SN-88124", lyo1.id()));
        components.createComponent(new ComponentRequests.CreateComponent("sw-core-01", "SN-90001", lyo2.id()));
        components.createComponent(new ComponentRequests.CreateComponent("patch-panel-01", "PP-0001", ste1.id()));
        components.createComponent(new ComponentRequests.CreateComponent("srv-spare-01", "SN-88999", null));

        administrators.link(lyo1.id(), claire.id(), true);
        administrators.link(lyo1.id(), agent.id(), false);
        administrators.link(lyo2.id(), jean.id(), true);
        administrators.link(ste1.id(), agent.id(), true);

        log.info("Demo data created: 3 racks, 5 components, 3 persons");
    }
}
