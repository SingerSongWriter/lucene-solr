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
package org.apache.solr.cloud.hdfs;

import java.io.IOException;

import com.carrotsearch.randomizedtesting.annotations.Nightly;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope.Scope;

import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.lucene.util.LuceneTestCase.Slow;
import org.apache.solr.cloud.ChaosMonkeyNothingIsSafeTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

@Slow
@Nightly
@ThreadLeakScope(Scope.NONE) // hdfs client currently leaks thread(s)
public class HdfsChaosMonkeyNothingIsSafeTest extends ChaosMonkeyNothingIsSafeTest {
  private static MiniDFSCluster dfsCluster;
  
  @BeforeClass
  public static void setupClass() throws Exception {
    dfsCluster = HdfsTestUtil.setupClass(createTempDir().getAbsolutePath());
  }
  
  @AfterClass
  public static void teardownClass() throws Exception {
    HdfsTestUtil.teardownClass(dfsCluster);
    dfsCluster = null;
  }
  
  @Before
  public void setUp() throws Exception {
    super.setUp();
    
    // super class may hard code directory
    useFactory("org.apache.solr.core.HdfsDirectoryFactory");
  }

  
  @Override
  protected String getDataDir(String dataDir) throws IOException {
    return HdfsTestUtil.getDataDir(dfsCluster, dataDir);
  }


}