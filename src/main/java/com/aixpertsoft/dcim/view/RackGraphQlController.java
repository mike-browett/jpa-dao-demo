package com.aixpertsoft.dcim.view;

import com.aixpertsoft.dcim.view.ViewNodes.AdministratorEdge;
import com.aixpertsoft.dcim.view.ViewNodes.ComponentNode;
import com.aixpertsoft.dcim.view.ViewNodes.RackNode;
import java.util.List;
import java.util.Map;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/**
 * Wires the GraphQL schema (schema.graphqls) to the view service. @BatchMapping makes Spring
 * collect all racks of a response and resolve "children" and "administrators" in one call each.
 */
@Controller
public class RackGraphQlController {

    private static final int MAX_SIZE = 100;

    private final RackViewService views;

    public RackGraphQlController(RackViewService views) {
        this.views = views;
    }

    @QueryMapping
    public List<RackNode> racks(@Argument Integer page, @Argument Integer size, @Argument String nameLike) {
        int p = page != null ? Math.max(0, page) : 0;
        int s = size != null ? Math.max(1, Math.min(size, MAX_SIZE)) : 20;
        return views.racks(p, s, nameLike);
    }

    @QueryMapping
    public int rackCount(@Argument String nameLike) {
        return (int) views.rackCount(nameLike);
    }

    @BatchMapping(typeName = "Rack")
    public Map<RackNode, List<ComponentNode>> children(List<RackNode> racks) {
        return views.children(racks);
    }

    @BatchMapping(typeName = "Rack")
    public Map<RackNode, List<AdministratorEdge>> administrators(List<RackNode> racks) {
        return views.administrators(racks);
    }
}
