package com.aixpertsoft.dcim.rules;

import static com.aixpertsoft.datastack.rules.Event.BEFORE_INSERT;
import static com.aixpertsoft.datastack.rules.Event.BEFORE_UPDATE;

import com.aixpertsoft.datastack.rules.BeforeSave;
import com.aixpertsoft.datastack.rules.Rule;
import com.aixpertsoft.dcim.entity.Component;
import com.aixpertsoft.dcim.entity.Rack;
import java.util.Objects;

/** Custom business rule: a Rack is a Component, so it must never end up inside itself. */
@Rule(on = Component.class, events = {BEFORE_INSERT, BEFORE_UPDATE}, order = 100)
public class NoContainmentCycle {

    public void apply(BeforeSave<Component> event) {
        Component component = event.entity();
        if (component.getId() == null) {
            return;   // a new component cannot contain anything yet
        }
        int depth = 0;
        for (Rack r = component.getRack(); r != null && depth < 100; r = r.getRack(), depth++) {
            if (Objects.equals(r.getId(), component.getId())) {
                event.reject("dcim.containmentCycle", "A rack cannot be placed inside itself");
            }
        }
    }
}
