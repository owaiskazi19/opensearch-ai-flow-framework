/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.flowframework.workflow;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.flowframework.exception.FlowFrameworkException;
import org.opensearch.ml.common.agent.MLToolSpec;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import static org.opensearch.flowframework.common.CommonValue.*;
import static org.opensearch.flowframework.util.ParseUtils.getStringToStringMap;

public class ToolStep implements WorkflowStep {

    CompletableFuture<WorkflowData> toolFuture = new CompletableFuture<>();
    static final String NAME = "tool";

    @Override
    public CompletableFuture<WorkflowData> execute(List<WorkflowData> data) throws IOException {
        String type = null;
        String name = null;
        String description = null;
        Map<String, String> parameters = Collections.emptyMap();
        Boolean includeOutputInAgentResponse = null;

        for (WorkflowData workflowData : data) {
            Map<String, Object> content = workflowData.getContent();

            for (Entry<String, Object> entry : content.entrySet()) {
                switch (entry.getKey()) {
                    case TYPE:
                        type = (String) content.get(TYPE);
                        break;
                    case NAME_FIELD:
                        name = (String) content.get(NAME_FIELD);
                        break;
                    case DESCRIPTION_FIELD:
                        description = (String) content.get(DESCRIPTION_FIELD);
                        break;
                    case PARAMETERS_FIELD:
                        parameters = getStringToStringMap(content.get(PARAMETERS_FIELD), PARAMETERS_FIELD);
                        break;
                    case INCLUDE_OUTPUT_IN_AGENT_RESPONSE:
                        includeOutputInAgentResponse = (Boolean) content.get(INCLUDE_OUTPUT_IN_AGENT_RESPONSE);
                        break;
                    default:
                        break;
                }

            }

        }

        if (type == null) {
            toolFuture.completeExceptionally(new FlowFrameworkException("Tool type is not provided", RestStatus.BAD_REQUEST));
        } else {
            MLToolSpec.MLToolSpecBuilder builder = MLToolSpec.builder();

            builder.type(type);
            if (name != null) {
                builder.name(name);
            }
            if (description != null) {
                builder.description(description);
            }
            if (parameters != null) {
                builder.parameters(parameters);
            }
            if (includeOutputInAgentResponse != null) {
                builder.includeOutputInAgentResponse(includeOutputInAgentResponse);
            }

            MLToolSpec mlToolSpec = builder.build();
            toolFuture.complete(new WorkflowData(Map.ofEntries(Map.entry(TOOLS_FIELD, mlToolSpec))));
        }

        return toolFuture;
    }

    @Override
    public String getName() {
        return NAME;
    }
}