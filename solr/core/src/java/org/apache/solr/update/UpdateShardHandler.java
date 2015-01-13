package org.apache.solr.update;

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

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.util.ExecutorUtil;
import org.apache.solr.core.ConfigSolr;
import org.apache.solr.util.DefaultSolrThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateShardHandler {
  
  private static Logger log = LoggerFactory.getLogger(UpdateShardHandler.class);
  
  private ThreadPoolExecutor cmdDistribExecutor = new ThreadPoolExecutor(0,
      Integer.MAX_VALUE, 5, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
      new DefaultSolrThreadFactory("cmdDistribExecutor"));
  
  private PoolingClientConnectionManager clientConnectionManager;

  private final HttpClient client;

  public UpdateShardHandler(ConfigSolr cfg) {
    clientConnectionManager = new PoolingClientConnectionManager();
    if (cfg != null) {
      clientConnectionManager.setMaxTotal(cfg.getMaxUpdateConnections());
      clientConnectionManager.setDefaultMaxPerRoute(cfg.getMaxUpdateConnectionsPerHost());
    }

    ModifiableSolrParams params = new ModifiableSolrParams();
    if (cfg != null) {
      params.set(HttpClientUtil.PROP_SO_TIMEOUT,
          cfg.getDistributedSocketTimeout());
      params.set(HttpClientUtil.PROP_CONNECTION_TIMEOUT,
          cfg.getDistributedConnectionTimeout());
    }
    // in the update case, we want to do retries, and to use
    // the default Solr retry handler that createClient will 
    // give us
    params.set(HttpClientUtil.PROP_USE_RETRY, true);
    client = HttpClientUtil.createClient(params, clientConnectionManager);
  }
  
  
  public HttpClient getHttpClient() {
    return client;
  }
  
  public ThreadPoolExecutor getCmdDistribExecutor() {
    return cmdDistribExecutor;
  }

  public void close() {
    try {
      ExecutorUtil.shutdownNowAndAwaitTermination(cmdDistribExecutor);
    } catch (Exception e) {
      SolrException.log(log, e);
    } finally {
      clientConnectionManager.shutdown();
    }
  }
}
