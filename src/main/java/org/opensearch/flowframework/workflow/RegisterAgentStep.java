/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.flowframework.workflow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ExceptionsHelper;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.flowframework.exception.FlowFrameworkException;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLAgent.MLAgentBuilder;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.opensearch.flowframework.common.CommonValue.AGENT_ID;
import static org.opensearch.flowframework.common.CommonValue.APP_TYPE_FIELD;
import static org.opensearch.flowframework.common.CommonValue.CREATED_TIME;
import static org.opensearch.flowframework.common.CommonValue.DESCRIPTION_FIELD;
import static org.opensearch.flowframework.common.CommonValue.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.flowframework.common.CommonValue.LLM_FIELD;
import static org.opensearch.flowframework.common.CommonValue.MEMORY_FIELD;
import static org.opensearch.flowframework.common.CommonValue.NAME_FIELD;
import static org.opensearch.flowframework.common.CommonValue.PARAMETERS_FIELD;
import static org.opensearch.flowframework.common.CommonValue.TOOLS_FIELD;
import static org.opensearch.flowframework.common.CommonValue.TYPE;
import static org.opensearch.flowframework.util.ParseUtils.getStringToStringMap;

/**
 * Step to register an agent
 */
public class RegisterAgentStep implements WorkflowStep {

    private static final Logger logger = LogManager.getLogger(RegisterAgentStep.class);

    private MachineLearningNodeClient mlClient;

    static final String NAME = "register_agent";

    private List<MLToolSpec> mlToolSpecList;

    /**
     * Instantiate this class
     * @param mlClient client to instantiate MLClient
     */
    public RegisterAgentStep(MachineLearningNodeClient mlClient) {
        this.mlClient = mlClient;
        this.mlToolSpecList = new ArrayList<>();
    }

    @Override
    public CompletableFuture<WorkflowData> execute(List<WorkflowData> data) throws IOException {

        CompletableFuture<WorkflowData> registerAgentModelFuture = new CompletableFuture<>();

        ActionListener<MLRegisterAgentResponse> actionListener = new ActionListener<>() {
            @Override
            public void onResponse(MLRegisterAgentResponse mlRegisterAgentResponse) {
                logger.info("Remote Agent registration successful");
                registerAgentModelFuture.complete(
                    new WorkflowData(Map.ofEntries(Map.entry(AGENT_ID, mlRegisterAgentResponse.getAgentId())))
                );
            }

            @Override
            public void onFailure(Exception e) {
                logger.error("Failed to register the agent");
                registerAgentModelFuture.completeExceptionally(new FlowFrameworkException(e.getMessage(), ExceptionsHelper.status(e)));
            }
        };

        String name = null;
        String type = null;
        String description = null;
        LLMSpec llm = null;
        List<MLToolSpec> tools = new ArrayList<>();
        Map<String, String> parameters = Collections.emptyMap();
        MLMemorySpec memory = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;
        String appType = null;

        for (WorkflowData workflowData : data) {
            Map<String, Object> content = workflowData.getContent();

            for (Entry<String, Object> entry : content.entrySet()) {
                switch (entry.getKey()) {
                    case NAME_FIELD:
                        name = (String) entry.getValue();
                        break;
                    case DESCRIPTION_FIELD:
                        description = (String) entry.getValue();
                        break;
                    case TYPE:
                        type = (String) entry.getValue();
                        break;
                    case LLM_FIELD:
                        llm = getLLMSpec(entry.getValue());
                        break;
                    case TOOLS_FIELD:
                        tools = addTools(entry.getValue());
                        break;
                    case PARAMETERS_FIELD:
                        parameters = getStringToStringMap(entry.getValue(), PARAMETERS_FIELD);
                        break;
                    case MEMORY_FIELD:
                        memory = getMLMemorySpec(entry.getValue());
                        break;
                    case CREATED_TIME:
                        createdTime = (Instant) entry.getValue();
                        break;
                    case LAST_UPDATED_TIME_FIELD:
                        lastUpdateTime = (Instant) entry.getValue();
                        break;
                    case APP_TYPE_FIELD:
                        appType = (String) entry.getValue();
                        break;
                    default:
                        break;
                }
            }
        }

        if (Stream.of(name, type, llm, tools, parameters, memory, createdTime, lastUpdateTime, appType).allMatch(x -> x != null)) {
            MLAgentBuilder builder = MLAgent.builder().name(name);

            if (description != null) {
                builder.description(description);
            }

            builder.type(type)
                .llm(llm)
                .tools(tools)
                .parameters(parameters)
                .memory(memory)
                .createdTime(createdTime)
                .lastUpdateTime(lastUpdateTime)
                .appType(appType);

            MLAgent mlAgent = builder.build();

            mlClient.registerAgent(mlAgent, actionListener);

        } else {
            registerAgentModelFuture.completeExceptionally(
                new FlowFrameworkException("Required fields are not provided", RestStatus.BAD_REQUEST)
            );
        }

        return registerAgentModelFuture;
    }

