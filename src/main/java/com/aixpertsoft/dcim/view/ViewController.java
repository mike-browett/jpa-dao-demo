package com.aixpertsoft.dcim.view;

import com.aixpertsoft.datastack.web.ApiException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.graphql.ExecutionGraphQlRequest;
import org.springframework.graphql.ExecutionGraphQlResponse;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.support.DefaultExecutionGraphQlRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves views over plain REST. A view is a stored GraphQL document; this controller runs it
 * in-process and returns the result as a page. UI clients never need to speak GraphQL.
 */
@RestController
@RequestMapping("/api/views")
public class ViewController {

    private final ExecutionGraphQlService graphQl;
    private final String rackOverview = load("graphql-documents/RackOverview.graphql");

    public ViewController(ExecutionGraphQlService graphQl) {
        this.graphQl = graphQl;
    }

    @GetMapping("/rack-overview")
    public Map<String, Object> rackOverview(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(required = false) String name) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("page", page);
        variables.put("size", size);
        variables.put("nameLike", name);
        Map<String, Object> data = run(rackOverview, "RackOverview", variables);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", page);
        result.put("size", size);
        result.put("total", data.get("rackCount"));
        result.put("items", data.get("racks"));
        return result;
    }

    private Map<String, Object> run(String document, String operation, Map<String, Object> variables) {
        ExecutionGraphQlRequest request = new DefaultExecutionGraphQlRequest(
                document, operation, variables, null, UUID.randomUUID().toString(), null);
        ExecutionGraphQlResponse response = graphQl.execute(request).block();
        if (response == null || !response.isValid() || !response.getErrors().isEmpty()) {
            String errors = response == null ? "no response" : String.valueOf(response.getErrors());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "viewFailed", "View " + operation + " failed: " + errors);
        }
        return response.getData();
    }

    private static String load(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read view document " + path, e);
        }
    }

}
