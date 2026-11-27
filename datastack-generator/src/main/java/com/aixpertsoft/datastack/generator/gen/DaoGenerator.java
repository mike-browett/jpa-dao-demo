package com.aixpertsoft.datastack.generator.gen;

import static com.aixpertsoft.datastack.generator.Names.lower;
import static com.aixpertsoft.datastack.generator.Names.upper;

import com.aixpertsoft.datastack.generator.ModelIndex;
import com.aixpertsoft.datastack.generator.ModelIndex.Entity;
import com.aixpertsoft.datastack.generator.ModelIndex.ToOne;

/** DAO layer: {@code RackDaoBase} (generated finders) and {@code RackDao} (owned, custom queries). */
public final class DaoGenerator {

    private final ModelIndex index;
    private final Output out;
    private final String pkg;

    public DaoGenerator(ModelIndex index, Output out) {
        this.index = index;
        this.out = out;
        this.pkg = index.pkg("dao");
    }

    public void generate() {
        index.entities().forEach(e -> {
            base(e);
            owned(e);
        });
    }

    private void base(Entity e) {
        Src s = new Src(pkg);
        String entity = s.type(index.pkg("entity") + "." + e.name());
        s.imports("com.aixpertsoft.datastack.dao.JpaDao", "java.util.UUID");
        s.line("/** Generated data access for " + e.name() + ". Custom queries go in " + e.name() + "Dao. */");
        s.line("public abstract class " + e.name() + "DaoBase extends JpaDao<" + entity + ", UUID> {");
        s.line();
        s.line("    protected " + e.name() + "DaoBase() {");
        s.line("        super(" + entity + ".class);");
        s.line("    }");
        if (!index.isRoot(e)) {
            s.imports("com.aixpertsoft.datastack.dao.NotFoundException");
            s.line();
            s.line("    /** Typed lookup: fails if the id belongs to another type in the same table. */");
            s.line("    @Override");
            s.line("    public " + entity + " get(UUID id) {");
            s.line("        return em.createQuery(\"select e from " + e.name() + " e where e.id = :id\", " + entity + ".class)");
            s.line("                 .setParameter(\"id\", id)");
            s.line("                 .getResultStream()");
            s.line("                 .findFirst()");
            s.line("                 .orElseThrow(() -> new NotFoundException(\"" + e.name() + "\", id));");
            s.line("    }");
        }
        for (ToOne r : index.ownToOnes(e)) {
            s.imports("java.util.List", "java.util.Collection");
            String n = upper(r.name());
            s.line();
            s.line("    public List<" + entity + "> findBy" + n + "(UUID " + r.name() + "Id) {");
            s.line("        return em.createQuery(\"select e from " + e.name() + " e where e." + r.name() + ".id = :id\", "
                    + entity + ".class)");
            s.line("                 .setParameter(\"id\", " + r.name() + "Id)");
            s.line("                 .getResultList();");
            s.line("    }");
            s.line();
            s.line("    public List<" + entity + "> findBy" + n + "Ids(Collection<UUID> " + r.name() + "Ids) {");
            s.line("        return em.createQuery(\"select e from " + e.name() + " e where e." + r.name() + ".id in :ids\", "
                    + entity + ".class)");
            s.line("                 .setParameter(\"ids\", " + r.name() + "Ids)");
            s.line("                 .getResultList();");
            s.line("    }");
            s.line();
            s.line("    public long countBy" + n + "(UUID " + r.name() + "Id) {");
            s.line("        return em.createQuery(\"select count(e) from " + e.name() + " e where e." + r.name()
                    + ".id = :id\", Long.class)");
            s.line("                 .setParameter(\"id\", " + r.name() + "Id)");
            s.line("                 .getSingleResult();");
            s.line("    }");
        }
        if (e.isIntersection()) {
            s.imports("java.util.Optional");
            String from = lower(e.intersectionOf().from().object());
            String to = lower(e.intersectionOf().to().object());
            s.line();
            s.line("    /** The link between one " + from + " and one " + to + ", if any. */");
            s.line("    public Optional<" + entity + "> find(UUID " + from + "Id, UUID " + to + "Id) {");
            s.line("        return em.createQuery(\"select e from " + e.name() + " e where e." + from + ".id = :from and e."
                    + to + ".id = :to\", " + entity + ".class)");
            s.line("                 .setParameter(\"from\", " + from + "Id)");
            s.line("                 .setParameter(\"to\", " + to + "Id)");
            s.line("                 .getResultStream()");
            s.line("                 .findFirst();");
            s.line("    }");
        }
        s.line("}");
        out.generatedJava(pkg, e.name() + "DaoBase", s.render(Output.GENERATED));
    }

    private void owned(Entity e) {
        Src s = new Src(pkg);
        s.imports("org.springframework.stereotype.Repository");
        s.line("/** Data access for " + e.name() + ". Add custom queries here. */");
        s.line("@Repository");
        s.line("public class " + e.name() + "Dao extends " + e.name() + "DaoBase {");
        s.line("}");
        out.ownedJava(pkg, e.name() + "Dao", s.render(""));
    }
}
