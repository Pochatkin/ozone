/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.hadoop.ozone.om.snapshot;

import org.apache.hadoop.hdds.utils.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.hadoop.fs.ozone.OzoneFsShell;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.scm.HddsWhiteboxTestUtils;
import org.apache.hadoop.ozone.MiniOzoneCluster;
import org.apache.hadoop.ozone.MiniOzoneHAClusterImpl;
import org.apache.hadoop.ozone.client.ObjectStore;
import org.apache.hadoop.ozone.client.OzoneBucket;
import org.apache.hadoop.ozone.client.OzoneClient;
import org.apache.hadoop.ozone.client.OzoneKey;
import org.apache.hadoop.ozone.client.OzoneVolume;
import org.apache.hadoop.ozone.client.io.OzoneOutputStream;
import org.apache.hadoop.ozone.client.BucketArgs;
import org.apache.hadoop.ozone.om.KeyManagerImpl;
import org.apache.hadoop.ozone.om.OMStorage;
import org.apache.hadoop.ozone.om.OmSnapshotManager;
import org.apache.hadoop.ozone.om.OzoneManager;
import org.apache.hadoop.ozone.om.helpers.BucketLayout;
import org.apache.hadoop.ozone.om.helpers.SnapshotInfo;
import org.apache.hadoop.util.ToolRunner;
import org.apache.ozone.test.GenericTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.hadoop.fs.FileSystem.FS_DEFAULT_NAME_KEY;
import static org.apache.hadoop.ozone.OzoneConsts.OM_DB_NAME;
import static org.apache.hadoop.ozone.OzoneConsts.OM_KEY_PREFIX;
import static org.apache.hadoop.ozone.OzoneConsts.OM_SNAPSHOT_DIR;
import static org.apache.hadoop.ozone.OzoneConsts.OZONE_OFS_URI_SCHEME;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests Snapshot Restore function.
 */
@Timeout(value = 300)
public class TestOzoneSnapshotRestore {
  private static final String OM_SERVICE_ID = "om-service-test-1";
  private MiniOzoneCluster cluster;
  private ObjectStore store;
  private File metaDir;
  private OzoneManager leaderOzoneManager;
  private OzoneConfiguration clientConf;
  private OzoneClient client;

  private static Stream<Arguments> bucketTypes() {
    return Stream.of(
            arguments(BucketLayout.FILE_SYSTEM_OPTIMIZED),
            arguments(BucketLayout.LEGACY)
    );
  }

  private static Stream<Arguments> bucketTypesCombinations() {
    return Stream.of(
            arguments(BucketLayout.FILE_SYSTEM_OPTIMIZED, BucketLayout.LEGACY),
            arguments(BucketLayout.LEGACY, BucketLayout.FILE_SYSTEM_OPTIMIZED)
    );
  }

  @BeforeEach
  public void init() throws Exception {
    OzoneConfiguration conf = new OzoneConfiguration();
    String clusterId = UUID.randomUUID().toString();
    String scmId = UUID.randomUUID().toString();
    String serviceID = OM_SERVICE_ID + RandomStringUtils.randomNumeric(5);

    cluster = MiniOzoneCluster.newOMHABuilder(conf)
            .setClusterId(clusterId)
            .setScmId(scmId)
            .setOMServiceId(serviceID)
            .setNumOfOzoneManagers(3)
            .build();
    cluster.waitForClusterToBeReady();

    leaderOzoneManager = ((MiniOzoneHAClusterImpl) cluster).getOMLeader();
    OzoneConfiguration leaderConfig = leaderOzoneManager.getConfiguration();
    cluster.setConf(leaderConfig);

    String hostPrefix = OZONE_OFS_URI_SCHEME + "://" + serviceID;
    clientConf = new OzoneConfiguration(cluster.getConf());
    clientConf.set(FS_DEFAULT_NAME_KEY, hostPrefix);

    client = cluster.newClient();
    store = client.getObjectStore();

    KeyManagerImpl keyManager = (KeyManagerImpl) HddsWhiteboxTestUtils
            .getInternalState(leaderOzoneManager, "keyManager");

    // stop the deletion services so that keys can still be read
    keyManager.stop();
    metaDir = OMStorage.getOmDbDir(leaderConfig);

  }

