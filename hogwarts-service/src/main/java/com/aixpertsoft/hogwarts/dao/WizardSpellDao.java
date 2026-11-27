// Created once by datastack-generator. This file is yours: the generator never overwrites it.
// Put custom code here; it survives every regeneration.
package com.aixpertsoft.hogwarts.dao;

import com.aixpertsoft.hogwarts.entity.WizardSpell;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Data access for WizardSpell. Add custom queries here. */
@Repository
public class WizardSpellDao extends WizardSpellDaoBase {

    /** Custom query used by the SingleFavouriteSpell rule. */
    public List<WizardSpell> findFavourites(UUID wizardId) {
        return em.createQuery("""
                select ws from WizardSpell ws
                where ws.wizard.id = :wizardId and ws.favourite = true""", WizardSpell.class)
                 .setParameter("wizardId", wizardId)
                 .getResultList();
    }
}
