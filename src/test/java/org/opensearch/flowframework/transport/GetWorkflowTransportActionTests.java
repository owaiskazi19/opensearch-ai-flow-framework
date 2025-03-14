/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.flowframework.transport;

import org.opensearch.Version;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.flowframework.TestHelpers;
import org.opensearch.flowframework.common.FlowFrameworkSettings;
import org.opensearch.flowframework.indices.FlowFrameworkIndicesHandler;
import org.opensearch.flowframework.model.Template;
import org.opensearch.flowframework.model.Workflow;
import org.opensearch.flowframework.model.WorkflowEdge;
import org.opensearch.flowframework.model.WorkflowNode;
import org.opensearch.flowframework.util.EncryptorUtils;
import org.opensearch.index.get.GetResult;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.mockito.ArgumentCaptor;

import static org.opensearch.flowframework.common.CommonValue.GLOBAL_CONTEXT_INDEX;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GetWorkflowTransportActionTests extends OpenSearchTestCase {

    private Client client;
    private SdkClient sdkClient;
    private NamedXContentRegistry xContentRegistry;
    private GetWorkflowTransportAction getTemplateTransportAction;
    private FlowFrameworkIndicesHandler flowFrameworkIndicesHandler;
    private FlowFrameworkSettings flowFrameworkSettings;
    private Template template;
    private EncryptorUtils encryptorUtils;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        this.client = mock(Client.class);
        this.xContentRegistry = mock(NamedXContentRegistry.class);
        this.flowFrameworkSettings = mock(FlowFrameworkSettings.class);
        this.sdkClient = SdkClientFactory.createSdkClient(client, xContentRegistry, Collections.emptyMap());
        this.encryptorUtils = new EncryptorUtils(mock(ClusterService.class), client, sdkClient, xContentRegistry);
        ClusterService clusterService = mock(ClusterService.class);
        ClusterSettings clusterSettings = new ClusterSettings(
            Settings.EMPTY,
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(FlowFrameworkSettings.FILTER_BY_BACKEND_ROLES)))
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        this.flowFrameworkIndicesHandler = spy(
            new FlowFrameworkIndicesHandler(client, sdkClient, clusterService, encryptorUtils, xContentRegistry)
        );

        this.getTemplateTransportAction = new GetWorkflowTransportAction(
            mock(TransportService.class),
            mock(ActionFilters.class),
            flowFrameworkIndicesHandler,
            flowFrameworkSettings,
            client,
            sdkClient,
            encryptorUtils,
            clusterService,
            xContentRegistry,
            Settings.EMPTY
        );

        Version templateVersion = Version.fromString("1.0.0");
        List<Version> compatibilityVersions = List.of(Version.fromString("2.0.0"), Version.fromString("3.0.0"));
        WorkflowNode nodeA = new WorkflowNode("A", "a-type", Collections.emptyMap(), Map.of("foo", "bar"));
        WorkflowNode nodeB = new WorkflowNode("B", "b-type", Collections.emptyMap(), Map.of("baz", "qux"));
        WorkflowEdge edgeAB = new WorkflowEdge("A", "B");
        List<WorkflowNode> nodes = List.of(nodeA, nodeB);
        List<WorkflowEdge> edges = List.of(edgeAB);
        Workflow workflow = new Workflow(Map.of("key", "value"), nodes, edges);

        this.template = new Template(
            "test",
            "description",
            "use case",
            templateVersion,
            compatibilityVersions,
            Map.of("provision", workflow),
            Collections.emptyMap(),
            TestHelpers.randomUser(),
            null,
            null,
            null,
            null
        );

        ThreadPool clientThreadPool = mock(ThreadPool.class);
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);

        when(client.threadPool()).thenReturn(clientThreadPool);
        when(clientThreadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testGetWorkflowNoGlobalContext() {

        doReturn(false).when(flowFrameworkIndicesHandler).doesIndexExist(anyString());
        @SuppressWarnings("unchecked")
        ActionListener<GetWorkflowResponse> listener = mock(ActionListener.class);
        WorkflowRequest workflowRequest = new WorkflowRequest("1", null);
        getTemplateTransportAction.doExecute(mock(Task.class), workflowRequest, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener, times(1)).onFailure(exceptionCaptor.capture());
        assertTrue(exceptionCaptor.getValue().getMessage().contains("There are no templates in the global_context"));
    }

    public void testGetWorkflowSuccess() {
        String workflowId = "12345";
        @SuppressWarnings("unchecked")
        ActionListener<GetWorkflowResponse> listener = mock(ActionListener.class);
        WorkflowRequest workflowRequest = new WorkflowRequest(workflowId, null);

        doReturn(true).when(flowFrameworkIndicesHandler).doesIndexExist(anyString());

        // Stub client.get to force on response
        doAnswer(invocation -> {
            ActionListener<GetResponse> responseListener = invocation.getArgument(1);

            XContentBuilder builder = XContentFactory.jsonBuilder();
            this.template.toXContent(builder, null);
            BytesReference templateBytesRef = BytesReference.bytes(builder);
            GetResult getResult = new GetResult(GLOBAL_CONTEXT_INDEX, workflowId, 1, 1, 1, true, templateBytesRef, null, null);
            responseListener.onResponse(new GetResponse(getResult));
            return null;
        }).when(client).get(any(GetRequest.class), any());

        getTemplateTransportAction.doExecute(mock(Task.class), workflowRequest, listener);

        ArgumentCaptor<GetWorkflowResponse> templateCaptor = ArgumentCaptor.forClass(GetWorkflowResponse.class);
        verify(listener, times(1)).onResponse(templateCaptor.capture());
        assertEquals(this.template.name(), templateCaptor.getValue().getTemplate().name());
    }

    public void testGetWorkflowFailure() {
        String workflowId = "12345";
        @SuppressWarnings("unchecked")
        ActionListener<GetWorkflowResponse> listener = mock(ActionListener.class);
        WorkflowRequest workflowRequest = new WorkflowRequest(workflowId, null);

        doReturn(true).when(flowFrameworkIndicesHandler).doesIndexExist(anyString());

        // Stub client.get to force on failure
        doAnswer(invocation -> {
            ActionListener<GetResponse> responseListener = invocation.getArgument(1);
            responseListener.onFailure(new Exception("failed"));
            return null;
        }).when(client).get(any(GetRequest.class), any());

        getTemplateTransportAction.doExecute(mock(Task.class), workflowRequest, listener);

        ArgumentCaptor<Exception> exceptionCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener, times(1)).onFailure(exceptionCaptor.capture());
        assertEquals("Failed to retrieve template (12345) from global context.", exceptionCaptor.getValue().getMessage());
    }
}
