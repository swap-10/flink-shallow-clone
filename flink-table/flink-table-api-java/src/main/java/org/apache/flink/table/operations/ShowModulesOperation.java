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

package org.apache.flink.table.operations;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.api.internal.TableResultInternal;
import org.apache.flink.table.module.ModuleEntry;
import org.apache.flink.table.types.DataType;

import java.util.Arrays;

import static org.apache.flink.table.api.internal.TableResultUtils.buildStringArrayResult;
import static org.apache.flink.table.api.internal.TableResultUtils.buildTableResult;

/** Operation to describe a SHOW [FULL] MODULES statement. */
@Internal
public class ShowModulesOperation implements ShowOperation {
    private final boolean requireFull;

    public ShowModulesOperation(boolean requireFull) {
        this.requireFull = requireFull;
    }

    @Override
    public String asSummaryString() {
        return requireFull ? "SHOW FULL MODULES" : "SHOW MODULES";
    }

    public boolean requireFull() {
        return requireFull;
    }

    @Override
    public TableResultInternal execute(Context ctx) {
        if (requireFull) {
            ModuleEntry[] fullModules =
                    ctx.getModuleManager().listFullModules().toArray(new ModuleEntry[0]);
            Object[][] rows =
                    Arrays.stream(fullModules)
                            .map(entry -> new Object[] {entry.name(), entry.used()})
                            .toArray(Object[][]::new);
            return buildTableResult(
                    new String[] {"module name", "used"},
                    new DataType[] {DataTypes.STRING(), DataTypes.BOOLEAN()},
                    rows);
        } else {
            String[] modules = ctx.getModuleManager().listModules().toArray(new String[0]);
            return buildStringArrayResult("module name", modules);
        }
    }
}
