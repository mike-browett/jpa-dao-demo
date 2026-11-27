package com.aixpertsoft.dcim.rules;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aixpertsoft.datastack.rules.BeforeSave;
import com.aixpertsoft.datastack.rules.Event;
import com.aixpertsoft.datastack.rules.RuleViolation;
import com.aixpertsoft.dcim.entity.Component;
import com.aixpertsoft.dcim.entity.Rack;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Plain unit test: no Spring, no database. */
class NoContainmentCycleTest {

    private final NoContainmentCycle rule = new NoContainmentCycle();

    @Test
    void componentInRackIsFine() {
        Rack rack = withId(new Rack("R1", null, 42));
        Component server = withId(new Component("srv", null));
        server.setRack(rack);

        assertThatCode(() -> rule.apply(new BeforeSave<>(Event.BEFORE_UPDATE, server))).doesNotThrowAnyException();
    }

    @Test
    void rackInsideItselfIsRejected() {
        Rack outer = withId(new Rack("outer", null, 42));
        Rack inner = withId(new Rack("inner", null, 12));
        inner.setRack(outer);
        outer.setRack(inner);   // outer -> inner -> outer

        assertThatThrownBy(() -> rule.apply(new BeforeSave<>(Event.BEFORE_UPDATE, outer)))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code())
                .isEqualTo("dcim.containmentCycle");
    }

    private static <T extends Component> T withId(T component) {
        ReflectionTestUtils.setField(component, "id", UUID.randomUUID());
        return component;
    }
}
