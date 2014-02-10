package org.apache.solr.handler.component;
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

import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.sentry.SentryTestBase;
import org.apache.solr.sentry.SentryIndexAuthorizationSingleton;
import org.apache.solr.sentry.SentrySingletonTestInstance;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test for QueryIndexAuthorizationComponent
 */
public class QueryDocAuthorizationComponentTest extends SentryTestBase {
  private static SolrCore core;
  private static SentryIndexAuthorizationSingleton sentryInstance;

  @BeforeClass
  public static void beforeClass() throws Exception {
    core = createCore("solrconfig.xml", "schema-minimal.xml");
    // store the CloudDescriptor, because we will overwrite it with a mock
    // and restore it later
    sentryInstance = SentrySingletonTestInstance.getInstance().getSentryInstance();
  }

  @AfterClass
  public static void afterClass() throws Exception {
    closeCore(core, null);
    core = null;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp(core);
  }

  private ResponseBuilder getResponseBuilder() {
    SolrQueryRequest request = getRequest();
    return new ResponseBuilder(request, null, null);
  }

  private ResponseBuilder runComponent(String user, NamedList args, SolrParams params)
  throws Exception {
    ResponseBuilder builder = getResponseBuilder();
    prepareCollAndUser(core, builder.req, "collection1", user);

    if (params != null) {
      builder.req.setParams(params);
    }

    QueryDocAuthorizationComponent component =
      new QueryDocAuthorizationComponent(sentryInstance);
    component.init(args);
    component.prepare(builder);
    return builder;
  }

  private void checkParams(String[] expected, ResponseBuilder builder) {
    final String fieldName = "fq";
    final String [] params = builder.req.getParams().getParams(fieldName);
    if (expected == null) {
      assertEquals(null, params);
    } else {
      assertNotNull(params);
      assertEquals(expected.length, params.length);
      for (int i = 0; i < expected.length; ++i) {
        assertEquals(expected[ i ], params[ i ]);
      }
    }
  }

  @Test
  public void testSimple() throws Exception {
    ResponseBuilder builder = runComponent("junit", new NamedList(), null);

    String expect = QueryDocAuthorizationComponent.DEFAULT_AUTH_FIELD + ":(junit)";
    checkParams(new String[] {expect}, builder);
  }

  @Test
  public void testAuthFieldNonDefault() throws Exception {
    String authField = "nonDefaultAuthField";
    NamedList args = new NamedList();
    args.add(QueryDocAuthorizationComponent.AUTH_FIELD_PROP, authField);
    ResponseBuilder builder = runComponent("junit", args, null);

    String expect = authField + ":(junit)";
    checkParams(new String[] {expect}, builder);
  }

  @Test
  public void testSuperUser() throws Exception {
    String superUser = (System.getProperty("solr.authorization.superuser", "solr"));
    ResponseBuilder builder = runComponent(superUser, new NamedList(), null);
    prepareCollAndUser(core, builder.req, "collection1", superUser);

    checkParams(null, builder);
  }

  @Test
  public void testExistingFilterQuery() throws Exception {
    ModifiableSolrParams newParams = new ModifiableSolrParams();
    String existingFq = "bogusField:(bogusUser)";
    newParams.add("fq", existingFq);
    ResponseBuilder builder = runComponent("junit", new NamedList(), newParams);

    String expect = QueryDocAuthorizationComponent.DEFAULT_AUTH_FIELD + ":(junit)";
    checkParams(new String[] {existingFq, expect} , builder);
  }

  @Test
  public void testEmptyGroup() throws Exception {
    ResponseBuilder builder = runComponent("bogusUser", new NamedList(), null);

    checkParams(null, builder);
  }

  @Test
  public void testMultipleGroup() throws Exception {
    ResponseBuilder builder = runComponent("multipleMemberGroup", new NamedList(), null);

    String expect = QueryDocAuthorizationComponent.DEFAULT_AUTH_FIELD + ":(user1 OR user2 OR user3)";
    checkParams(new String[] {expect}, builder);
  }
}