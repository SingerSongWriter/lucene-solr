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

package org.apache.solr.servlet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.security.token.delegation.web.KerberosDelegationTokenAuthenticationHandler;
import org.apache.hadoop.security.authentication.server.PseudoAuthenticationHandler;
import org.apache.hadoop.security.token.delegation.web.PseudoDelegationTokenAuthenticationHandler;
import static org.apache.hadoop.security.token.delegation.web.DelegationTokenAuthenticationFilter.PROXYUSER_PREFIX;
import static org.apache.solr.servlet.SolrHadoopAuthenticationFilter.SOLR_PROXYUSER_PREFIX;
import static org.apache.solr.servlet.SolrHadoopAuthenticationFilter.SOLR_PREFIX;
import org.apache.solr.SolrTestCaseJ4;

import java.util.HashMap;
import java.util.Properties;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SolrHadoopAuthenticationFilterTest extends SolrTestCaseJ4 {
  private static SolrHadoopAuthenticationFilter filter;

  @BeforeClass
  public static void beforeClass() throws Exception {
    System.setProperty("zkHost", "127.0.0.1:2180/solr");
    filter = new SolrHadoopAuthenticationFilter();
    filter.init(null);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    System.clearProperty("zkHost");
  }

  @Test
  public void testDefaults() throws Exception {
    Properties props = filter.getConfiguration(null, null);
    assertEquals(props.getProperty(SolrHadoopAuthenticationFilter.AUTH_TYPE),
      PseudoDelegationTokenAuthenticationHandler.class.getName());
    assertEquals("true", props.getProperty(PseudoAuthenticationHandler.ANONYMOUS_ALLOWED));
    assertEquals("36000", props.getProperty("token.validity"));
    assertEquals("zookeeper", props.getProperty("signer.secret.provider"));
    assertEquals("/token", props.getProperty("signer.secret.provider.zookeeper.path"));
  }

  @Test
  public void testOverrideDefaults() throws Exception {
    Map<String, String> map = new HashMap<String, String>();
    map.put(SOLR_PREFIX + SolrHadoopAuthenticationFilter.AUTH_TYPE, "otherAuthType");
    map.put(SOLR_PREFIX + PseudoAuthenticationHandler.ANONYMOUS_ALLOWED, "false");
    map.put(SOLR_PREFIX + "token.validity", "1234");
    map.put(SOLR_PREFIX + "signer.secret.provider", "notzookeeper");
    map.put(SOLR_PREFIX + "signer.secret.provider.zookeeper.path", "/notsolr/tokens");
    for (Map.Entry<String, String> entry : map.entrySet()) {
      System.setProperty(entry.getKey(), entry.getValue());
    }
    Properties props = filter.getConfiguration(null, null);

    for (Map.Entry<String, String> entry : map.entrySet()) {
      if (entry.getKey().startsWith(SOLR_PROXYUSER_PREFIX)) {
        String newKey = PROXYUSER_PREFIX + "."
          + entry.getKey().substring(SOLR_PROXYUSER_PREFIX.length());
        assertEquals(entry.getValue(), props.get(newKey));
      }
      System.clearProperty(entry.getKey());
    }
  }

  @Test
  public void testKerberos() throws Exception {
    String kerberos = "kerberos";
    String authType = filter.SOLR_PREFIX + filter.AUTH_TYPE;
    System.setProperty(authType, kerberos);
    Properties props = filter.getConfiguration(null, null);
    assertEquals(KerberosDelegationTokenAuthenticationHandler.class.getName(),
      props.getProperty(SolrHadoopAuthenticationFilter.AUTH_TYPE));
    System.clearProperty(authType);
  }

  @Test
  public void testGetProxyuserConfiguration() throws Exception {
    final String superUserProp = "solr.authorization.superuser";
    String superUserVal = System.getProperty(superUserProp);
    System.clearProperty(superUserProp);
    Map<String, String> map = new HashMap<String, String>();
    map.put(SOLR_PROXYUSER_PREFIX + "hue.hosts", "*");
    map.put(SOLR_PROXYUSER_PREFIX + "hue.groups", "value");
    map.put(SOLR_PROXYUSER_PREFIX + "hue.somethingElse", "somethingElse");
    map.put(SOLR_PROXYUSER_PREFIX, "justPrefix");
    map.put(PROXYUSER_PREFIX + "oldPrefix.hosts", "solr");
    for (Map.Entry<String, String> entry : map.entrySet()) {
      System.setProperty(entry.getKey(), entry.getValue());
    }

    Configuration conf = filter.getProxyuserConfiguration(null);
    // PROXYUSER_PREFIX should not be conf, but two superUser entries
    // (hosts + groups) should be
    assertEquals(map.size() + 1, conf.size());

    assertEquals(conf.get(PROXYUSER_PREFIX + ".solr.groups"), "*");
    assertEquals(conf.get(PROXYUSER_PREFIX + ".solr.hosts"), "*");
    for (Map.Entry<String, String> entry : map.entrySet()) {
      if (entry.getKey().startsWith(SOLR_PROXYUSER_PREFIX)) {
        String newKey = PROXYUSER_PREFIX + "."
          + entry.getKey().substring(SOLR_PROXYUSER_PREFIX.length());
        assertEquals(entry.getValue(), conf.get(newKey));
      }
      System.clearProperty(entry.getKey());
    }

    // restore superUser prop
    if (superUserVal != null) System.setProperty(superUserProp, superUserVal);
  }
}
