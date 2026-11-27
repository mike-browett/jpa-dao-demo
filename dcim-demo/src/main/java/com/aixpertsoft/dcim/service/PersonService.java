package com.aixpertsoft.dcim.service;

import com.aixpertsoft.dcim.api.Page;
import com.aixpertsoft.dcim.api.PersonDto;
import com.aixpertsoft.dcim.api.PersonRequests;
import com.aixpertsoft.dcim.dao.PersonDao;
import com.aixpertsoft.dcim.entity.Person;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases for persons. Owns the transaction; talks to DAOs, returns DTOs. */
@Service
@Transactional
public class PersonService {

    private final PersonDao persons;

    public PersonService(PersonDao persons) {
        this.persons = persons;
    }

    @Transactional(readOnly = true)
    public Page<PersonDto> list(int page, int size) {
        return new Page<>(page, size, persons.count(), persons.list(page, size).stream().map(PersonDto::of).toList());
    }

    @Transactional(readOnly = true)
    public PersonDto get(UUID id) {
        return PersonDto.of(persons.get(id));
    }

    public PersonDto create(PersonRequests.Create request) {
        Person person = persons.create(new Person(request.name(), request.personType(), request.contact()));
        return PersonDto.of(person);
    }

    public PersonDto update(UUID id, PersonRequests.Update request) {
        Person person = persons.get(id);
        Versions.check(request.version(), person.getVersion());
        if (request.name() != null) person.setName(request.name());
        if (request.personType() != null) person.setPersonType(request.personType());
        if (request.contact() != null) person.setContact(request.contact());
        persons.update(person);
        persons.flush();   // so the returned version is the new one
        return PersonDto.of(person);
    }

    public void delete(UUID id) {
        persons.delete(persons.get(id));
    }
}
