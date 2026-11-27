package com.aixpertsoft.dcim.dao;

import com.aixpertsoft.datastack.dao.JpaDao;
import com.aixpertsoft.datastack.dao.NotFoundException;
import com.aixpertsoft.dcim.entity.Rack;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class RackDao extends JpaDao<Rack, UUID> {

    public RackDao() {
        super(Rack.class);
    }

    /** Typed lookup: fails if the id belongs to a component that is not a rack. */
    @Override
    public Rack get(UUID id) {
        return em.createQuery("select r from Rack r where r.id = :id", Rack.class)
                 .setParameter("id", id)
                 .getResultStream()
                 .findFirst()
                 .orElseThrow(() -> new NotFoundException("Rack", id));
    }

    /** Racks ordered by name; namePattern is an SQL LIKE pattern (case-insensitive) or null for all. */
    public List<Rack> list(int page, int size, String namePattern) {
        return filtered("select r from Rack r", " order by r.name", namePattern, Rack.class)
                 .setFirstResult(page * size)
                 .setMaxResults(size)
                 .getResultList();
    }

    public long count(String namePattern) {
        return filtered("select count(r) from Rack r", "", namePattern, Long.class).getSingleResult();
    }

    private <R> TypedQuery<R> filtered(String select, String orderBy, String namePattern, Class<R> resultType) {
        if (namePattern == null || namePattern.isBlank()) {
            return em.createQuery(select + orderBy, resultType);
        }
        return em.createQuery(select + " where lower(r.name) like lower(:pattern)" + orderBy, resultType)
                 .setParameter("pattern", namePattern);
    }
}
