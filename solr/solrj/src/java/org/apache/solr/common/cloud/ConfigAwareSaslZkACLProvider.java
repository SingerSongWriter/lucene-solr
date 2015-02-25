package org.apache.solr.common.cloud;

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

import java.util.List;

import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.ACL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A SASL-enabled ZkACLProvider that does the following:
 * 1) For all non-config nodes, uses the SaslZkACLProvider, i.e.
 * gives all permissions for the user specified in System property
 * "solr.authorization.superuser" (default: "solr") when using sasl,
 * and gives read permissions for anyone else.
 * 2) For config nodes, if solr.authorization.zk.protectConfigNodes is
 * set to true, gives the same permissions as 1) to the config nodes.
 * Otherwise, the config nodes are open.
 *
 * Designed for a setup where configurations need to be modified but config
 * API calls are not present.
 */
public class ConfigAwareSaslZkACLProvider implements ZkACLProvider {
  private static final Logger LOG = LoggerFactory
      .getLogger(ConfigAwareSaslZkACLProvider.class);

  private static String superUser = System.getProperty("solr.authorization.superuser", "solr");
  private boolean protectConfigNodes;
  private SaslZkACLProvider saslProvider = new SaslZkACLProvider();

  public ConfigAwareSaslZkACLProvider() {
    String protectConfigNodesProp = System.getProperty("solr.authorization.zk.protectConfigNodes", "false");
    protectConfigNodes = "true".equalsIgnoreCase(protectConfigNodesProp);
  }

  @Override
  public List<ACL> getACLsToAdd(String zNodePath) {
    if (isConfigPath(zNodePath) && !protectConfigNodes) {
      return ZooDefs.Ids.OPEN_ACL_UNSAFE;
    } else {
      return saslProvider.getACLsToAdd(zNodePath);
    }
  }

  private boolean isConfigPath(String zNodePath) {
    if (zNodePath != null && (zNodePath.startsWith("/configs/") || zNodePath.equals("/configs"))) {
      return true;
    }
    return false;
  }
}
