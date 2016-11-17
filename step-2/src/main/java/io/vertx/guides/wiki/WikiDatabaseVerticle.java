/*
 *  Copyright (c) 2016 Red Hat, Inc. and/or its affiliates.
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

package io.vertx.guides.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
public class WikiDatabaseVerticle extends AbstractVerticle {

  public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
  public static final String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class";
  public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";
  public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";
  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

  private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseVerticle.class);

  private JDBCClient dbClient;

  public enum ErrorCodes {
    NO_ACTION_SPECIFIED,
    BAD_ACTION,
    DB_ERROR
  }

  private enum SqlQuery {
    CREATE_PAGES_TABLE,
    ALL_PAGES,
    GET_PAGE
  }

  private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    loadSqlQueries();

    dbClient = JDBCClient.createShared(vertx, new JsonObject()
      .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:file:db/wiki"))
      .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, "org.hsqldb.jdbcDriver"))
      .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 30)));

    dbClient.getConnection(ar -> {
      if (ar.failed()) {
        LOGGER.error("Could not open a database connection", ar.cause());
        startFuture.fail(ar.cause());
      } else {
        SQLConnection connection = ar.result();
        connection.execute(sqlQueries.get(SqlQuery.CREATE_PAGES_TABLE), create -> {
          connection.close();
          if (create.failed()) {
            LOGGER.error("Database preparation error", create.cause());
            startFuture.fail(create.cause());
          } else {
            vertx.eventBus().consumer(config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue"), this::onMessage);
            startFuture.complete();
          }
        });
      }
    });
  }

  public void onMessage(Message<JsonObject> message) {

    if (!message.headers().contains("action")) {
      message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "No action header specified");
    }
    String action = message.headers().get("action");

    switch (action) {
      case "all-pages":
        fetchAllPages(message);
        break;
      case "get-page":
        fetchPage(message);
        break;
      default:
        message.fail(ErrorCodes.BAD_ACTION.ordinal(), "Bad action: " + action);
    }
  }

  private void fetchAllPages(Message<JsonObject> message) {

    dbClient.getConnection(car -> {
      if (car.succeeded()) {
        SQLConnection connection = car.result();
        connection.query(sqlQueries.get(SqlQuery.ALL_PAGES), res -> {
          connection.close();
          if (res.succeeded()) {
            List<String> pages = res.result()
              .getResults()
              .stream()
              .map(json -> json.getString(0))
              .sorted()
              .collect(Collectors.toList());
            message.reply(new JsonObject().put("pages", new JsonArray(pages)));
          } else {
            reportQueryError(message, res.cause());
          }
        });
      } else {
        reportQueryError(message, car.cause());
      }
    });
  }

  private void fetchPage(Message<JsonObject> message) {

    String requestedPage = message.body().getString("page");

    dbClient.getConnection(car -> {
      if (car.succeeded()) {
        SQLConnection connection = car.result();
        connection.queryWithParams(sqlQueries.get(SqlQuery.GET_PAGE), new JsonArray().add(requestedPage), fetch -> {
          connection.close();
          if (fetch.succeeded()) {
            JsonObject response = new JsonObject();
            ResultSet resultSet = fetch.result();
            if (resultSet.getNumRows() == 0) {
              response.put("found", false);
            } else {
              response.put("found", true);
              JsonArray row = resultSet.getResults().get(0);
              response.put("id", row.getInteger(0));
              response.put("rawContent", row.getString(1));
            }
            message.reply(response);
          } else {
            reportQueryError(message, fetch.cause());
          }
        });
      } else {
        reportQueryError(message, car.cause());
      }
    });

  }

  private void reportQueryError(Message<JsonObject> message, Throwable cause) {
    LOGGER.error("Database query error", cause);
    message.fail(ErrorCodes.DB_ERROR.ordinal(), cause.getMessage());
  }

  /*
   * Note: this uses blocking APIs, but data is small...
   */
  private void loadSqlQueries() throws IOException {

    String queriesFile = config().getString(CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE);
    InputStream queriesInputStream;
    if (queriesFile != null) {
      queriesInputStream = new FileInputStream(queriesFile);
    } else {
      queriesInputStream = getClass().getResourceAsStream("/db-queries.properties");
    }

    Properties queriesProps = new Properties();
    queriesProps.load(queriesInputStream);
    queriesInputStream.close();

    sqlQueries.put(SqlQuery.CREATE_PAGES_TABLE, queriesProps.getProperty("create-pages-table"));
    sqlQueries.put(SqlQuery.ALL_PAGES, queriesProps.getProperty("all-pages"));
    sqlQueries.put(SqlQuery.GET_PAGE, queriesProps.getProperty("get-page"));
  }
}
