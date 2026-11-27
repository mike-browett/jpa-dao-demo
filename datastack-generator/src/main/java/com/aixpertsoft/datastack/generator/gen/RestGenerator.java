package com.aixpertsoft.datastack.generator.gen;

import static com.aixpertsoft.datastack.generator.Names.lower;
import static com.aixpertsoft.datastack.generator.Names.singular;
import static com.aixpertsoft.datastack.generator.Names.upper;

import com.aixpertsoft.datastack.generator.ModelIndex;
import com.aixpertsoft.datastack.generator.ModelIndex.Entity;
import com.aixpertsoft.datastack.generator.model.ObjectDef;
import com.aixpertsoft.datastack.generator.model.RelationDef;

/** REST layer: {@code RackControllerBase} (generated endpoints) and {@code RackController} (owned: extra endpoints). */
public final class RestGenerator {

    private final ModelIndex index;
    private final Output out;
    private final String pkg;

    public RestGenerator(ModelIndex index, Output out) {
        this.index = index;
        this.out = out;
        this.pkg = index.pkg("rest");
    }

    public void generate() {
        for (Entity e : index.restEntities()) {
            base(e);
            owned(e);
        }
    }

    private void base(Entity e) {
        ObjectDef.Rest rest = e.def().rest();
        Src s = new Src(pkg);
        s.imports("org.springframework.beans.factory.annotation.Autowired", "org.springframework.web.bind.annotation.RequestMapping",
                "java.util.UUID");
        String service = s.type(index.pkg("service") + "." + e.name() + "Service");
        String dto = s.type(index.pkg("api") + "." + DtoGenerator.dtoName(index, e));
        String requests = s.type(index.pkg("api") + "." + e.name() + "Requests");
        String path = index.restBase() + "/" + rest.path();

        s.line("/** Generated endpoints for " + e.name() + " at " + path + ". Add endpoints in " + e.name() + "Controller. */");
        s.line("@RequestMapping(\"" + path + "\")");
        s.line("public abstract class " + e.name() + "ControllerBase {");
        s.line();
        s.line("    @Autowired");
        s.line("    protected " + service + " service;");
        if (rest.allows("list")) {
            s.imports("com.aixpertsoft.datastack.api.Page", "com.aixpertsoft.datastack.web.Paging",
                    "org.springframework.web.bind.annotation.GetMapping", "org.springframework.web.bind.annotation.RequestParam");
            s.line();
            s.line("    @GetMapping");
            s.line("    public Page<" + dto + "> list(@RequestParam(required = false) Integer page,");
            s.line("                                 @RequestParam(required = false) Integer size) {");
            s.line("        return service.list(Paging.page(page), Paging.size(size));");
            s.line("    }");
        }
        if (rest.allows("read")) {
            s.imports("org.springframework.web.bind.annotation.GetMapping", "org.springframework.web.bind.annotation.PathVariable");
            s.line();
            s.line("    @GetMapping(\"/{id}\")");
            s.line("    public " + dto + " get(@PathVariable UUID id) {");
            s.line("        return service.get(id);");
            s.line("    }");
        }
        if (rest.allows("create")) {
            s.imports("org.springframework.web.bind.annotation.PostMapping", "org.springframework.web.bind.annotation.RequestBody",
                    "org.springframework.web.bind.annotation.ResponseStatus", "org.springframework.http.HttpStatus",
                    "jakarta.validation.Valid");
            s.line();
            s.line("    @PostMapping");
            s.line("    @ResponseStatus(HttpStatus.CREATED)");
            s.line("    public " + dto + " create(@Valid @RequestBody " + requests + ".Create request) {");
            s.line("        return service.create(request);");
            s.line("    }");
        }
        if (rest.allows("update")) {
            s.imports("org.springframework.web.bind.annotation.PatchMapping", "org.springframework.web.bind.annotation.PathVariable",
                    "org.springframework.web.bind.annotation.RequestBody", "jakarta.validation.Valid");
            s.line();
            s.line("    @PatchMapping(\"/{id}\")");
            s.line("    public " + dto + " update(@PathVariable UUID id, @Valid @RequestBody " + requests + ".Update request) {");
            s.line("        return service.update(id, request);");
            s.line("    }");
        }
        if (rest.allows("delete")) {
            s.imports("org.springframework.web.bind.annotation.DeleteMapping", "org.springframework.web.bind.annotation.PathVariable",
                    "org.springframework.web.bind.annotation.ResponseStatus", "org.springframework.http.HttpStatus");
            s.line();
            s.line("    @DeleteMapping(\"/{id}\")");
            s.line("    @ResponseStatus(HttpStatus.NO_CONTENT)");
            s.line("    public void delete(@PathVariable UUID id) {");
            s.line("        service.delete(id);");
            s.line("    }");
        }
        for (RelationDef r : index.restRelationsFrom(e)) {
            if (r.isOneToMany()) oneToMany(s, r);
            if (r.isManyToMany()) manyToMany(s, r);
        }
        s.line("}");
        out.generatedJava(pkg, e.name() + "ControllerBase", s.render(Output.GENERATED));
    }