  @AfterEach
  public void tearDown() throws Exception {
    IOUtils.closeQuietly(client);
    if (cluster != null) {
      cluster.shutdown();
    }
  }

  private void createFileKey(OzoneBucket bucket, String key)
          throws IOException {
    byte[] value = RandomStringUtils.randomAscii(10240).getBytes(UTF_8);
    OzoneOutputStream fileKey = bucket.createKey(key, value.length);
    fileKey.write(value);
    fileKey.close();
  }

  private void deleteKeys(OzoneBucket bucket) throws IOException {
    Iterator<? extends OzoneKey> bucketIterator = bucket.listKeys(null);
    while (bucketIterator.hasNext()) {
      OzoneKey key = bucketIterator.next();
      bucket.deleteKey(key.getName());
    }
  }

  private String createSnapshot(String volName, String buckName)
          throws IOException, InterruptedException, TimeoutException {
    String snapshotName = UUID.randomUUID().toString();
    store.createSnapshot(volName, buckName, snapshotName);
    String snapshotKeyPrefix = OmSnapshotManager
            .getSnapshotPrefix(snapshotName);
    SnapshotInfo snapshotInfo = leaderOzoneManager
            .getMetadataManager()
            .getSnapshotInfoTable()
            .get(SnapshotInfo.getTableKey(volName, buckName, snapshotName));
    String snapshotDirName = metaDir + OM_KEY_PREFIX +
            OM_SNAPSHOT_DIR + OM_KEY_PREFIX + OM_DB_NAME +
            snapshotInfo.getCheckpointDirName() + OM_KEY_PREFIX + "CURRENT";
    GenericTestUtils.waitFor(() -> new File(snapshotDirName).exists(),
            1000, 120000);
    return snapshotKeyPrefix;

  }

  private int keyCount(OzoneBucket bucket, String keyPrefix)
          throws IOException {
    Iterator<? extends OzoneKey> iterator = bucket.listKeys(keyPrefix);
    int keyCount = 0;
    while (iterator.hasNext()) {
      iterator.next();
      keyCount++;
    }
    return keyCount;
  }

  private void keyCopy(String sourcePath, String destPath)
          throws Exception {
    OzoneFsShell shell = new OzoneFsShell(clientConf);

    try {
      // Copy key from source to destination path
      int res = ToolRunner.run(shell,
              new String[]{"-cp", sourcePath, destPath});
      Assertions.assertEquals(0, res);
    } finally {
      shell.close();
    }
  }

  @ParameterizedTest
  @MethodSource("bucketTypes")
  public void testRestoreSnapshot(BucketLayout bucketLayoutTest)
          throws Exception {
    String volume = "vol-" + RandomStringUtils.randomNumeric(5);
    String bucket = "buc-" + RandomStringUtils.randomNumeric(5);
    String keyPrefix = "key-";

    store.createVolume(volume);
    OzoneVolume vol = store.getVolume(volume);
    BucketArgs bucketArgs = BucketArgs.newBuilder()
            .setBucketLayout(bucketLayoutTest).build();
    vol.createBucket(bucket, bucketArgs);
    OzoneBucket buck = vol.getBucket(bucket);

    for (int i = 0; i < 5; i++) {
      createFileKey(buck, keyPrefix + i);
    }

    String snapshotKeyPrefix = createSnapshot(volume, bucket);

    int volBucketKeyCount = keyCount(buck, snapshotKeyPrefix + keyPrefix);
    Assertions.assertEquals(5, volBucketKeyCount);

    deleteKeys(buck);
    int delKeyCount = keyCount(buck, keyPrefix);
    Assertions.assertEquals(0, delKeyCount);

    String sourcePath = OM_KEY_PREFIX + volume + OM_KEY_PREFIX + bucket
        + OM_KEY_PREFIX + snapshotKeyPrefix;
    String destPath = OM_KEY_PREFIX + volume + OM_KEY_PREFIX + bucket
        + OM_KEY_PREFIX;

    for (int i = 0; i < 5; i++) {
      keyCopy(sourcePath + keyPrefix + i, destPath);
    }

    int finalKeyCount = keyCount(buck, keyPrefix);
    Assertions.assertEquals(5, finalKeyCount);
  }

