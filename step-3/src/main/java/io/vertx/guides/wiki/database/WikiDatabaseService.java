/*
 *  Copyright (c) 2017 Red Hat, Inc. and/or its affiliates.
 *  Copyright (c) 2017 INSA Lyon, CITI Laboratory.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.guides.wiki.database;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.HashMap;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
// tag::interface[]
@ProxyGen
public interface WikiDatabaseService {

  void fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler);

  void fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler);

  void createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler);

  void savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler);

  void deletePage(int id, Handler<AsyncResult<Void>> resultHandler);

  // (...)
  // end::interface[]

  // tag::create[]
  static WikiDatabaseService create(JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
    return new WikiDatabaseServiceImpl(dbClient, sqlQueries, readyHandler);
  }
  // end::create[]

  // tag::proxy[]
  static WikiDatabaseService createProxy(Vertx vertx, String address) {
    return new WikiDatabaseServiceVertxEBProxy(vertx, address);
  }
  // end::proxy[]
}