    private void oneToMany(Src s, RelationDef r) {
        s.imports("org.springframework.web.bind.annotation.PutMapping", "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PathVariable", "org.springframework.web.bind.annotation.ResponseStatus",
                "org.springframework.http.HttpStatus");
        Entity child = index.entity(r.to().object());
        String childDto = s.type(index.pkg("api") + "." + DtoGenerator.dtoName(index, child));
        String item = upper(singular(r.from().name()));
        String childId = lower(child.name()) + "Id";
        String sub = "/{id}/" + r.rest().path() + "/{" + childId + "}";
        s.line();
        s.line("    // ---- relation " + r.id() + " ----");
        s.line();
        s.line("    @PutMapping(\"" + sub + "\")");
        s.line("    public " + childDto + " add" + item + "(@PathVariable UUID id, @PathVariable UUID " + childId + ") {");
        s.line("        return service.add" + item + "(id, " + childId + ");");
        s.line("    }");
        s.line();
        s.line("    @DeleteMapping(\"" + sub + "\")");
        s.line("    @ResponseStatus(HttpStatus.NO_CONTENT)");
        s.line("    public void remove" + item + "(@PathVariable UUID id, @PathVariable UUID " + childId + ") {");
        s.line("        service.remove" + item + "(id, " + childId + ");");
        s.line("    }");
    }

    private void manyToMany(Src s, RelationDef r) {
        s.imports("org.springframework.web.bind.annotation.GetMapping", "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PatchMapping", "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PathVariable", "org.springframework.web.bind.annotation.RequestBody",
                "org.springframework.web.bind.annotation.ResponseStatus", "org.springframework.http.HttpStatus",
                "jakarta.validation.Valid", "java.util.List");
        String linkDto = s.type(index.pkg("api") + "." + r.id() + "Dto");
        String requests = s.type(index.pkg("api") + "." + r.id() + "Requests");
        String item = upper(singular(r.from().name()));
        String toId = lower(r.to().object()) + "Id";
        String base = "/{id}/" + r.rest().path();
        s.line();
        s.line("    // ---- relation " + r.id() + " ----");
        s.line();
        s.line("    @GetMapping(\"" + base + "\")");
        s.line("    public List<" + linkDto + "> list" + upper(r.from().name()) + "(@PathVariable UUID id) {");
        s.line("        return service.list" + upper(r.from().name()) + "(id);");
        s.line("    }");
        s.line();
        s.line("    @PostMapping(\"" + base + "\")");
        s.line("    @ResponseStatus(HttpStatus.CREATED)");
        s.line("    public " + linkDto + " add" + item + "(@PathVariable UUID id, @Valid @RequestBody " + requests + ".Link request) {");
        s.line("        return service.add" + item + "(id, request);");
        s.line("    }");
        s.line();
        s.line("    @PatchMapping(\"" + base + "/{" + toId + "}\")");
        s.line("    public " + linkDto + " update" + item + "(@PathVariable UUID id, @PathVariable UUID " + toId + ",");
        s.line("            @Valid @RequestBody " + requests + ".Update request) {");
        s.line("        return service.update" + item + "(id, " + toId + ", request);");
        s.line("    }");
        s.line();
        s.line("    @DeleteMapping(\"" + base + "/{" + toId + "}\")");
        s.line("    @ResponseStatus(HttpStatus.NO_CONTENT)");
        s.line("    public void remove" + item + "(@PathVariable UUID id, @PathVariable UUID " + toId + ") {");
        s.line("        service.remove" + item + "(id, " + toId + ");");
        s.line("    }");
    }

    private void owned(Entity e) {
        Src s = new Src(pkg);
        s.imports("org.springframework.web.bind.annotation.RestController");
        s.line("/** REST endpoints for " + e.name() + ". Add custom endpoints here; paths start at the base class mapping. */");
        s.line("@RestController");
        s.line("public class " + e.name() + "Controller extends " + e.name() + "ControllerBase {");
        s.line("}");
        out.ownedJava(pkg, e.name() + "Controller", s.render(""));
    }
}