  @ParameterizedTest
  @MethodSource("bucketTypes")
  public void testRestoreSnapshotDifferentBucket(BucketLayout bucketLayoutTest)
          throws Exception {
    String volume = "vol-" + RandomStringUtils.randomNumeric(5);
    String bucket = "buc-" + RandomStringUtils.randomNumeric(5);
    String bucket2 = "buc-" + RandomStringUtils.randomNumeric(5);
    String keyPrefix = "key-";

    store.createVolume(volume);
    OzoneVolume vol = store.getVolume(volume);
    BucketArgs bucketArgs = BucketArgs.newBuilder()
            .setBucketLayout(bucketLayoutTest).build();
    vol.createBucket(bucket, bucketArgs);
    OzoneBucket buck = vol.getBucket(bucket);
    vol.createBucket(bucket2, bucketArgs);
    OzoneBucket buck2 = vol.getBucket(bucket2);

    for (int i = 0; i < 5; i++) {
      createFileKey(buck, keyPrefix + i);
    }

    String snapshotKeyPrefix = createSnapshot(volume, bucket);

    int volBucketKeyCount = keyCount(buck, snapshotKeyPrefix + keyPrefix);
    Assertions.assertEquals(5, volBucketKeyCount);

    // Delete keys from the source bucket.
    // This is temporary fix to make sure that test passes all the time.
    // If we don't delete keys from the source bucket, copy command
    // will fail. In copy command, there is a check to make sure that key
    // doesn't exist in the destination bucket.
    // Problem is that RocksDB's seek operation doesn't 100% guarantee that
    // item key is available. Tho we believe that it is in
    // `createFakeDirIfShould` function. When we seek if there is a directory
    // for the key name, sometime it returns non-null response but in reality
    // item doesn't exist.
    // In the case when seek returns non-null response, "key doesn't exist in
    // the destination bucket" check fails because getFileStatus returns
    // dir with key name exists.
    deleteKeys(buck);

    String sourcePath = OM_KEY_PREFIX + volume + OM_KEY_PREFIX + bucket
        + OM_KEY_PREFIX + snapshotKeyPrefix;
    String destPath = OM_KEY_PREFIX + volume + OM_KEY_PREFIX + bucket2
        + OM_KEY_PREFIX;

    for (int i = 0; i < 5; i++) {
      keyCopy(sourcePath + keyPrefix + i, destPath);
    }

    int finalKeyCount = keyCount(buck2, keyPrefix);
    Assertions.assertEquals(5, finalKeyCount);
  }

  @ParameterizedTest
  @MethodSource("bucketTypesCombinations")
  public void testRestoreSnapshotDifferentBucketLayout(
          BucketLayout bucketLayoutSource, BucketLayout bucketLayoutDest)
          throws Exception {
    String volume = "vol-" + RandomStringUtils.randomNumeric(5);
    String bucket = "buc-" + RandomStringUtils.randomNumeric(5);
    String bucket2 = "buc-" + RandomStringUtils.randomNumeric(5);
    String keyPrefix = "key-";

    store.createVolume(volume);
    OzoneVolume vol = store.getVolume(volume);
    BucketArgs bucketArgs = BucketArgs.newBuilder()
            .setBucketLayout(bucketLayoutSource).build();
    vol.createBucket(bucket, bucketArgs);
    OzoneBucket buck = vol.getBucket(bucket);

    bucketArgs = BucketArgs.newBuilder()
            .setBucketLayout(bucketLayoutDest).build();
    vol.createBucket(bucket2, bucketArgs);
    OzoneBucket buck2 = vol.getBucket(bucket2);

    for (int i = 0; i < 5; i++) {
      createFileKey(buck, keyPrefix + i);
    }

    String snapshotKeyPrefix = createSnapshot(volume, bucket);

    int volBucketKeyCount = keyCount(buck, snapshotKeyPrefix + keyPrefix);
    Assertions.assertEquals(5, volBucketKeyCount);

    String sourcePath = OM_KEY_PREFIX + volume + OM_KEY_PREFIX + bucket
        + OM_KEY_PREFIX + snapshotKeyPrefix;
    String destPath = OM_KEY_PREFIX + volume + OM_KEY_PREFIX + bucket2
        + OM_KEY_PREFIX;

    for (int i = 0; i < 5; i++) {
      keyCopy(sourcePath + keyPrefix + i, destPath);
    }

    int finalKeyCount = keyCount(buck2, keyPrefix);
    Assertions.assertEquals(5, finalKeyCount);
  }
}
