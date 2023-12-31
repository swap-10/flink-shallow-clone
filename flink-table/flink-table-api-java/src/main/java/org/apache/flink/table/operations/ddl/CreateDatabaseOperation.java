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
import org.apache.flink.table.api.ValidationException;
import org.apache.flink.table.api.internal.TableResultImpl;
import org.apache.flink.table.api.internal.TableResultInternal;
import org.apache.flink.table.catalog.CatalogDatabase;
import org.apache.flink.table.catalog.exceptions.DatabaseAlreadyExistException;
import org.apache.flink.table.operations.Operation;
import org.apache.flink.table.operations.OperationUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Operation to describe a CREATE DATABASE statement. */
@Internal
public class CreateDatabaseOperation implements CreateOperation {
    private final String catalogName;
    private final String databaseName;
    private final CatalogDatabase catalogDatabase;
    private final boolean ignoreIfExists;

    public CreateDatabaseOperation(
            String catalogName,
            String databaseName,
            CatalogDatabase catalogDatabase,
            boolean ignoreIfExists) {
        this.catalogName = catalogName;
        this.databaseName = databaseName;
        this.catalogDatabase = catalogDatabase;
        this.ignoreIfExists = ignoreIfExists;
    }

    public String getCatalogName() {
        return catalogName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public CatalogDatabase getCatalogDatabase() {
        return catalogDatabase;
    }

    public boolean isIgnoreIfExists() {
        return ignoreIfExists;
    }

    @Override
    public String asSummaryString() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("catalogDatabase", catalogDatabase.getProperties());
        params.put("catalogName", catalogName);
        params.put("databaseName", databaseName);
        params.put("ignoreIfExists", ignoreIfExists);

        return OperationUtils.formatWithChildren(
                "CREATE DATABASE", params, Collections.emptyList(), Operation::asSummaryString);
    }

    @Override
    public TableResultInternal execute(Context ctx) {
        try {
            ctx.getCatalogManager()
                    .createDatabase(catalogName, databaseName, catalogDatabase, ignoreIfExists);
            return TableResultImpl.TABLE_RESULT_OK;
        } catch (DatabaseAlreadyExistException e) {
            throw new ValidationException(
                    String.format("Could not execute %s", asSummaryString()), e);
        } catch (Exception e) {
            throw new TableException(String.format("Could not execute %s", asSummaryString()), e);
        }
    }
}
