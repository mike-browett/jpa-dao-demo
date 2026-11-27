// Created once by datastack-generator. This file is yours: the generator never overwrites it.
// Put custom code here; it survives every regeneration.
package com.aixpertsoft.dcim.dao;

import com.aixpertsoft.dcim.entity.RackAdministrator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Data access for RackAdministrator. Add custom queries here. */
@Repository
public class RackAdministratorDao extends RackAdministratorDaoBase {

    /** Custom query used by the SinglePrimaryAdministrator rule. */
    public List<RackAdministrator> findByRackAndPrimary(UUID rackId, boolean primaryAdministrator) {
        return em.createQuery("""
                select ra from RackAdministrator ra
                where ra.rack.id = :rackId and ra.primaryAdministrator = :primary""", RackAdministrator.class)
                 .setParameter("rackId", rackId)
                 .setParameter("primary", primaryAdministrator)
                 .getResultList();
    }
}
