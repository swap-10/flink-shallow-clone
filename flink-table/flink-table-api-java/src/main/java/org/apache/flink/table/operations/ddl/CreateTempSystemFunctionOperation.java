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

package org.apache.flink.table.operations.ddl;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.api.internal.TableResultImpl;
import org.apache.flink.table.api.internal.TableResultInternal;
import org.apache.flink.table.catalog.CatalogFunction;
import org.apache.flink.table.catalog.CatalogFunctionImpl;
import org.apache.flink.table.catalog.FunctionCatalog;
import org.apache.flink.table.catalog.FunctionLanguage;
import org.apache.flink.table.functions.FunctionDefinition;
import org.apache.flink.table.operations.Operation;
import org.apache.flink.table.operations.OperationUtils;
import org.apache.flink.table.resource.ResourceUri;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operation to describe a CREATE FUNCTION statement for temporary system function. */
@Internal
public class CreateTempSystemFunctionOperation implements CreateOperation {
    private final String functionName;
    private final boolean ignoreIfExists;
    private final CatalogFunction catalogFunction;

    public CreateTempSystemFunctionOperation(
            String functionName,
            String functionClass,
            boolean ignoreIfExists,
            FunctionLanguage functionLanguage,
            List<ResourceUri> resourceUris) {
        this.functionName = functionName;
        this.ignoreIfExists = ignoreIfExists;
        this.catalogFunction =
                new CatalogFunctionImpl(functionClass, functionLanguage, resourceUris);
    }

    public CreateTempSystemFunctionOperation(
            String functionName, boolean ignoreIfExists, FunctionDefinition functionDefinition) {
        this.functionName = functionName;
        this.ignoreIfExists = ignoreIfExists;
        this.catalogFunction = new FunctionCatalog.InlineCatalogFunction(functionDefinition);
    }

    public String getFunctionName() {
        return this.functionName;
    }

    public boolean isIgnoreIfExists() {
        return this.ignoreIfExists;
    }

    public CatalogFunction getCatalogFunction() {
        return catalogFunction;
    }

    @Override
    public String asSummaryString() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("functionName", functionName);
        params.put("catalogFunction", getCatalogFunction());
        params.put("ignoreIfExists", ignoreIfExists);
        params.put("functionLanguage", getCatalogFunction().getFunctionLanguage());

        return OperationUtils.formatWithChildren(
                "CREATE TEMPORARY SYSTEM FUNCTION",
                params,
                Collections.emptyList(),
                Operation::asSummaryString);
    }

    @Override
    public TableResultInternal execute(Context ctx) {
        try {
            ctx.getFunctionCatalog()
                    .registerTemporarySystemFunction(functionName, catalogFunction, ignoreIfExists);
            return TableResultImpl.TABLE_RESULT_OK;
        } catch (Exception e) {
            throw new TableException(String.format("Could not execute %s", asSummaryString()), e);
        }
    }
}
