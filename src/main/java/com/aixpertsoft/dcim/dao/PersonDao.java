package com.aixpertsoft.dcim.dao;

import com.aixpertsoft.datastack.dao.JpaDao;
import com.aixpertsoft.dcim.entity.Person;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class PersonDao extends JpaDao<Person, UUID> {

    public PersonDao() {
        super(Person.class);
    }

    @Override
    public List<Person> list(int page, int size) {
        return em.createQuery("select p from Person p order by p.name", Person.class)
                 .setFirstResult(page * size)
                 .setMaxResults(size)
                 .getResultList();
    }
}
