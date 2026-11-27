package com.aixpertsoft.dcim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A human or AI entity with a name and contact details. Login and security stay with User. */
@Entity
@Table(name = "person")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private long version;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", nullable = false, length = 10)
    private PersonType personType;

    /** JSON attributes, stored in the dedicated jsonb column "ext". */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ext", nullable = false, columnDefinition = "jsonb")
    private PersonExt ext = PersonExt.empty();

    protected Person() {
    }

    public Person(String name, PersonType personType, PersonContact contact) {
        this.name = name;
        this.personType = personType;
        this.ext = new PersonExt(contact);
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PersonType getPersonType() { return personType; }
    public void setPersonType(PersonType personType) { this.personType = personType; }

    /** Typed accessor for the JSON attribute "contact". */
    public PersonContact getContact() { return ext.contact(); }
    public void setContact(PersonContact contact) { this.ext = new PersonExt(contact); }
}
