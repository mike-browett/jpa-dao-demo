package com.aixpertsoft.dcim.view;

import com.aixpertsoft.dcim.dao.ComponentDao;
import com.aixpertsoft.dcim.dao.RackAdministratorDao;
import com.aixpertsoft.dcim.dao.RackDao;
import com.aixpertsoft.dcim.view.ViewNodes.AdministratorEdge;
import com.aixpertsoft.dcim.view.ViewNodes.ComponentNode;
import com.aixpertsoft.dcim.view.ViewNodes.PersonNode;
import com.aixpertsoft.dcim.view.ViewNodes.RackNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data access for the views, one method per graph level. Each method is one SQL query whatever the
 * number of racks: this is the data-source contract's fetchRoots / fetchByParentKeys, in hand-written form.
 */
@Service
@Transactional(readOnly = true)
public class RackViewService {

    private final RackDao racks;
    private final ComponentDao components;
    private final RackAdministratorDao administrators;

    public RackViewService(RackDao racks, ComponentDao components, RackAdministratorDao administrators) {
        this.racks = racks;
        this.components = components;
        this.administrators = administrators;
    }

    public List<RackNode> racks(int page, int size, String namePattern) {
        return racks.list(page, size, namePattern).stream()
                .map(r -> new RackNode(r.getId(), r.getName(), r.getSerialNumber(), r.getHeightUnits()))
                .toList();
    }

    public long rackCount(String namePattern) {
        return racks.count(namePattern);
    }

    public Map<RackNode, List<ComponentNode>> children(List<RackNode> parents) {
        Map<UUID, List<ComponentNode>> byRack = components.findChildrenOfRacks(ids(parents)).stream()
                .collect(Collectors.groupingBy(c -> c.getRack().getId(),
                        Collectors.mapping(c -> new ComponentNode(c.getId(), c.getType(), c.getName(),
                                c.getSerialNumber()), Collectors.toList())));
        return byParent(parents, byRack);
    }

    public Map<RackNode, List<AdministratorEdge>> administrators(List<RackNode> parents) {
        Map<UUID, List<AdministratorEdge>> byRack = administrators.findByRacks(ids(parents)).stream()
                .collect(Collectors.groupingBy(ra -> ra.getRack().getId(),
                        Collectors.mapping(ra -> new AdministratorEdge(ra.isPrimaryAdministrator(),
                                new PersonNode(ra.getPerson().getId(), ra.getPerson().getName(),
                                        ra.getPerson().getPersonType(), ra.getPerson().getContact())),
                                Collectors.toList())));
        return byParent(parents, byRack);
    }

    private static List<UUID> ids(List<RackNode> parents) {
        return parents.stream().map(RackNode::id).toList();
    }

    /** One entry per parent, in the parents' order; parents without children get an empty list. */
    private static <V> Map<RackNode, List<V>> byParent(List<RackNode> parents, Map<UUID, List<V>> byRack) {
        return parents.stream().collect(Collectors.toMap(Function.identity(),
                p -> byRack.getOrDefault(p.id(), List.of()), (a, b) -> a, LinkedHashMap::new));
    }
}
