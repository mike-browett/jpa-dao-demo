package com.aixpertsoft.dcim.api;

import com.aixpertsoft.dcim.entity.Person;
import com.aixpertsoft.dcim.entity.PersonContact;
import com.aixpertsoft.dcim.entity.PersonType;
import java.util.UUID;

public record PersonDto(UUID id, long version, String name, PersonType personType, PersonContact contact) {

    public static PersonDto of(Person p) {
        return new PersonDto(p.getId(), p.getVersion(), p.getName(), p.getPersonType(), p.getContact());
    }
}
