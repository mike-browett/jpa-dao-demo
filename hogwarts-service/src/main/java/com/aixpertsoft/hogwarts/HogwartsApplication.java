// Created once by datastack-generator. This file is yours: the generator never overwrites it.
// Put custom code here; it survives every regeneration.
package com.aixpertsoft.hogwarts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The hogwarts microservice. Scans the data stack runtime and this model's package. */
@SpringBootApplication(scanBasePackages = {"com.aixpertsoft.datastack", "com.aixpertsoft.hogwarts"})
public class HogwartsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HogwartsApplication.class, args);
    }
}
