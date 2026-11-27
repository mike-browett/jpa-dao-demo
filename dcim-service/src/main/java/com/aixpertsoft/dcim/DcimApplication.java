// Created once by datastack-generator. This file is yours: the generator never overwrites it.
// Put custom code here; it survives every regeneration.
package com.aixpertsoft.dcim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The dcim microservice. Scans the data stack runtime and this model's package. */
@SpringBootApplication(scanBasePackages = {"com.aixpertsoft.datastack", "com.aixpertsoft.dcim"})
public class DcimApplication {

    public static void main(String[] args) {
        SpringApplication.run(DcimApplication.class, args);
    }
}
