package com.aixpertsoft.datastack.boot;

import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api: the models this server hosts, with their REST base paths. */
@RestController
public class ModelsController {

    private final List<DatastackModel> models;

    public ModelsController(List<DatastackModel> models) {
        this.models = models.stream().sorted(Comparator.comparing(DatastackModel::id)).toList();
    }

    @GetMapping("/api")
    public List<DatastackModel> models() {
        return models;
    }
}
