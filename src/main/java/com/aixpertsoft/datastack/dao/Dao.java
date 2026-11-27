package com.aixpertsoft.datastack.dao;

import java.util.List;
import java.util.Optional;

/**
 * The single write gateway for one object type. Every create, update and delete goes through a DAO,
 * which is what lets business rules fire on every write path.
 */
public interface Dao<T, ID> {

    Optional<T> find(ID id);

    /** Like {@link #find} but throws {@link NotFoundException} when the object does not exist. */
    T get(ID id);

    List<T> list(int page, int size);

    long count();

    T create(T entity);

    T update(T entity);

    void delete(T entity);

    /** Sends pending changes to the database now (still inside the current transaction). */
    void flush();
}
