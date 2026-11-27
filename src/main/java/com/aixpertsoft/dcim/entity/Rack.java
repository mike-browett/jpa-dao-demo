package com.aixpertsoft.dcim.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/** A Component that can contain other Components and is run by administrators. */
@Entity
@DiscriminatorValue(Rack.TYPE)
public class Rack extends Component {

    public static final String TYPE = "RACK";

    @Column(name = "height_units")
    private Integer heightUnits;

    @OneToMany(mappedBy = "rack")
    private List<Component> children = new ArrayList<>();

    @OneToMany(mappedBy = "rack")
    private List<RackAdministrator> administrators = new ArrayList<>();

    protected Rack() {
    }

    public Rack(String name, String serialNumber, Integer heightUnits) {
        super(name, serialNumber);
        this.heightUnits = heightUnits;
    }

    public Integer getHeightUnits() { return heightUnits; }
    public void setHeightUnits(Integer heightUnits) { this.heightUnits = heightUnits; }
    public List<Component> getChildren() { return children; }
    public List<RackAdministrator> getAdministrators() { return administrators; }
}
