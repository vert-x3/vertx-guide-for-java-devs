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

package io.vertx.guides.wiki;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
public class MainVerticle extends AbstractVerticle {

  // tag::sql-fields[]
  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?"; // <1>
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";
  // end::sql-fields[]

  // tag::db-and-logger[]
  private JDBCPool dbPool;

  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);
  // end::db-and-logger[]


  // tag::prepareDatabase[]
  private Future<Void> prepareDatabase() {
    Promise<Void> promise = Promise.promise();

    dbPool = JDBCPool.pool(vertx, new JsonObject()  // <1>
      .put("url", "jdbc:hsqldb:file:db/wiki")   // <2>
      .put("driver_class", "org.hsqldb.jdbcDriver")   // <3>
      .put("max_pool_size", 30));   // <4>

    dbPool.query(SQL_CREATE_PAGES_TABLE)
      .execute()
      .onSuccess(rows -> {
        LOGGER.info("created the database tables");
        promise.complete();
      })
      .onFailure(error -> {
        LOGGER.error("Database preparation error", error);
        promise.fail(error);
      });

    return promise.future();
  }
  // end::prepareDatabase[]

  // tag::startHttpServer[]
  private FreeMarkerTemplateEngine templateEngine;

  private Future<Void> startHttpServer() {
    Promise<Void> promise = Promise.promise();
    HttpServer server = vertx.createHttpServer();   // <1>

    Router router = Router.router(vertx);   // <2>
    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler); // <3>
    router.post().handler(BodyHandler.create());  // <4>
    router.post("/save").handler(this::pageUpdateHandler);
    router.post("/create").handler(this::pageCreateHandler);
    router.post("/delete").handler(this::pageDeletionHandler);

    templateEngine = FreeMarkerTemplateEngine.create(vertx);

    server
      .requestHandler(router)   // <5>
      .listen(8080)
      .onSuccess(result -> {
        LOGGER.info("HTTP server running on port 8080");
        promise.complete();
      })
      .onFailure(error -> {
        LOGGER.error("Could not start a HTTP server", error);
        promise.fail(error);
      });
    return promise.future();
  }
  // end::startHttpServer[]

  // tag::pageDeletionHandler[]
  private void pageDeletionHandler(RoutingContext context) {
    String id = context.request().getParam("id");
    this.dbPool.preparedQuery(SQL_DELETE_PAGE)
      .execute(Tuple.of(id))
      .onSuccess(rows -> {
        context.response().putHeader("Location", "/");
        context.response().end();
      })
      .onFailure(error -> {
        context.fail(error);
      });
  }
  // end::pageDeletionHandler[]

  // tag::pageCreateHandler[]
  private void pageCreateHandler(RoutingContext context) {
    String pageName = context.request().getParam("name");
    String location = "/wiki/" + pageName;
    if (pageName == null || pageName.isEmpty()) {
      location = "/";
    }
    context.response().setStatusCode(303);
    context.response().putHeader("Location", location);
    context.response().end();
  }
  // end::pageCreateHandler[]

  // tag::indexHandler[]
  private void indexHandler(RoutingContext context) {
    this.dbPool
      .query(SQL_ALL_PAGES)
      .execute()
      .compose(rows -> {
        JsonObject templateData = new JsonObject();
        JsonArray pages = new JsonArray();
        rows.forEach(r -> {
          pages.add(r.getString("NAME"));
        });
        templateData.put("title", "Wiki home");  // <2>
        templateData.put("pages", pages);
        return Future.succeededFuture(templateData);
      })
      .compose(templateData -> {
        return templateEngine.render(templateData, "templates/index.ftl");
      })
      .onSuccess(data -> {
        context.response().putHeader("Content-Type", "text/html");
        context.response().end(data.toString());
      })
      .onFailure(error -> {
        context.fail(error);
      });
  }
// end::indexHandler[]

  // tag::pageUpdateHandler[]
  private void pageUpdateHandler(RoutingContext context) {
    String id = context.request().getParam("id");   // <1>
    String title = context.request().getParam("title");
    String markdown = context.request().getParam("markdown");
    boolean newPage = "yes".equals(context.request().getParam("newPage"));  // <2>
    Future<RowSet<Row>> preparedQuery;
    if (newPage) {
      preparedQuery = this.dbPool.preparedQuery(SQL_CREATE_PAGE)
        .execute(Tuple.of(title, markdown));
    } else {
      preparedQuery = this.dbPool.preparedQuery(SQL_SAVE_PAGE)
        .execute(Tuple.of(markdown, id));
    }
    preparedQuery
      .onSuccess(rows -> {
        context.response().setStatusCode(303);    // <5>
        context.response().putHeader("Location", "/wiki/" + title);
        context.response().end();
      })
      .onFailure(error -> {
        context.fail(error);
      });
  }
// end::pageUpdateHandler[]

  // tag::pageRenderingHandler[]
  private static final String EMPTY_PAGE_MARKDOWN =
    "# A new page\n" +
      "\n" +
      "Feel-free to write in Markdown!\n";

  private void pageRenderingHandler(RoutingContext context) {
    String page = context.request().getParam("page");   // <1>

    this.dbPool.preparedQuery(SQL_GET_PAGE)
      .execute(Tuple.of(page))
      .compose(rows -> {
        JsonObject templateData = new JsonObject();
        RowIterator<Row> iterator = rows.iterator();
        if (iterator.hasNext()) {
          Row row = iterator.next();
          templateData.put("id", row.getInteger(0));
          templateData.put("rawContent", row.getString("CONTENT"));
          templateData.put("newPage", "no");
        } else {
          templateData.put("id", -1);
          templateData.put("rawContent", EMPTY_PAGE_MARKDOWN);
          templateData.put("newPage", "yes");
        }
        templateData.put("title", page);
        templateData.put("timestamp", new Date().toString());
        templateData.put("content", Processor.process(templateData.getString("rawContent")));
        return Future.succeededFuture(templateData);
      })
      .compose(templateData -> {
        return templateEngine.render(templateData, "templates/page.ftl");
      })
      .onSuccess(renderedContent -> {
        context.response().putHeader("Content-Type", "text/html");
        context.response().end(renderedContent.toString());
      })
      .onFailure(error -> {
        context.fail(500, error);
      });
  }
// end::pageRenderingHandler[]

  // tag::start[]
  @Override
  public void start(Promise<Void> promise) throws Exception {
    Future<Void> steps = prepareDatabase()
      .compose(v -> startHttpServer())
      .onSuccess(result -> {
        LOGGER.info("db and httpserver started");
        promise.complete();
      }).onFailure(error -> {
        LOGGER.error("something went wrong while setting up the db or httpserver {}", error);
        promise.fail(error);
      });
  }
  // end::start[]
}
