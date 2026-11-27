package com.aixpertsoft.datastack.generator.gen;

import static com.aixpertsoft.datastack.generator.Names.article;
import static com.aixpertsoft.datastack.generator.Names.lower;
import static com.aixpertsoft.datastack.generator.Names.singular;
import static com.aixpertsoft.datastack.generator.Names.upper;

import com.aixpertsoft.datastack.generator.ModelIndex;
import com.aixpertsoft.datastack.generator.ModelIndex.Entity;
import com.aixpertsoft.datastack.generator.ModelIndex.ToOne;
import com.aixpertsoft.datastack.generator.model.AttributeDef;
import com.aixpertsoft.datastack.generator.model.RelationDef;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Service layer: {@code RackServiceBase} (generated use cases: CRUD, relations, transactions, error codes)
 * and {@code RackService} (owned: override or add use cases).
 */
public final class ServiceGenerator {

    private final ModelIndex index;
    private final Output out;
    private final String pkg;
    private final String model;

    public ServiceGenerator(ModelIndex index, Output out) {
        this.index = index;
        this.out = out;
        this.pkg = index.pkg("service");
        this.model = index.model().id();
    }

    public void generate() {
        for (Entity e : index.restEntities()) {
            base(e);
            owned(e);
        }
    }

    /** Error codes derived from the model, e.g. dcim.rackNotEmpty, dcim.duplicateAdministrator. */
    public static String notEmptyCode(String model, String fromObject) {
        return model + "." + lower(fromObject) + "NotEmpty";
    }

    public static String duplicateLinkCode(String model, RelationDef r) {
        return model + ".duplicate" + upper(singular(r.from().name()));
    }

    private void base(Entity e) {
        Src s = new Src(pkg);
        s.imports("com.aixpertsoft.datastack.api.Page", "com.aixpertsoft.datastack.service.Versions",
                "org.springframework.beans.factory.annotation.Autowired",
                "org.springframework.transaction.annotation.Transactional", "java.util.UUID");
        String entity = s.type(index.pkg("entity") + "." + e.name());
        String dto = s.type(index.pkg("api") + "." + DtoGenerator.dtoName(index, e));
        String requests = s.type(index.pkg("api") + "." + e.name() + "Requests");

        // DAOs this service needs
        Set<Entity> daos = new LinkedHashSet<>();
        daos.add(e);
        index.allToOnes(e).forEach(r -> daos.add(r.target()));
        index.restrictingRelations(e).forEach(r -> daos.add(index.entity(r.to().object())));
        for (RelationDef r : index.restRelationsFrom(e)) {
            daos.add(index.entity(r.to().object()));
            if (r.isManyToMany()) daos.add(index.entity(r.id()));
        }

        s.line("/** Generated use cases for " + e.name() + ". Override or add use cases in " + e.name() + "Service. */");
        s.line("@Transactional");
        s.line("public abstract class " + e.name() + "ServiceBase {");
        s.line();
        for (Entity d : daos) {
            s.line("    @Autowired");
            s.line("    protected " + s.type(index.pkg("dao") + "." + d.name() + "Dao") + " " + lower(d.name()) + "Dao;");
            s.line();
        }
        String dao = lower(e.name()) + "Dao";

        s.line("    @Transactional(readOnly = true)");
        s.line("    public Page<" + dto + "> list(int page, int size) {");
        s.line("        return new Page<>(page, size, " + dao + ".count(),");
        s.line("                " + dao + ".list(page, size).stream().map(" + dto + "::of).toList());");
        s.line("    }");
        s.line();
        s.line("    @Transactional(readOnly = true)");
        s.line("    public " + dto + " get(UUID id) {");
        s.line("        return " + dto + ".of(" + dao + ".get(id));");
        s.line("    }");
        s.line();
        s.line("    public " + dto + " create(" + requests + ".Create request) {");
        s.line("        " + entity + " e = new " + entity + "();");
        for (AttributeDef a : index.allAttributes(e)) {
            if (a.defaultValue() != null) {
                s.line("        if (request." + a.name() + "() != null) e.set" + upper(a.name()) + "(request." + a.name() + "());");
            } else {
                s.line("        e.set" + upper(a.name()) + "(request." + a.name() + "());");
            }
        }
        for (ToOne r : index.allToOnes(e)) {
            s.line("        if (request." + r.name() + "Id() != null) e.set" + upper(r.name()) + "(" + lower(r.target().name())
                    + "Dao.get(request." + r.name() + "Id()));");
        }
        s.line("        return " + dto + ".of(" + dao + ".create(e));");
        s.line("    }");
        s.line();
        s.line("    public " + dto + " update(UUID id, " + requests + ".Update request) {");
        s.line("        " + entity + " e = " + dao + ".get(id);");
        s.line("        Versions.check(request.version(), e.getVersion());");
        for (AttributeDef a : index.allAttributes(e)) {
            s.line("        if (request." + a.name() + "() != null) e.set" + upper(a.name()) + "(request." + a.name() + "());");
        }
        s.line("        " + dao + ".update(e);");
        s.line("        " + dao + ".flush();   // so the returned version is the new one");
        s.line("        return " + dto + ".of(e);");
        s.line("    }");
        s.line();
        s.line("    public void delete(UUID id) {");
        s.line("        " + entity + " e = " + dao + ".get(id);");
        for (RelationDef r : index.restrictingRelations(e)) {
            s.imports("com.aixpertsoft.datastack.web.ApiException", "org.springframework.http.HttpStatus");
            String childDao = lower(r.to().object()) + "Dao";
            String count = childDao + ".countBy" + upper(r.to().name()) + "(id) > 0";
            String guard = r.from().object().equals(e.name()) ? count
                    : "e instanceof " + s.type(index.pkg("entity") + "." + r.from().object()) + " && " + count;
            s.line("        if (" + guard + ") {");
            s.line("            throw new ApiException(HttpStatus.CONFLICT, \"" + notEmptyCode(model, r.from().object()) + "\",");
            s.line("                    \"The " + lower(r.from().object()) + " still has " + r.from().name() + "; remove them first\");");
            s.line("        }");
        }
        s.line("        " + dao + ".delete(e);");
        s.line("    }");

        for (RelationDef r : index.restRelationsFrom(e)) {
            if (r.isOneToMany()) oneToMany(s, e, r);
            if (r.isManyToMany()) manyToMany(s, e, r);
        }
        s.line("}");
        out.generatedJava(pkg, e.name() + "ServiceBase", s.render(Output.GENERATED));
    }

