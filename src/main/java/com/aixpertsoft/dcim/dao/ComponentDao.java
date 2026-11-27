package com.aixpertsoft.dcim.dao;

import com.aixpertsoft.datastack.dao.JpaDao;
import com.aixpertsoft.dcim.entity.Component;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ComponentDao extends JpaDao<Component, UUID> {

    public ComponentDao() {
        super(Component.class);
    }

    /** Children of several racks in one query: the batched fetch behind the view's "children" link. */
    public List<Component> findChildrenOfRacks(Collection<UUID> rackIds) {
        return em.createQuery("""
                select c from Component c
                where c.rack.id in :rackIds
                order by c.name""", Component.class)
                 .setParameter("rackIds", rackIds)
                 .getResultList();
    }

    public long countChildren(UUID rackId) {
        return em.createQuery("select count(c) from Component c where c.rack.id = :rackId", Long.class)
                 .setParameter("rackId", rackId)
                 .getSingleResult();
    }
}
