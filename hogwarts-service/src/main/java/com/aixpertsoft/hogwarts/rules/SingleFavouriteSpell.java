package com.aixpertsoft.hogwarts.rules;

import static com.aixpertsoft.datastack.rules.Event.BEFORE_INSERT;
import static com.aixpertsoft.datastack.rules.Event.BEFORE_UPDATE;

import com.aixpertsoft.datastack.rules.BeforeSave;
import com.aixpertsoft.datastack.rules.Rule;
import com.aixpertsoft.hogwarts.dao.WizardSpellDao;
import com.aixpertsoft.hogwarts.entity.WizardSpell;
import java.util.Objects;

/**
 * Custom business rule: a new favourite spell replaces the old one instead of failing.
 * The "oneFavouriteSpell" exclusion constraint generated from "uniqueWhere" is checked at commit,
 * so demoting the old favourite after inserting the new one is fine.
 */
@Rule(on = WizardSpell.class, events = {BEFORE_INSERT, BEFORE_UPDATE}, order = 100)
public class SingleFavouriteSpell {

    private final WizardSpellDao links;

    public SingleFavouriteSpell(WizardSpellDao links) {
        this.links = links;
    }

    public void apply(BeforeSave<WizardSpell> event) {
        WizardSpell link = event.entity();
        if (!link.isFavourite()) {
            return;
        }
        links.findFavourites(link.getWizard().getId()).stream()
             .filter(other -> !Objects.equals(other.getId(), link.getId()))
             .forEach(other -> other.setFavourite(false));
    }
}
