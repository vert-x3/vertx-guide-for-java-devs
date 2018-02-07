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

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.ResultSet;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.SingleHelper;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.sql.SQLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
class WikiDatabaseServiceImpl implements WikiDatabaseService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseServiceImpl.class);

  private final HashMap<SqlQuery, String> sqlQueries;
  private final JDBCClient dbClient;

  WikiDatabaseServiceImpl(io.vertx.ext.jdbc.JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries, Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
    this.dbClient = new JDBCClient(dbClient);
    this.sqlQueries = sqlQueries;

    getConnection()
      .flatMapCompletable(conn -> conn.rxExecute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE)))
      .andThen(Single.just(this))
      .subscribe(SingleHelper.toObserver(readyHandler));
  }

  private Single<SQLConnection> getConnection() {
    return dbClient.rxGetConnection().flatMap(conn -> {
      Single<SQLConnection> connectionSingle = Single.just(conn);
      return connectionSingle.doFinally(conn::close);
    });
  }

  @Override
  public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
    dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES))
      .flatMapPublisher(res -> {
        List<JsonArray> results = res.getResults();
        return Flowable.fromIterable(results);
      })
      .map(json -> json.getString(0))
      .sorted()
      .collect(JsonArray::new, JsonArray::add)
      .subscribe(SingleHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
    dbClient.rxQueryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(name))
      .map(result -> {
        if (result.getNumRows() > 0) {
          JsonArray row = result.getResults().get(0);
          return new JsonObject()
            .put("found", true)
            .put("id", row.getInteger(0))
            .put("rawContent", row.getString(1));
        } else {
          return new JsonObject().put("found", false);
        }
      })
      .subscribe(SingleHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService fetchPageById(int id, Handler<AsyncResult<JsonObject>> resultHandler) {
    Single<ResultSet> resultSet = dbClient.rxQueryWithParams(
      sqlQueries.get(SqlQuery.GET_PAGE_BY_ID), new JsonArray().add(id));
    resultSet
      .map(result -> {
        if (result.getNumRows() > 0) {
          JsonObject row = result.getRows().get(0);
          return new JsonObject()
            .put("found", true)
            .put("id", row.getInteger("ID"))
            .put("name", row.getString("NAME"))
            .put("content", row.getString("CONTENT"));
        } else {
          return new JsonObject().put("found", false);
        }
      })
      .subscribe(SingleHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.CREATE_PAGE), new JsonArray().add(title).add(markdown))
      .toCompletable()
      .subscribe(CompletableHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.SAVE_PAGE), new JsonArray().add(markdown).add(id))
      .toCompletable()
      .subscribe(CompletableHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
    JsonArray data = new JsonArray().add(id);
    dbClient.rxUpdateWithParams(sqlQueries.get(SqlQuery.DELETE_PAGE), data)
      .toCompletable()
      .subscribe(CompletableHelper.toObserver(resultHandler));
    return this;
  }

  @Override
  public WikiDatabaseService fetchAllPagesData(Handler<AsyncResult<List<JsonObject>>> resultHandler) {
    dbClient.rxQuery(sqlQueries.get(SqlQuery.ALL_PAGES_DATA))
      .map(ResultSet::getRows)
      .subscribe(SingleHelper.toObserver(resultHandler));
    return this;
  }
}
