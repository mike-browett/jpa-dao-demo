package com.aixpertsoft.datastack.generator;

import com.aixpertsoft.datastack.generator.gen.ChangelogGenerator;
import com.aixpertsoft.datastack.generator.gen.ConfigGenerator;
import com.aixpertsoft.datastack.generator.gen.DaoGenerator;
import com.aixpertsoft.datastack.generator.gen.DtoGenerator;
import com.aixpertsoft.datastack.generator.gen.EntityGenerator;
import com.aixpertsoft.datastack.generator.gen.Output;
import com.aixpertsoft.datastack.generator.gen.RestGenerator;
import com.aixpertsoft.datastack.generator.gen.ServiceGenerator;
import com.aixpertsoft.datastack.generator.gen.ViewGenerator;
import com.aixpertsoft.datastack.generator.model.Model;

/** Runs every generator over a validated model. */
public final class Generator {

    private final ModelIndex index;
    private final Output out;

    public Generator(Model model, Output out) {
        this.index = new ModelIndex(model);
        this.out = out;
    }

    public void run() {
        out.clean();
        new ChangelogGenerator(index, out).generate();
        new EntityGenerator(index, out).generate();
        new DaoGenerator(index, out).generate();
        new DtoGenerator(index, out).generate();
        new ServiceGenerator(index, out).generate();
        new RestGenerator(index, out).generate();
        new ViewGenerator(index, out).generate();
        new ConfigGenerator(index, out).generate();
    }
}
