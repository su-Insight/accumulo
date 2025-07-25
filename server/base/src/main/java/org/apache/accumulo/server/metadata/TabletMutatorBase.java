/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.accumulo.server.metadata;

import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.dataImpl.KeyExtent;
import org.apache.accumulo.core.fate.FateTxId;
import org.apache.accumulo.core.lock.ServiceLock;
import org.apache.accumulo.core.metadata.ReferencedTabletFile;
import org.apache.accumulo.core.metadata.StoredTabletFile;
import org.apache.accumulo.core.metadata.SuspendingTServer;
import org.apache.accumulo.core.metadata.TServerInstance;
import org.apache.accumulo.core.metadata.schema.Ample;
import org.apache.accumulo.core.metadata.schema.Ample.TabletMutator;
import org.apache.accumulo.core.metadata.schema.DataFileValue;
import org.apache.accumulo.core.metadata.schema.ExternalCompactionId;
import org.apache.accumulo.core.metadata.schema.ExternalCompactionMetadata;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.BulkFileColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.CurrentLocationColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.DataFileColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.ExternalCompactionColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.FutureLocationColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.LastLocationColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.LogColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.ScanFileColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.ServerColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.SuspendLocationColumn;
import org.apache.accumulo.core.metadata.schema.MetadataSchema.TabletsSection.TabletColumnFamily;
import org.apache.accumulo.core.metadata.schema.MetadataTime;
import org.apache.accumulo.core.metadata.schema.TabletMetadata.Location;
import org.apache.accumulo.core.metadata.schema.TabletMetadata.LocationType;
import org.apache.accumulo.core.tabletserver.log.LogEntry;
import org.apache.accumulo.server.ServerContext;
import org.apache.hadoop.io.Text;

import com.google.common.base.Preconditions;

public abstract class TabletMutatorBase implements Ample.TabletMutator {

  private final ServerContext context;
  private final Mutation mutation;
  protected AutoCloseable closeAfterMutate;
  private boolean updatesEnabled = true;

  protected TabletMutatorBase(ServerContext context, KeyExtent extent) {
    this.context = context;
    mutation = new Mutation(extent.toMetaRow());
  }

  @Override
  public Ample.TabletMutator putPrevEndRow(Text per) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    TabletColumnFamily.PREV_ROW_COLUMN.put(mutation, TabletColumnFamily.encodePrevEndRow(per));
    return this;
  }

  @Override
  public Ample.TabletMutator putDirName(String dirName) {
    ServerColumnFamily.validateDirCol(dirName);
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    ServerColumnFamily.DIRECTORY_COLUMN.put(mutation, new Value(dirName));
    return this;
  }

  @Override
  public Ample.TabletMutator putFile(ReferencedTabletFile path, DataFileValue dfv) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.put(DataFileColumnFamily.NAME, path.insert().getMetadataText(),
        new Value(dfv.encode()));
    return this;
  }

  @Override
  public Ample.TabletMutator putFile(StoredTabletFile path, DataFileValue dfv) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.put(DataFileColumnFamily.NAME, path.getMetadataText(), new Value(dfv.encode()));
    return this;
  }

  @Override
  public Ample.TabletMutator deleteFile(StoredTabletFile path) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.putDelete(DataFileColumnFamily.NAME, path.getMetadataText());
    return this;
  }

  @Override
  public Ample.TabletMutator putScan(StoredTabletFile path) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.put(ScanFileColumnFamily.NAME, path.getMetadataText(), new Value());
    return this;
  }

  @Override
  public Ample.TabletMutator deleteScan(StoredTabletFile path) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.putDelete(ScanFileColumnFamily.NAME, path.getMetadataText());
    return this;
  }

  @Override
  public Ample.TabletMutator putCompactionId(long compactionId) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    ServerColumnFamily.COMPACT_COLUMN.put(mutation, new Value(Long.toString(compactionId)));
    return this;
  }

  @Override
  public Ample.TabletMutator putFlushId(long flushId) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    ServerColumnFamily.FLUSH_COLUMN.put(mutation, new Value(Long.toString(flushId)));
    return this;
  }

  @Override
  public Ample.TabletMutator putTime(MetadataTime time) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    ServerColumnFamily.TIME_COLUMN.put(mutation, new Value(time.encode()));
    return this;
  }

  private String getLocationFamily(LocationType type) {
    switch (type) {
      case CURRENT:
        return CurrentLocationColumnFamily.STR_NAME;
      case FUTURE:
        return FutureLocationColumnFamily.STR_NAME;
      case LAST:
        return LastLocationColumnFamily.STR_NAME;
      default:
        throw new IllegalArgumentException();
    }
  }

  @Override
  public Ample.TabletMutator putLocation(Location location) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.put(getLocationFamily(location.getType()), location.getSession(),
        location.getHostPort());
    return this;
  }

  @Override
  public Ample.TabletMutator deleteLocation(Location location) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.putDelete(getLocationFamily(location.getType()), location.getSession());
    return this;
  }

  @Override
  public Ample.TabletMutator putZooLock(ServiceLock zooLock) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    ServerColumnFamily.LOCK_COLUMN.put(mutation,
        new Value(zooLock.getLockID().serialize(context.getZooKeeperRoot() + "/")));
    return this;
  }

  @Override
  public Ample.TabletMutator putWal(LogEntry logEntry) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    logEntry.addToMutation(mutation);
    return this;
  }

  @Override
  public Ample.TabletMutator deleteWal(LogEntry logEntry) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.putDelete(LogColumnFamily.NAME, logEntry.getColumnQualifier());
    return this;
  }

  @Override
  public Ample.TabletMutator deleteWal(String wal) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.putDelete(LogColumnFamily.STR_NAME, wal);
    return this;
  }

  @Override
  public Ample.TabletMutator putBulkFile(ReferencedTabletFile bulkref, long tid) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.put(BulkFileColumnFamily.NAME, bulkref.insert().getMetadataText(),
        new Value(FateTxId.formatTid(tid)));
    return this;
  }

  @Override
  public Ample.TabletMutator deleteBulkFile(StoredTabletFile bulkref) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.putDelete(BulkFileColumnFamily.NAME, bulkref.getMetadataText());
    return this;
  }

  @Override
  public Ample.TabletMutator putSuspension(TServerInstance tServer, long suspensionTime) {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.put(SuspendLocationColumn.SUSPEND_COLUMN.getColumnFamily(),
        SuspendLocationColumn.SUSPEND_COLUMN.getColumnQualifier(),
        SuspendingTServer.toValue(tServer, suspensionTime));
    return this;
  }

  @Override
  public Ample.TabletMutator deleteSuspension() {
    Preconditions.checkState(updatesEnabled, "Cannot make updates after calling mutate.");
    mutation.putDelete(SuspendLocationColumn.SUSPEND_COLUMN.getColumnFamily(),
        SuspendLocationColumn.SUSPEND_COLUMN.getColumnQualifier());
    return this;
  }

  @Override
  public TabletMutator putExternalCompaction(ExternalCompactionId ecid,
      ExternalCompactionMetadata ecMeta) {
    mutation.put(ExternalCompactionColumnFamily.STR_NAME, ecid.canonical(), ecMeta.toJson());
    return this;
  }

  @Override
  public TabletMutator deleteExternalCompaction(ExternalCompactionId ecid) {
    mutation.putDelete(ExternalCompactionColumnFamily.STR_NAME, ecid.canonical());
    return this;
  }

  protected Mutation getMutation() {
    updatesEnabled = false;
    return mutation;
  }

  public void setCloseAfterMutate(AutoCloseable closeable) {
    this.closeAfterMutate = closeable;
  }
}
