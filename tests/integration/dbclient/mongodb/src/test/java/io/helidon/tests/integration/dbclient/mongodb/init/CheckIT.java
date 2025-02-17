/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.tests.integration.dbclient.mongodb.init;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.helidon.common.reactive.Multi;
import io.helidon.config.Config;
import io.helidon.reactive.dbclient.DbClient;
import io.helidon.reactive.dbclient.DbRow;
import io.helidon.tests.integration.dbclient.common.AbstractIT;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Check minimal functionality needed before running database schema initialization.
 * First test class being executed after database startup.
 */
public class CheckIT extends AbstractIT {

    /** Local logger instance. */
    private static final System.Logger LOGGER = System.getLogger(CheckIT.class.getName());

    /** Timeout in seconds to wait for database to come up. */
    private static final int TIMEOUT = 60;

    /** Helidon DB client with admin database access. */
    public static final DbClient DB_ADMIN = initDbAdmin();

    private static DbClient initDbAdmin() {
        Config dbConfig = CONFIG.get("dbadmin");
        return DbClient.builder(dbConfig).build();
    }

    /**
     * Wait for database server to start.
     *
     * @param dbClient Helidon database client
     */
    private static void waitForStart(DbClient dbClient) {
        long endTm = 1000 * TIMEOUT + System.currentTimeMillis();
        boolean retry = true;
        while (retry) {
            try {
                dbClient.execute(exec -> exec.namedGet("ping"))
                        .await(Duration.ofSeconds(1));
                retry = false;
            } catch (Exception ex) {
                if (System.currentTimeMillis() > endTm) {
                    fail("Database startup failed!", ex);
                } else {
                    // Exceptions will be coming until database is up
                    LOGGER.log(Level.DEBUG, () -> String.format("Exception: %s", ex.getMessage()), ex);
                }
            }
        }
    }

    /**
     * Initialize user for test database.
     *
     * @param dbClient Helidon database client
     */
    private static void initUser(DbClient dbClient) {
        try {
            dbClient.execute(exec -> exec
                    .namedGet("use")
                    .flatMapSingle(result -> exec.namedGet("create-user"))
            ).await();
        } catch (Exception ex) {
                LOGGER.log(Level.WARNING, () -> String.format("Exception: %s", ex.getMessage()), ex);
                fail("Database user setup failed!", ex);
        }
    }

    /**
     * Setup database for tests.
     * Wait for database to start. Returns after ping query completed successfully or timeout passed.
     */
    @BeforeAll
    public static void setup() {
        waitForStart(DB_ADMIN);
        //initUser(DB_ADMIN);
    }

    /**
     * Simple test to verify that DML query execution works.
     * Used before running database schema initialization.
     */
    @Test
    public void testDmlStatementExecution() {
        Multi<DbRow> result = DB_CLIENT.execute(exec -> exec.namedQuery("ping-query"));
        List<DbRow> rowsList = result.collectList().await(5, TimeUnit.SECONDS);
        DbRow row = rowsList.get(0);
        Double ok = row.column("ok").as(Double.class);
        assertThat(ok, equalTo(1.0));
        LOGGER.log(Level.DEBUG, () -> String.format("Command ping row: %s", row));
    }

}
