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
package org.apache.accumulo.core.metrics;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Prior to 2.1.0 Accumulo used the <a href=
 * "https://hadoop.apache.org/docs/current/api/org/apache/hadoop/metrics2/package-summary.html">Hadoop
 * Metrics2</a> framework. In 2.1.0 Accumulo migrated away from the Metrics2 framework to
 * <a href="https://micrometer.io/">Micrometer</a>. Micrometer suggests using a particular
 * <a href="https://micrometer.io/docs/concepts#_naming_meters">naming convention</a> for the
 * metrics. The table below contains a mapping of the old to new metric names.
 * <table border="1">
 * <caption>Summary of Metric Changes</caption>
 * <tr>
 * <th>Old Name</th>
 * <th>Hadoop Metrics2 Type</th>
 * <th>New Name</th>
 * <th>Micrometer Type</th>
 * <th>Notes</th>
 * </tr>
 * <!-- general server metrics -->
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_SERVER_IDLE}</td>
 * <td>Gauge</td>
 * <td>Indicates if the server is idle or not. The value will be 1 when idle and 0 when not idle.
 * <!-- compactor -->
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_LOW_MEMORY}</td>
 * <td>Gauge</td>
 * <td>reports 1 when process memory usage is above threshold, 0 when memory is okay</td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_COMPACTOR_MAJC_STUCK}</td>
 * <td>LongTaskTimer</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_COMPACTOR_JOB_PRIORITY_QUEUES}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_LENGTH}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_JOBS_PRIORITY}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_JOBS_QUEUED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_JOBS_DEQUEUED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_JOBS_REJECTED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_COMPACTOR_ENTRIES_READ}</td>
 * <td>FunctionCounter</td>
 * <td>Number of entries read by all threads performing compactions</td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_COMPACTOR_ENTRIES_WRITTEN}</td>
 * <td>FunctionCounter</td>
 * <td>Number of entries written by all threads performing compactions</td>
 * </tr>
 * <!-- fate -->
 * <tr>
 * <td>currentFateOps</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_OPS}</td>
 * <td>Gauge</td>
 * <td>Was previously named "accumulo.fate.ops.in.progress". Changed to better reflect what the
 * gauge is actually tracking which is all the current fate ops in any state.</td>
 * </tr>
 * <tr>
 * <td>FateTxOpType_{name}</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_TYPE_IN_PROGRESS}</td>
 * <td>Gauge</td>
 * <td>Previously there was a metric per operation type with the count of in-progress transactions
 * of that type. Now there is one metric and the type is in the tag op.type</td>
 * </tr>
 * <tr>
 * <td>totalFateOps</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_OPS_ACTIVITY}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>totalZkConnErrors</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_ERRORS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>FateTxState_NEW</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_TX}</td>
 * <td>Gauge</td>
 * <td>The state is now in a tag: state=new</td>
 * </tr>
 * <tr>
 * <td>FateTxState_IN_PROGRESS</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_TX}</td>
 * <td>Gauge</td>
 * <td>The state is now in a tag: state=in.progress</td>
 * </tr>
 * <tr>
 * <td>FateTxState_FAILED_IN_PROGRESS</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_TX}</td>
 * <td>Gauge</td>
 * <td>The state is now in a tag: state=failed.in.progress</td>
 * </tr>
 * <tr>
 * <td>FateTxState_FAILED</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_TX}</td>
 * <td>Gauge</td>
 * <td>The state is now in a tag: state=failed</td>
 * </tr>
 * <tr>
 * <td>FateTxState_SUCCESSFUL</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_TX}</td>
 * <td>Gauge</td>
 * <td>The state is now in a tag: state=successful</td>
 * </tr>
 * <tr>
 * <td>FateTxState_UNKNOWN</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_FATE_TX}</td>
 * <td>Gauge</td>
 * <td>The state is now in a tag: state=unknown</td>
 * </tr>
 * <!-- garbage collection -->
 * <tr>
 * <td>AccGcStarted</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_STARTED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcFinished</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_FINISHED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcCandidates</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_CANDIDATES}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcInUse</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_IN_USE}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcDeleted</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_DELETED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcErrors</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_ERRORS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcWalStarted</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_WAL_STARTED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcWalFinished</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_WAL_FINISHED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcWalCandidates</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_WAL_CANDIDATES}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcWalInUse</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_WAL_IN_USE}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcWalDeleted</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_WAL_DELETED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcWalErrors</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_WAL_ERRORS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcPosOpDuration</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_POST_OP_DURATION}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>AccGcRunCycleCount</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_GC_RUN_CYCLE}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <!-- tablet server -->
 * <tr>
 * <td>entries</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_ENTRIES}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>entriesInMem</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_MEM_ENTRIES}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>activeMinCs</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_MINC_RUNNING}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>queuedMinCs</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_MINC_QUEUED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>totalMinCs</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_MINC_TOTAL}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>onlineTablets</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_TABLETS_ONLINE}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td></td>
 * <td></td>
 * <td>{@link #METRICS_TSERVER_TABLETS_ONLINE_ONDEMAND}</td>
 * <td>Gauge</td>
 * <td>Represents the number of on-demand tablets that are online</td>
 * </tr>
 * <tr>
 * <td></td>
 * <td></td>
 * <td>{@link #METRICS_TSERVER_TABLETS_ONDEMAND_UNLOADED_FOR_MEM}</td>
 * <td>Gauge</td>
 * <td>Represents the number of on-demand tablets that were unloaded due to low memory
 * condition</td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_TSERVER_TABLETS_LONG_ASSIGNMENTS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>openingTablets</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_TABLETS_OPENING}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>unopenedTablets</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_TABLETS_UNOPENED}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>filesPerTablet</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_TABLETS_FILES}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>ingestRate</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_INGEST_MUTATIONS}</td>
 * <td>Gauge</td>
 * <td>Prior to 2.1.0 this metric was reported as a rate, it is now the count and the rate can be
 * derived</td>
 * </tr>
 * <tr>
 * <td>ingestByteRate</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_INGEST_BYTES}</td>
 * <td>Gauge</td>
 * <td>Prior to 2.1.0 this metric was reported as a rate, it is now the count and the rate can be
 * derived</td>
 * </tr>
 * <tr>
 * <td>holdTime</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_TSERVER_HOLD}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <!-- scan server -->
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_SCAN_RESERVATION_TOTAL_TIMER}</td>
 * <td>Timer</td>
 * <td>Time to reserve a tablets files for scan</td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_SCAN_BUSY_TIMEOUT_COUNTER}</td>
 * <td>Counter</td>
 * <td>Count of the scans where a busy timeout happened</td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_SCAN_TABLET_METADATA_CACHE}</td>
 * <td>Cache</td>
 * <td>scan server tablet cache metrics</td>
 * </tr>
 * <!-- scans -->
 * <tr>
 * <td>scan</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_SCAN_TIMES}</td>
 * <td>Timer</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_SCAN_OPEN_FILES}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>result</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_SCAN_RESULTS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>yield</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_SCAN_YIELDS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_SCAN_START}</td>
 * <td>Counter</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_SCAN_CONTINUE}</td>
 * <td>Counter</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value #METRICS_SCAN_CLOSE}</td>
 * <td>Counter</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>queries</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_SCAN_QUERIES}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>scannedRate</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_SCAN_SCANNED_ENTRIES}</td>
 * <td>Gauge</td>
 * <td>Prior to 2.1.0 this metric was reported as a rate, it is now the count and the rate can be
 * derived</td>
 * </tr>
 * <tr>
 * <td>queryRate</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_SCAN_QUERY_SCAN_RESULTS}</td>
 * <td>Gauge</td>
 * <td>Prior to 2.1.0 this metric was reported as a rate, it is now the count and the rate can be
 * derived</td>
 * </tr>
 * <tr>
 * <td>queryByteRate</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_SCAN_QUERY_SCAN_RESULTS_BYTES}</td>
 * <td>Gauge</td>
 * <td>Prior to 2.1.0 this metric was reported as a rate, it is now the count and the rate can be
 * derived</td>
 * </tr>
 * <!-- major compactions -->
 * <tr>
 * <td>{i|e}_{compactionServiceName}_{executor_name}_queued</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_MAJC_QUEUED}</td>
 * <td>Gauge</td>
 * <td>The compaction service information is in a tag:
 * id={i|e}_{compactionServiceName}_{executor_name}</td>
 * </tr>
 * <tr>
 * <td>{i|e}_{compactionServiceName}_{executor_name}_running</td>
 * <td>Gauge</td>
 * <td>{@value #METRICS_MAJC_RUNNING}</td>
 * <td>Gauge</td>
 * <td>The compaction service information is in a tag:
 * id={i|e}_{compactionServiceName}_{executor_name}</td>
 * </tr>
 * <tr>
 * <td></td>
 * <td></td>
 * <td>{@link #METRICS_MAJC_PAUSED}</td>
 * <td>Counter</td>
 * <td></td>
 * </tr>
 * <!-- minor compactions -->
 * <tr>
 * <td>Queue</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_MINC_QUEUED}</td>
 * <td>Timer</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>Minc</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_MINC_RUNNING}</td>
 * <td>Timer</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td></td>
 * <td></td>
 * <td>{@value #METRICS_MINC_PAUSED}</td>
 * <td>Counter</td>
 * <td></td>
 * </tr>
 * <!-- Updates (ingest) -->
 * <tr>
 * <td>permissionErrors</td>
 * <td>Counter</td>
 * <td>{@value #METRICS_UPDATE_ERRORS}</td>
 * <td>Gauge</td>
 * <td>Type is stored in tag: type=permission</td>
 * </tr>
 * <tr>
 * <td>unknownTabletErrors</td>
 * <td>Counter</td>
 * <td>{@value #METRICS_UPDATE_ERRORS}</td>
 * <td>Gauge</td>
 * <td>Type is stored in tag: type=unknown.tablet</td>
 * </tr>
 * <tr>
 * <td>constraintViolations</td>
 * <td>Counter</td>
 * <td>{@value #METRICS_UPDATE_ERRORS}</td>
 * <td>Gauge</td>
 * <td>Type is stored in tag: type=constraint.violation</td>
 * </tr>
 * <tr>
 * <td>commitPrep</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_UPDATE_COMMIT_PREP}</td>
 * <td>Timer</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>commitTime</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_UPDATE_COMMIT}</td>
 * <td>Timer</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>waLogWriteTime</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_UPDATE_WALOG_WRITE}</td>
 * <td>Timer</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>mutationArraysSize</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_UPDATE_MUTATION_ARRAY_SIZE}</td>
 * <td>Distribution Summary</td>
 * <td></td>
 * </tr>
 * <!-- Thrift -->
 * <tr>
 * <td>idle</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_THRIFT_IDLE}</td>
 * <td>Distribution Summary</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>execute</td>
 * <td>Stat</td>
 * <td>{@value #METRICS_THRIFT_EXECUTE}</td>
 * <td>Distribution Summary</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_MANAGER_ROOT_TGW_ERRORS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_MANAGER_META_TGW_ERRORS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_MANAGER_COMPACTION_SVC_ERRORS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@link #METRICS_MANAGER_USER_TGW_ERRORS}</td>
 * <td>Gauge</td>
 * <td></td>
 * </tr>
 * <!-- Balancing -->
 * <tr>
 * <td>N/A</td>
 * <td>N/A</td>
 * <td>{@value METRICS_MANAGER_BALANCER_MIGRATIONS_NEEDED}</td>
 * <td>Gauge</td>
 * <td>The number of migrations that need to complete before the system is balanced</td>
 * </tr>
 * </table>
 *
 * @since 2.1.0
 */
public interface MetricsProducer {

  Logger LOG = LoggerFactory.getLogger(MetricsProducer.class);

  String METRICS_LOW_MEMORY = "accumulo.detected.low.memory";
  String METRICS_SERVER_IDLE = "accumulo.server.idle";

  String METRICS_COMPACTOR_PREFIX = "accumulo.compactor.";
  String METRICS_COMPACTOR_MAJC_STUCK = METRICS_COMPACTOR_PREFIX + "majc.stuck";
  String METRICS_COMPACTOR_QUEUE_PREFIX = METRICS_COMPACTOR_PREFIX + "queue.";
  String METRICS_COMPACTOR_JOB_PRIORITY_QUEUES = METRICS_COMPACTOR_QUEUE_PREFIX + "count";
  String METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_LENGTH = METRICS_COMPACTOR_QUEUE_PREFIX + "length";
  String METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_JOBS_DEQUEUED =
      METRICS_COMPACTOR_QUEUE_PREFIX + "jobs.dequeued";
  String METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_JOBS_QUEUED =
      METRICS_COMPACTOR_QUEUE_PREFIX + "jobs.queued";
  String METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_JOBS_REJECTED =
      METRICS_COMPACTOR_QUEUE_PREFIX + "jobs.rejected";
  String METRICS_COMPACTOR_JOB_PRIORITY_QUEUE_JOBS_PRIORITY =
      METRICS_COMPACTOR_QUEUE_PREFIX + "jobs.priority";
  String METRICS_COMPACTOR_ENTRIES_READ = METRICS_COMPACTOR_PREFIX + "entries.read";
  String METRICS_COMPACTOR_ENTRIES_WRITTEN = METRICS_COMPACTOR_PREFIX + "entries.written";

  String METRICS_FATE_PREFIX = "accumulo.fate.";
  String METRICS_FATE_TYPE_IN_PROGRESS = METRICS_FATE_PREFIX + "ops.in.progress.by.type";
  String METRICS_FATE_OPS = METRICS_FATE_PREFIX + "ops";
  String METRICS_FATE_OPS_ACTIVITY = METRICS_FATE_PREFIX + "ops.activity";
  String METRICS_FATE_ERRORS = METRICS_FATE_PREFIX + "errors";
  String METRICS_FATE_TX = METRICS_FATE_PREFIX + "tx";

  String METRICS_GC_PREFIX = "accumulo.gc.";
  String METRICS_GC_STARTED = METRICS_GC_PREFIX + "started";
  String METRICS_GC_FINISHED = METRICS_GC_PREFIX + "finished";
  String METRICS_GC_CANDIDATES = METRICS_GC_PREFIX + "candidates";
  String METRICS_GC_IN_USE = METRICS_GC_PREFIX + "in.use";
  String METRICS_GC_DELETED = METRICS_GC_PREFIX + "deleted";
  String METRICS_GC_ERRORS = METRICS_GC_PREFIX + "errors";
  String METRICS_GC_WAL_STARTED = METRICS_GC_PREFIX + "wal.started";
  String METRICS_GC_WAL_FINISHED = METRICS_GC_PREFIX + "wal.finished";
  String METRICS_GC_WAL_CANDIDATES = METRICS_GC_PREFIX + "wal.candidates";
  String METRICS_GC_WAL_IN_USE = METRICS_GC_PREFIX + "wal.in.use";
  String METRICS_GC_WAL_DELETED = METRICS_GC_PREFIX + "wal.deleted";
  String METRICS_GC_WAL_ERRORS = METRICS_GC_PREFIX + "wal.errors";
  String METRICS_GC_POST_OP_DURATION = METRICS_GC_PREFIX + "post.op.duration";
  String METRICS_GC_RUN_CYCLE = METRICS_GC_PREFIX + "run.cycle";

  String METRICS_MANAGER_PREFIX = "accumulo.manager.";
  String METRICS_MANAGER_ROOT_TGW_ERRORS = METRICS_MANAGER_PREFIX + "tabletmgmt.root.errors";
  String METRICS_MANAGER_META_TGW_ERRORS = METRICS_MANAGER_PREFIX + "tabletmgmt.meta.errors";
  String METRICS_MANAGER_USER_TGW_ERRORS = METRICS_MANAGER_PREFIX + "tabletmgmt.user.errors";
  String METRICS_MANAGER_COMPACTION_SVC_ERRORS =
      METRICS_MANAGER_PREFIX + "compaction.svc.misconfigured";

  String METRICS_MAJC_PREFIX = "accumulo.compactions.majc.";
  String METRICS_MAJC_QUEUED = METRICS_MAJC_PREFIX + "queued";
  String METRICS_MAJC_RUNNING = METRICS_MAJC_PREFIX + "running";
  String METRICS_MAJC_PAUSED = METRICS_MAJC_PREFIX + "paused";

  String METRICS_MINC_PREFIX = "accumulo.tserver.compactions.minc.";
  String METRICS_MINC_QUEUED = METRICS_MINC_PREFIX + "queued";
  String METRICS_MINC_RUNNING = METRICS_MINC_PREFIX + "running";
  String METRICS_MINC_PAUSED = METRICS_MINC_PREFIX + "paused";

  String METRICS_SCAN_PREFIX = "accumulo.scan.";
  String METRICS_SCAN_TIMES = METRICS_SCAN_PREFIX + "times";
  String METRICS_SCAN_OPEN_FILES = METRICS_SCAN_PREFIX + "files.open";
  String METRICS_SCAN_RESULTS = METRICS_SCAN_PREFIX + "result";
  String METRICS_SCAN_YIELDS = METRICS_SCAN_PREFIX + "yields";
  String METRICS_SCAN_START = METRICS_SCAN_PREFIX + "start";
  String METRICS_SCAN_CONTINUE = METRICS_SCAN_PREFIX + "continue";
  String METRICS_SCAN_CLOSE = METRICS_SCAN_PREFIX + "close";
  String METRICS_SCAN_RESERVATION_TOTAL_TIMER = METRICS_SCAN_PREFIX + "reservation.total.timer";
  String METRICS_SCAN_RESERVATION_WRITEOUT_TIMER =
      METRICS_SCAN_PREFIX + "reservation.writeout.timer";
  String METRICS_SCAN_BUSY_TIMEOUT_COUNTER = METRICS_SCAN_PREFIX + "busy.timeout.count";
  String METRICS_SCAN_RESERVATION_CONFLICT_COUNTER =
      METRICS_SCAN_PREFIX + "reservation.conflict.count";
  String METRICS_SCAN_QUERIES = METRICS_SCAN_PREFIX + "queries";
  String METRICS_SCAN_QUERY_SCAN_RESULTS = METRICS_SCAN_PREFIX + "query.results";
  String METRICS_SCAN_QUERY_SCAN_RESULTS_BYTES = METRICS_SCAN_PREFIX + "query.results.bytes";
  String METRICS_SCAN_SCANNED_ENTRIES = METRICS_SCAN_PREFIX + "query.scanned.entries";
  String METRICS_SCAN_PAUSED_FOR_MEM = METRICS_SCAN_PREFIX + "paused.for.memory";
  String METRICS_SCAN_RETURN_FOR_MEM = METRICS_SCAN_PREFIX + "return.early.for.memory";

  String METRICS_SCAN_TABLET_METADATA_CACHE = METRICS_SCAN_PREFIX + "tablet.metadata.cache";

  String METRICS_TSERVER_PREFIX = "accumulo.tserver.";
  String METRICS_TSERVER_ENTRIES = METRICS_TSERVER_PREFIX + "entries";
  String METRICS_TSERVER_MEM_ENTRIES = METRICS_TSERVER_PREFIX + "entries.mem";
  String METRICS_TSERVER_MINC_QUEUED = METRICS_TSERVER_PREFIX + "minc.queued";
  String METRICS_TSERVER_MINC_RUNNING = METRICS_TSERVER_PREFIX + "minc.running";
  String METRICS_TSERVER_MINC_TOTAL = METRICS_TSERVER_PREFIX + "minc.total";
  String METRICS_TSERVER_TABLETS_LONG_ASSIGNMENTS =
      METRICS_TSERVER_PREFIX + "tablets.assignments.warning";
  String METRICS_TSERVER_TABLETS_ONLINE_ONDEMAND =
      METRICS_TSERVER_PREFIX + "tablets.ondemand.online";
  String METRICS_TSERVER_TABLETS_ONDEMAND_UNLOADED_FOR_MEM =
      METRICS_TSERVER_PREFIX + "tablets.ondemand.unloaded.lowmem";
  String METRICS_TSERVER_TABLETS_ONLINE = METRICS_TSERVER_PREFIX + "tablets.online";
  String METRICS_TSERVER_TABLETS_OPENING = METRICS_TSERVER_PREFIX + "tablets.opening";
  String METRICS_TSERVER_TABLETS_UNOPENED = METRICS_TSERVER_PREFIX + "tablets.unopened";
  String METRICS_TSERVER_TABLETS_FILES = METRICS_TSERVER_PREFIX + "tablets.files";
  String METRICS_TSERVER_HOLD = METRICS_TSERVER_PREFIX + "hold";
  String METRICS_TSERVER_INGEST_MUTATIONS = METRICS_TSERVER_PREFIX + "ingest.mutations";
  String METRICS_TSERVER_INGEST_BYTES = METRICS_TSERVER_PREFIX + "ingest.bytes";

  String METRICS_THRIFT_PREFIX = "accumulo.thrift.";
  String METRICS_THRIFT_EXECUTE = METRICS_THRIFT_PREFIX + "execute";
  String METRICS_THRIFT_IDLE = METRICS_THRIFT_PREFIX + "idle";

  String METRICS_UPDATE_PREFIX = "accumulo.tserver.updates.";
  String METRICS_UPDATE_ERRORS = METRICS_UPDATE_PREFIX + "error";
  String METRICS_UPDATE_COMMIT = METRICS_UPDATE_PREFIX + "commit";
  String METRICS_UPDATE_COMMIT_PREP = METRICS_UPDATE_COMMIT + ".prep";
  String METRICS_UPDATE_WALOG_WRITE = METRICS_UPDATE_PREFIX + "walog.write";
  String METRICS_UPDATE_MUTATION_ARRAY_SIZE = METRICS_UPDATE_PREFIX + "mutation.arrays.size";

  String METRICS_BLOCKCACHE_PREFIX = "accumulo.blockcache.";
  String METRICS_BLOCKCACHE_INDEX_HITCOUNT = METRICS_BLOCKCACHE_PREFIX + "index.hitcount";
  String METRICS_BLOCKCACHE_INDEX_REQUESTCOUNT = METRICS_BLOCKCACHE_PREFIX + "index.requestcount";
  String METRICS_BLOCKCACHE_INDEX_EVICTIONCOUNT = METRICS_BLOCKCACHE_PREFIX + "index.evictioncount";
  String METRICS_BLOCKCACHE_DATA_HITCOUNT = METRICS_BLOCKCACHE_PREFIX + "data.hitcount";
  String METRICS_BLOCKCACHE_DATA_REQUESTCOUNT = METRICS_BLOCKCACHE_PREFIX + "data.requestcount";
  String METRICS_BLOCKCACHE_DATA_EVICTIONCOUNT = METRICS_BLOCKCACHE_PREFIX + "data.evictioncount";
  String METRICS_BLOCKCACHE_SUMMARY_HITCOUNT = METRICS_BLOCKCACHE_PREFIX + "summary.hitcount";
  String METRICS_BLOCKCACHE_SUMMARY_REQUESTCOUNT =
      METRICS_BLOCKCACHE_PREFIX + "summary.requestcount";
  String METRICS_BLOCKCACHE_SUMMARY_EVICTIONCOUNT =
      METRICS_BLOCKCACHE_PREFIX + "summary.evictioncount";

  String METRICS_MANAGER_BALANCER_MIGRATIONS_NEEDED = "accumulo.manager.balancer.migrations.needed";

  /**
   * Build Micrometer Meter objects and register them with the registry
   */
  void registerMetrics(MeterRegistry registry);

  /**
   * Returns a new mutable mapping of metric field value to metric field name.
   *
   * @return map of field names to variable names.
   */
  default Map<String,String> getMetricFields() {
    Map<String,String> fields = new HashMap<>();
    for (Field f : MetricsProducer.class.getDeclaredFields()) {
      if (Modifier.isStatic(f.getModifiers()) && f.getType().equals(String.class)
          && !f.getName().contains("PREFIX")) {
        try {

          fields.put((String) f.get(MetricsProducer.class), f.getName());
        } catch (IllegalArgumentException | IllegalAccessException e) {
          // this shouldn't happen, but let's log it anyway
          LOG.error("Error getting metric value for field: {}", f.getName());
        }
      }
    }
    return fields;
  }
}
