/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.table.source;

import org.apache.paimon.CoreOptions;
import org.apache.paimon.operation.ScanKind;
import org.apache.paimon.predicate.Predicate;
import org.apache.paimon.schema.TableSchema;
import org.apache.paimon.table.DataTable;
import org.apache.paimon.table.source.snapshot.BoundedChecker;
import org.apache.paimon.table.source.snapshot.FollowUpScanner;
import org.apache.paimon.table.source.snapshot.StartingScanner;
import org.apache.paimon.utils.Filter;

import javax.annotation.Nullable;

import java.io.Serializable;
import java.util.HashMap;

/** {@link DataTableScan} for streaming planning. */
public interface StreamDataTableScan extends DataTableScan, InnerStreamTableScan {

    @Override
    StreamDataTableScan withSnapshot(long snapshotId);

    @Override
    StreamDataTableScan withFilter(Predicate predicate);

    @Override
    StreamDataTableScan withKind(ScanKind scanKind);

    @Override
    StreamDataTableScan withLevelFilter(Filter<Integer> levelFilter);

    boolean supportStreamingReadOverwrite();

    StreamDataTableScan withStartingScanner(StartingScanner startingScanner);

    StreamDataTableScan withFollowUpScanner(FollowUpScanner followUpScanner);

    StreamDataTableScan withBoundedChecker(BoundedChecker boundedChecker);

    StreamDataTableScan withSnapshotStarting();

    static void validate(TableSchema schema) {
        CoreOptions options = new CoreOptions(schema.options());
        CoreOptions.MergeEngine mergeEngine = options.mergeEngine();
        HashMap<CoreOptions.MergeEngine, String> mergeEngineDesc =
                new HashMap<CoreOptions.MergeEngine, String>() {
                    {
                        put(CoreOptions.MergeEngine.PARTIAL_UPDATE, "Partial update");
                        put(CoreOptions.MergeEngine.AGGREGATE, "Pre-aggregate");
                    }
                };
        if (schema.primaryKeys().size() > 0 && mergeEngineDesc.containsKey(mergeEngine)) {
            switch (options.changelogProducer()) {
                case NONE:
                case INPUT:
                    throw new RuntimeException(
                            mergeEngineDesc.get(mergeEngine)
                                    + " continuous reading is not supported. You can use "
                                    + "'lookup' or 'full-compaction' changelog producer to support streaming reading.");
                default:
            }
        }
    }

    // ------------------------------------------------------------------------
    //  factory interface
    // ------------------------------------------------------------------------

    /** Factory to create {@link StreamDataTableScan}. */
    interface Factory extends Serializable {

        StreamDataTableScan create(DataTable dataTable, @Nullable Long nextSnapshotId);
    }

    /** A default {@link Factory} to create {@link StreamDataTableScan}. */
    class DefaultFactory implements Factory {

        @Override
        public StreamDataTableScan create(DataTable dataTable, @Nullable Long nextSnapshotId) {
            StreamDataTableScan scan = dataTable.newStreamScan();
            scan.restore(nextSnapshotId);
            return scan;
        }
    }
}
