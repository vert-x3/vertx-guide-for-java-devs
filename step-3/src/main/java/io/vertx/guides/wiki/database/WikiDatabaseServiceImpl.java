package io.vertx.guides.wiki.database;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;

import java.util.HashMap;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
class WikiDatabaseServiceImpl implements WikiDatabaseService {

  private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseServiceImpl.class);

  private final Vertx vertx;
  private final HashMap<SqlQuery, String> sqlQueries;
  private final JDBCClient dbClient;

  public WikiDatabaseServiceImpl(Vertx vertx, JDBCClient dbClient, HashMap<SqlQuery, String> sqlQueries) {
    this.vertx = vertx;
    this.dbClient = dbClient;
    this.sqlQueries = sqlQueries;
  }

  @Override
  public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
    return this;
  }

  @Override
  public WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
    return this;
  }

  @Override
  public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    return this;
  }

  @Override
  public WikiDatabaseService savePage(String id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
    return this;
  }

  @Override
  public WikiDatabaseService deletePage(String id, Handler<AsyncResult<Void>> resultHandler) {
    return this;
  }
}
