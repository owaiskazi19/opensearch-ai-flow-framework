/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.flowframework.workflow;

import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.flowframework.exception.FlowFrameworkException;
import org.opensearch.ml.client.MachineLearningNodeClient;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.test.OpenSearchTestCase;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

public class RegisterAgentTests extends OpenSearchTestCase {
    private WorkflowData inputData = WorkflowData.EMPTY;
    private WorkflowData inputDataWithNoName = WorkflowData.EMPTY;

    @Mock
    MachineLearningNodeClient machineLearningNodeClient;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        MockitoAnnotations.openMocks(this);

        LLMSpec llmSpec = new LLMSpec("xyz", Collections.emptyMap());

        Map<?, ?>[] tools = new Map<?, ?>[] {
            Map.ofEntries(
                Map.entry(MLToolSpec.TOOL_TYPE_FIELD, "tool1"),
                Map.entry(MLToolSpec.TOOL_NAME_FIELD, "CatIndexTool"),
                Map.entry(MLToolSpec.DESCRIPTION_FIELD, "desc"),
                Map.entry(MLToolSpec.PARAMETERS_FIELD, "test"),
                Map.entry(MLToolSpec.INCLUDE_OUTPUT_IN_AGENT_RESPONSE, false)
            ) };

        // MLToolSpec mlToolSpec1 = new MLToolSpec("tool1", "CatIndexTool", "desc", Collections.emptyMap(), false);
        // MLToolSpec mlToolSpec2 = new MLToolSpec("tool2", "MathTool", "desc", Collections.emptyMap(), false);
        // List<MLToolSpec> tools = new ArrayList();
        // tools.add(mlToolSpec1);
        // tools.add(mlToolSpec2);

        MLMemorySpec mlMemorySpec = new MLMemorySpec("type", "memory", 2);
        inputData = new WorkflowData(
            Map.ofEntries(
                Map.entry("name", "test"),
                Map.entry("description", "description"),
                Map.entry("type", "type"),
                Map.entry("llm", llmSpec),
                Map.entry("tools", tools),
                Map.entry("parameters", Collections.emptyMap()),
                Map.entry("memory", mlMemorySpec),
                Map.entry("created_time", Instant.now()),
                Map.entry("last_updated_time", Instant.now()),
                Map.entry("app_type", "app")
            )
        );
    }

    public void testRegisterAgent() throws IOException, ExecutionException, InterruptedException {
        String agentId = "agent_id";
        RegisterAgentStep registerAgentStep = new RegisterAgentStep(machineLearningNodeClient);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<MLRegisterAgentResponse>> actionListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLRegisterAgentResponse> actionListener = invocation.getArgument(1);
            MLRegisterAgentResponse output = new MLRegisterAgentResponse(agentId);
            actionListener.onResponse(output);
            return null;
        }).when(machineLearningNodeClient).registerAgent(any(MLAgent.class), actionListenerCaptor.capture());

        CompletableFuture<WorkflowData> future = registerAgentStep.execute(List.of(inputData));

        verify(machineLearningNodeClient).registerAgent(any(MLAgent.class), actionListenerCaptor.capture());

        assertTrue(future.isDone());
        assertEquals(agentId, future.get().getContent().get("agent_id"));
    }

    public void testRegisterAgentFailure() throws IOException {
        String agentId = "agent_id";
        RegisterAgentStep registerAgentStep = new RegisterAgentStep(machineLearningNodeClient);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ActionListener<MLRegisterAgentResponse>> actionListenerCaptor = ArgumentCaptor.forClass(ActionListener.class);

        doAnswer(invocation -> {
            ActionListener<MLRegisterAgentResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new FlowFrameworkException("Failed to register the agent", RestStatus.INTERNAL_SERVER_ERROR));
            return null;
        }).when(machineLearningNodeClient).registerAgent(any(MLAgent.class), actionListenerCaptor.capture());

        CompletableFuture<WorkflowData> future = registerAgentStep.execute(List.of(inputData));

        verify(machineLearningNodeClient).registerAgent(any(MLAgent.class), actionListenerCaptor.capture());

        assertTrue(future.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get().getContent());
        assertTrue(ex.getCause() instanceof FlowFrameworkException);
        assertEquals("Failed to register the agent", ex.getCause().getMessage());
    }
}