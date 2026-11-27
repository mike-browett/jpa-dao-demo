package com.aixpertsoft.dcim.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.aixpertsoft.datastack.rules.BeforeSave;
import com.aixpertsoft.datastack.rules.Event;
import com.aixpertsoft.dcim.dao.RackAdministratorDao;
import com.aixpertsoft.dcim.entity.Person;
import com.aixpertsoft.dcim.entity.PersonType;
import com.aixpertsoft.dcim.entity.Rack;
import com.aixpertsoft.dcim.entity.RackAdministrator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Plain unit test with an in-memory DAO: runs in milliseconds. */
class SinglePrimaryAdministratorTest {

    private final Rack rack = withId(new Rack("LYO-R01", null, 42));
    private final List<RackAdministrator> stored = new ArrayList<>();

    private final RackAdministratorDao inMemory = new RackAdministratorDao() {
        @Override
        public List<RackAdministrator> findByRackAndPrimary(UUID rackId, boolean primary) {
            return stored.stream()
                    .filter(ra -> ra.getRack().getId().equals(rackId) && ra.isPrimaryAdministrator() == primary)
                    .toList();
        }
    };

    private final SinglePrimaryAdministrator rule = new SinglePrimaryAdministrator(inMemory);

    @Test
    void newPrimaryDemotesTheOldOne() {
        RackAdministrator claire = store(new RackAdministrator(rack, person("Claire"), true));
        RackAdministrator agent = new RackAdministrator(rack, person("Ops Agent"), true);

        rule.apply(new BeforeSave<>(Event.BEFORE_INSERT, agent));

        assertThat(claire.isPrimaryAdministrator()).isFalse();
        assertThat(agent.isPrimaryAdministrator()).isTrue();
    }

    @Test
    void nonPrimaryLinkChangesNothing() {
        RackAdministrator claire = store(new RackAdministrator(rack, person("Claire"), true));

        rule.apply(new BeforeSave<>(Event.BEFORE_INSERT, new RackAdministrator(rack, person("Jean"), false)));

        assertThat(claire.isPrimaryAdministrator()).isTrue();
    }

    private RackAdministrator store(RackAdministrator link) {
        stored.add(withId(link));
        return link;
    }

    private static Person person(String name) {
        return withId(new Person(name, PersonType.HUMAN, null));
    }

    private static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }
}
