package com.aixpertsoft.datastack.dao;

import com.aixpertsoft.datastack.rules.Event;
import com.aixpertsoft.datastack.rules.RuleDispatcher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * JPA implementation of {@link Dao}. Raises the lifecycle events that business rules listen to,
 * so rules run whatever service or endpoint triggered the write.
 */
public abstract class JpaDao<T, ID> implements Dao<T, ID> {

    @PersistenceContext
    protected EntityManager em;

    private RuleDispatcher rules;

    private final Class<T> type;

    protected JpaDao(Class<T> type) {
        this.type = type;
    }

    @Autowired
    void setRuleDispatcher(RuleDispatcher rules) {
        this.rules = rules;
    }

    @Override
    public Optional<T> find(ID id) {
        return Optional.ofNullable(em.find(type, id));
    }

    @Override
    public T get(ID id) {
        return find(id).orElseThrow(() -> new NotFoundException(type.getSimpleName(), id));
    }

    @Override
    public List<T> list(int page, int size) {
        return em.createQuery("select e from " + type.getSimpleName() + " e order by e.id", type)
                 .setFirstResult(page * size)
                 .setMaxResults(size)
                 .getResultList();
    }

    @Override
    public long count() {
        return em.createQuery("select count(e) from " + type.getSimpleName() + " e", Long.class)
                 .getSingleResult();
    }

    @Override
    public T create(T entity) {
        fire(Event.BEFORE_INSERT, entity);
        em.persist(entity);
        return entity;
    }

    @Override
    public T update(T entity) {
        fire(Event.BEFORE_UPDATE, entity);
        return em.merge(entity);
    }

    @Override
    public void delete(T entity) {
        fire(Event.BEFORE_DELETE, entity);
        em.remove(entity);
    }

    @Override
    public void flush() {
        em.flush();
    }

    protected void fire(Event event, T entity) {
        if (rules != null) {
            rules.fire(event, entity);
        }
    }
}