    private void oneToMany(Src s, Entity e, RelationDef r) {
        s.imports("com.aixpertsoft.datastack.web.ApiException", "org.springframework.http.HttpStatus", "java.util.Objects");
        Entity child = index.entity(r.to().object());
        String childDto = s.type(index.pkg("api") + "." + DtoGenerator.dtoName(index, child));
        String childDao = lower(child.name()) + "Dao";
        String ref = upper(r.to().name());
        String item = upper(singular(r.from().name()));
        s.line();
        s.line("    // ---- relation " + r.id() + ": " + e.name() + "." + r.from().name() + " (1-to-N) ----");
        s.line();
        s.line("    /** Puts a " + lower(child.name()) + " into this " + lower(e.name()) + ", moving it out of any other. */");
        s.line("    public " + childDto + " add" + item + "(UUID id, UUID " + lower(child.name()) + "Id) {");
        s.line("        var parent = " + lower(e.name()) + "Dao.get(id);");
        s.line("        var child = " + childDao + ".get(" + lower(child.name()) + "Id);");
        s.line("        child.set" + ref + "(parent);");
        s.line("        " + childDao + ".update(child);");
        s.line("        " + childDao + ".flush();");
        s.line("        return " + childDto + ".of(child);");
        s.line("    }");
        s.line();
        s.line("    /** Takes a " + lower(child.name()) + " out of this " + lower(e.name()) + ". The " + lower(child.name()) + " itself is kept. */");
        s.line("    public void remove" + item + "(UUID id, UUID " + lower(child.name()) + "Id) {");
        s.line("        var child = " + childDao + ".get(" + lower(child.name()) + "Id);");
        s.line("        if (child.get" + ref + "() == null || !Objects.equals(child.get" + ref + "().getId(), id)) {");
        s.line("            throw new ApiException(HttpStatus.NOT_FOUND, \"" + model + ".notIn" + e.name() + "\",");
        s.line("                    \"" + child.name() + " \" + " + lower(child.name()) + "Id + \" is not in " + lower(e.name()) + " \" + id);");
        s.line("        }");
        s.line("        child.set" + ref + "(null);");
        s.line("        " + childDao + ".update(child);");
        s.line("    }");
    }

