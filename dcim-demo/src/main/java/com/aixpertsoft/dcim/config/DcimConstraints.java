package com.aixpertsoft.dcim.config;

import com.aixpertsoft.datastack.web.ConstraintMapping;
import com.aixpertsoft.datastack.web.ConstraintMappings;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** The database constraints of this microservice, with the error codes clients see. */
@Component
public class DcimConstraints implements ConstraintMappings {

    @Override
    public List<ConstraintMapping> mappings() {
        return List.of(
                new ConstraintMapping("uq_rack_administrator", HttpStatus.CONFLICT, "dcim.duplicateAdministrator",
                        "This person already administers this rack"),
                new ConstraintMapping("ex_rack_primary_administrator", HttpStatus.CONFLICT, "dcim.duplicatePrimary",
                        "A rack can have only one primary administrator"),
                new ConstraintMapping("serial_number", HttpStatus.CONFLICT, "duplicateSerialNumber",
                        "Another component already has this serial number"));
    }
}
