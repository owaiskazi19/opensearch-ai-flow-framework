/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package org.opensearch.flowframework.rest;

import com.google.common.collect.ImmutableList;
import org.opensearch.flowframework.model.Template;
import org.opensearch.flowframework.transport.SearchWorkflowAction;

import static org.opensearch.flowframework.common.CommonValue.GLOBAL_CONTEXT_INDEX;
import static org.opensearch.flowframework.common.CommonValue.WORKFLOW_URI;

public class RestSearchWorkflowAction extends AbstractSearchWorkflowAction<Template> {

    private static final String SEARCH_WORKFLOW_ACTION = "search_workflow_action";
    private static final String SEARCH_WORKFLOW_PATH = WORKFLOW_URI + "/_search";

    public RestSearchWorkflowAction() {
        super(ImmutableList.of(SEARCH_WORKFLOW_PATH), GLOBAL_CONTEXT_INDEX, Template.class, SearchWorkflowAction.INSTANCE);
    }

    @Override
    public String getName() {
        return SEARCH_WORKFLOW_ACTION;
    }
}