    @Override
    public String getName() {
        return NAME;
    }

    private List<MLToolSpec> addTools(Object tools) {
        for (Map<?, ?> map : (Map<?, ?>[]) tools) {
            MLToolSpec mlToolSpec = (MLToolSpec) map.get(TOOLS_FIELD);
            mlToolSpecList.add(mlToolSpec);
        }
        return mlToolSpecList;
    }

    private LLMSpec getLLMSpec(Object llm) {
        // if (!(array instanceof Map[])) {
        // throw new IllegalArgumentException("[" + LLM_FIELD + "] must be an array of key-value maps.");
        // }
        if (llm instanceof LLMSpec) {
            return (LLMSpec) llm;
        }
        throw new IllegalArgumentException("[" + LLM_FIELD + "] must be of type LLMSpec.");
        // String modelId = null;
        // Map<String, String> parameters = Collections.emptyMap();
        //
        // modelId = llm.getModelId();
        // parameters = llm.getParameters();
        //// for (Map<?, ?> map : (Map<?, ?>[]) array) {
        //// modelId = (String) map.get(LLMSpec.MODEL_ID_FIELD);
        //// parameters = (Map<String, String>) map.get(LLMSpec.PARAMETERS_FIELD);
        //// }
        //
        // @SuppressWarnings("unchecked")
        // LLMSpec.LLMSpecBuilder builder = LLMSpec.builder();
        //
        // builder.modelId(modelId);
        // if (parameters != null) {
        // builder.parameters(parameters);
        // }
        // LLMSpec llmSpec = builder.build();
        // return llmSpec;
    }

    private MLMemorySpec getMLMemorySpec(Object mlMemory) {
        // if (!(array instanceof Map[])) {
        // throw new IllegalArgumentException("[" + MEMORY_FIELD + "] must be an array of key-value maps.");
        // }

        if (mlMemory instanceof MLMemorySpec) {
            return (MLMemorySpec) mlMemory;
        }
        throw new IllegalArgumentException("[" + MEMORY_FIELD + "] must be of type MLMemorySpec.");
        // String type = null;
        // String sessionId = null;
        // Integer windowSize = null;
        //
        // type = mlMemory.getType();
        // if (type == null) {
        // throw new IllegalArgumentException("agent name is null");
        // }
        // sessionId = mlMemory.getSessionId();
        // windowSize = mlMemory.getWindowSize();

        // for (Map<?, ?> map : (Map<?, ?>[]) array) {
        // type = (String) map.get(MLMemorySpec.MEMORY_TYPE_FIELD);
        // if (type == null) {
        // throw new IllegalArgumentException("agent name is null");
        // }
        // sessionId = (String) map.get(MLMemorySpec.SESSION_ID_FIELD);
        // windowSize = (Integer) map.get(MLMemorySpec.SESSION_ID_FIELD);
        // }

        // @SuppressWarnings("unchecked")
        // MLMemorySpec.MLMemorySpecBuilder builder = MLMemorySpec.builder();
        //
        // builder.type(type);
        // if (sessionId != null) {
        // builder.sessionId(sessionId);
        // }
        // if (windowSize != null) {
        // builder.windowSize(windowSize);
        // }
        //
        // MLMemorySpec mlMemorySpec = builder.build();
        // return mlMemorySpec;

    }

}