    private void manyToMany(Src s, Entity e, RelationDef r) {
        s.imports("com.aixpertsoft.datastack.web.ApiException", "org.springframework.http.HttpStatus", "java.util.List");
        Entity link = index.entity(r.id());
        String linkEntity = s.type(index.pkg("entity") + "." + link.name());
        String linkDto = s.type(index.pkg("api") + "." + link.name() + "Dto");
        String requests = s.type(index.pkg("api") + "." + r.id() + "Requests");
        String linkDao = lower(link.name()) + "Dao";
        String from = lower(r.from().object());
        String to = lower(r.to().object());
        String item = upper(singular(r.from().name()));
        s.line();
        s.line("    // ---- relation " + r.id() + ": " + e.name() + "." + r.from().name() + " (M-to-N) ----");
        s.line();
        s.line("    @Transactional(readOnly = true)");
        s.line("    public List<" + linkDto + "> list" + upper(r.from().name()) + "(UUID id) {");
        s.line("        " + lower(e.name()) + "Dao.get(id);   // 404 if it does not exist");
        s.line("        return " + linkDao + ".findBy" + upper(from) + "(id).stream().map(" + linkDto + "::of).toList();");
        s.line("    }");
        s.line();
        s.line("    public " + linkDto + " add" + item + "(UUID id, " + requests + ".Link request) {");
        s.line("        if (" + linkDao + ".find(id, request." + to + "Id()).isPresent()) {");
        s.line("            throw new ApiException(HttpStatus.CONFLICT, \"" + duplicateLinkCode(model, r) + "\",");
        s.line("                    \"This " + to + " is already linked to this " + from + "\");");
        s.line("        }");
        s.line("        " + linkEntity + " link = new " + linkEntity + "();");
        s.line("        link.set" + upper(from) + "(" + lower(e.name()) + "Dao.get(id));");
        s.line("        link.set" + upper(to) + "(" + lower(r.to().object()) + "Dao.get(request." + to + "Id()));");
        for (AttributeDef a : link.def().attributes()) {
            s.line("        if (request." + a.name() + "() != null) link.set" + upper(a.name()) + "(request." + a.name() + "());");
        }
        s.line("        return " + linkDto + ".of(" + linkDao + ".create(link));");
        s.line("    }");
        s.line();
        s.line("    public " + linkDto + " update" + item + "(UUID id, UUID " + to + "Id, " + requests + ".Update request) {");
        s.line("        " + linkEntity + " link = find" + item + "(id, " + to + "Id);");
        for (AttributeDef a : link.def().attributes()) {
            s.line("        if (request." + a.name() + "() != null) link.set" + upper(a.name()) + "(request." + a.name() + "());");
        }
        s.line("        " + linkDao + ".update(link);");
        s.line("        return " + linkDto + ".of(link);");
        s.line("    }");
        s.line();
        s.line("    public void remove" + item + "(UUID id, UUID " + to + "Id) {");
        s.line("        " + linkDao + ".delete(find" + item + "(id, " + to + "Id));");
        s.line("    }");
        s.line();
        String singularName = singular(r.from().name());
        s.line("    protected " + linkEntity + " find" + item + "(UUID id, UUID " + to + "Id) {");
        s.line("        return " + linkDao + ".find(id, " + to + "Id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,");
        s.line("                \"" + model + ".not" + article(singularName) + upper(singularName) + "\",");
        s.line("                \"" + upper(to) + " \" + " + to + "Id + \" is not linked to " + from + " \" + id));");
        s.line("    }");
    }

    private void owned(Entity e) {
        Src s = new Src(pkg);
        s.imports("org.springframework.stereotype.Service");
        s.line("/** Use cases for " + e.name() + ". Override generated ones or add your own here. */");
        s.line("@Service");
        s.line("public class " + e.name() + "Service extends " + e.name() + "ServiceBase {");
        s.line("}");
        out.ownedJava(pkg, e.name() + "Service", s.render(""));
    }
}
