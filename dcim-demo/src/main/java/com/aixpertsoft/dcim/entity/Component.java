package com.aixpertsoft.dcim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/** Any piece of hardware. Racks are Components too; both live in table "component". */
@Entity
@Table(name = "component")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", length = 30)
@DiscriminatorValue(Component.TYPE)
public class Component {

    public static final String TYPE = "COMPONENT";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private long version;

    /** The discriminator, readable but written by Hibernate only. */
    @Column(name = "type", insertable = false, updatable = false)
    private String type;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "serial_number", unique = true, length = 60)
    private String serialNumber;

    /** The rack this component sits in, or null. Relation RackChildren, 1-to-N. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rack_id")
    private Rack rack;

    protected Component() {
    }

    public Component(String name, String serialNumber) {
        this.name = name;
        this.serialNumber = serialNumber;
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public Rack getRack() { return rack; }
    public void setRack(Rack rack) { this.rack = rack; }

    /** COMPONENT or RACK. Not yet set on a new object before it is saved, so derive it from the class. */
    public String getType() {
        return type != null ? type : (this instanceof Rack ? Rack.TYPE : TYPE);
    }
}
