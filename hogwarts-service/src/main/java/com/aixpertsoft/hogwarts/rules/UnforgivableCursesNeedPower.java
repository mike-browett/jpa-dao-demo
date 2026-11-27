package com.aixpertsoft.hogwarts.rules;

import static com.aixpertsoft.datastack.rules.Event.BEFORE_INSERT;
import static com.aixpertsoft.datastack.rules.Event.BEFORE_UPDATE;

import com.aixpertsoft.datastack.rules.BeforeSave;
import com.aixpertsoft.datastack.rules.Rule;
import com.aixpertsoft.hogwarts.entity.WizardSpell;

/**
 * Custom business rule that reads the linked entity: an Unforgivable Curse can only be cast
 * at level 7 or above ("you need to really mean it"). Below that the change is refused with HTTP 422.
 * The model knows level is 1-9 and unforgivable is a flag; it does not know this combination.
 */
@Rule(on = WizardSpell.class, events = {BEFORE_INSERT, BEFORE_UPDATE}, order = 50)
public class UnforgivableCursesNeedPower {

    static final int MIN_LEVEL = 7;

    public void apply(BeforeSave<WizardSpell> event) {
        WizardSpell link = event.entity();
        if (link.getSpell().isUnforgivable() && link.getLevel() != null && link.getLevel() < MIN_LEVEL) {
            event.reject("hogwarts.unforgivableTooWeak",
                    link.getSpell().getName() + " needs level " + MIN_LEVEL + " or more: you need to really mean it");
        }
    }
}
