package com.aixpertsoft.dcim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** Intersection entity of the M-to-N relation Rack <-> Person, carrying the primary flag. */
@Entity
@Table(name = "rack_administrator")
public class RackAdministrator {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rack_id")
    private Rack rack;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id")
    private Person person;

    @Column(name = "primary_administrator", nullable = false)
    private boolean primaryAdministrator;

    protected RackAdministrator() {
    }

    public RackAdministrator(Rack rack, Person person, boolean primaryAdministrator) {
        this.rack = rack;
        this.person = person;
        this.primaryAdministrator = primaryAdministrator;
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public Rack getRack() { return rack; }
    public Person getPerson() { return person; }
    public boolean isPrimaryAdministrator() { return primaryAdministrator; }
    public void setPrimaryAdministrator(boolean primaryAdministrator) { this.primaryAdministrator = primaryAdministrator; }
}
