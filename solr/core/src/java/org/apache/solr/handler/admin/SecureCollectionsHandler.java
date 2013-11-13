package org.apache.solr.handler.admin;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.EnumSet;
import org.apache.sentry.core.model.search.SearchModelAction;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.core.CoreContainer;

/**
 * Secure (sentry-aware) version of CollectionsHandler
 */
public class SecureCollectionsHandler extends CollectionsHandler {

  public SecureCollectionsHandler() {
    super();
  }

  public SecureCollectionsHandler(final CoreContainer coreContainer) {
    super(coreContainer);
  }

  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
    // all actions require UPDATE privileges, so we don't have to do
    // special handling for each action
    SecureHandler.checkSentry(req, SecureHandler.UPDATE_ONLY);
    super.handleRequestBody(req, rsp);
  }
}
