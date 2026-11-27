package com.aixpertsoft.dcim.dao;

import com.aixpertsoft.datastack.dao.JpaDao;
import com.aixpertsoft.dcim.entity.RackAdministrator;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RackAdministratorDao extends JpaDao<RackAdministrator, UUID> {

    public RackAdministratorDao() {
        super(RackAdministrator.class);
    }

    public Optional<RackAdministrator> find(UUID rackId, UUID personId) {
        return em.createQuery("""
                select ra from RackAdministrator ra
                where ra.rack.id = :rackId and ra.person.id = :personId""", RackAdministrator.class)
                 .setParameter("rackId", rackId)
                 .setParameter("personId", personId)
                 .getResultStream()
                 .findFirst();
    }

    public List<RackAdministrator> findByRack(UUID rackId) {
        return findByRacks(List.of(rackId));
    }

    public List<RackAdministrator> findByRackAndPrimary(UUID rackId, boolean primaryAdministrator) {
        return em.createQuery("""
                select ra from RackAdministrator ra
                where ra.rack.id = :rackId and ra.primaryAdministrator = :primary""", RackAdministrator.class)
                 .setParameter("rackId", rackId)
                 .setParameter("primary", primaryAdministrator)
                 .getResultList();
    }

    /**
     * Administrator links of several racks, with their persons, in one query:
     * the batched fetch behind the view's "administrators" link.
     */
    public List<RackAdministrator> findByRacks(Collection<UUID> rackIds) {
        return em.createQuery("""
                select ra from RackAdministrator ra
                join fetch ra.person
                where ra.rack.id in :rackIds
                order by ra.primaryAdministrator desc, ra.person.name""", RackAdministrator.class)
                 .setParameter("rackIds", rackIds)
                 .getResultList();
    }
}
