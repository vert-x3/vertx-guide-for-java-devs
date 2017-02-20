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

package io.vertx.guides.wiki.http;

import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.jwt.JWTOptions;
import io.vertx.ext.auth.shiro.ShiroAuthOptions;
import io.vertx.ext.auth.shiro.ShiroAuthRealmType;
import io.vertx.guides.wiki.database.rxjava.WikiDatabaseService;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.ext.auth.AuthProvider;
import io.vertx.rxjava.ext.auth.User;
import io.vertx.rxjava.ext.auth.jwt.JWTAuth;
import io.vertx.rxjava.ext.auth.shiro.ShiroAuth;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.client.WebClient;
import io.vertx.rxjava.ext.web.codec.BodyCodec;
import io.vertx.rxjava.ext.web.handler.AuthHandler;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import io.vertx.rxjava.ext.web.handler.CookieHandler;
import io.vertx.rxjava.ext.web.handler.FormLoginHandler;
import io.vertx.rxjava.ext.web.handler.JWTAuthHandler;
import io.vertx.rxjava.ext.web.handler.RedirectAuthHandler;
import io.vertx.rxjava.ext.web.handler.SessionHandler;
import io.vertx.rxjava.ext.web.handler.UserSessionHandler;
import io.vertx.rxjava.ext.web.sstore.LocalSessionStore;
import io.vertx.rxjava.ext.web.templ.FreeMarkerTemplateEngine;
import rx.Single;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
public class HttpServerVerticle extends AbstractVerticle {

  public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
  public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

  private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

  private WikiDatabaseService dbService;

  private WebClient webClient;

  private static final String EMPTY_PAGE_MARKDOWN =
    "# A new page\n" +
      "\n" +
      "Feel-free to write in Markdown!\n";

  @Override
  public void start(Future<Void> startFuture) throws Exception {

    String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");
    dbService = io.vertx.guides.wiki.database.WikiDatabaseService.createProxy(vertx.getDelegate(), wikiDbQueue);

    webClient = WebClient.wrap(vertx.createHttpClient(new HttpClientOptions().setSsl(true)));

    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
      .setSsl(true)
      .setKeyStoreOptions(new JksOptions()
        .setPath("server-keystore.jks")
        .setPassword("secret")));

    AuthProvider auth = ShiroAuth.create(vertx, new ShiroAuthOptions()
      .setType(ShiroAuthRealmType.PROPERTIES)
      .setConfig(new JsonObject()
        .put("properties_path", "classpath:wiki-users.properties")));

    Router router = Router.router(vertx);

    router.route().handler(CookieHandler.create());
    router.route().handler(BodyHandler.create());
    router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    router.route().handler(UserSessionHandler.create(auth));

    AuthHandler authHandler = RedirectAuthHandler.create(auth, "/login");
    router.route("/").handler(authHandler);
    router.route("/wiki/*").handler(authHandler);
    router.route("/action/*").handler(authHandler);

    router.get("/").handler(this::indexHandler);
    router.get("/wiki/:page").handler(this::pageRenderingHandler);
    router.post("/action/save").handler(this::pageUpdateHandler);
    router.post("/action/create").handler(this::pageCreateHandler);
    router.get("/action/backup").handler(this::backupHandler);
    router.post("/action/delete").handler(this::pageDeletionHandler);

    router.get("/login").handler(this::loginHandler);
    router.post("/login-auth").handler(FormLoginHandler.create(auth));

    router.get("/logout").handler(context -> {
      context.clearUser();
      context.response()
        .setStatusCode(302)
        .putHeader("Location", "/")
        .end();
    });

    JWTAuth jwtAuth = JWTAuth.create(vertx, new JsonObject()
      .put("keyStore", new JsonObject()
        .put("path", "keystore.jceks")
        .put("type", "jceks")
        .put("password", "secret")));

    Router apiRouter = Router.router(vertx);

    apiRouter.route().handler(JWTAuthHandler.create(jwtAuth, "/api/token"));

    apiRouter.get("/token").handler(context -> {

      JsonObject creds = new JsonObject()
        .put("username", context.request().getHeader("login"))
        .put("password", context.request().getHeader("password"));
      auth.authenticate(creds, authResult -> {

        if (authResult.succeeded()) {
          User user = authResult.result();
          user.isAuthorised("create", canCreate -> {
            user.isAuthorised("delete", canDelete -> {
              user.isAuthorised("update", canUpdate -> {

                String token = jwtAuth.generateToken(
                  new JsonObject()
                    .put("username", context.request().getHeader("login"))
                    .put("canCreate", canCreate.succeeded() && canCreate.result())
                    .put("canDelete", canDelete.succeeded() && canDelete.result())
                    .put("canUpdate", canUpdate.succeeded() && canUpdate.result()),
                  new JWTOptions()
                    .setSubject("Wiki API")
                    .setIssuer("Vert.x"));
                context.response().putHeader("Content-Type", "text/plain").end(token);
              });
            });
          });
        } else {
          context.fail(401);
        }
      });
    });

    apiRouter.get("/pages").handler(this::apiRoot);
    apiRouter.get("/pages/:id").handler(this::apiGetPage);
    apiRouter.post().handler(BodyHandler.create());
    apiRouter.post("/pages").handler(this::apiCreatePage);
    apiRouter.put().handler(BodyHandler.create());
    apiRouter.put("/pages/:id").handler(this::apiUpdatePage);
    apiRouter.delete("/pages/:id").handler(this::apiDeletePage);
    router.mountSubRouter("/api", apiRouter);

    int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
    server
      .requestHandler(router::accept)
      .listen(portNumber, ar -> {
        if (ar.succeeded()) {
          LOGGER.info("HTTP server running on port " + portNumber);
          startFuture.complete();
        } else {
          LOGGER.error("Could not start a HTTP server", ar.cause());
          startFuture.fail(ar.cause());
        }
      });
  }

  private Single<Void> checkAuthorised(RoutingContext context, String authority) {
    return context.user().rxIsAuthorised(authority).<Void>map(granted -> {
      if (!granted) {
        context.response().setStatusCode(403);
      }
      return null;
    }).doOnError(err -> {
      if (context.response().getStatusCode() == 200) {
        context.fail(err);
      }
    });
  }

  private void apiDeletePage(RoutingContext context) {
    if (context.user().principal().getBoolean("canDelete", false)) {
      int id = Integer.valueOf(context.request().getParam("id"));
      dbService.deletePage(id, reply -> {
        handleSimpleDbReply(context, reply);
      });
    } else {
      context.fail(401);
    }
  }

  private void handleSimpleDbReply(RoutingContext context, AsyncResult<Void> reply) {
    if (reply.succeeded()) {
      context.response().setStatusCode(200);
      context.response().putHeader("Content-Type", "application/json");
      context.response().end(new JsonObject().put("success", true).encode());
    } else {
      context.response().setStatusCode(500);
      context.response().putHeader("Content-Type", "application/json");
      context.response().end(new JsonObject()
        .put("success", false)
        .put("error", reply.cause().getMessage()).encode());
    }
  }

  private void apiUpdatePage(RoutingContext context) {
    if (context.user().principal().getBoolean("canUpdate", false)) {
      int id = Integer.valueOf(context.request().getParam("id"));
      JsonObject page = context.getBodyAsJson();
      if (!validateJsonPageDocument(context, page, "markdown")) {
        return;
      }
      dbService.savePage(id, page.getString("markdown"), reply -> {
        handleSimpleDbReply(context, reply);
      });
    } else {
      context.fail(401);
    }
  }

  private boolean validateJsonPageDocument(RoutingContext context, JsonObject page, String... expectedKeys) {
    if (!Arrays.stream(expectedKeys).allMatch(page::containsKey)) {
      LOGGER.error("Bad page creation JSON payload: " + page.encodePrettily() + " from " + context.request().remoteAddress());
      context.response().setStatusCode(400);
      context.response().putHeader("Content-Type", "application/json");
      context.response().end(new JsonObject()
        .put("success", false)
        .put("error", "Bad request payload").encode());
      return false;
    }
    return true;
  }

  private void apiCreatePage(RoutingContext context) {
    if (context.user().principal().getBoolean("canCreate", false)) {
      JsonObject page = context.getBodyAsJson();
      if (!validateJsonPageDocument(context, page, "name", "markdown")) {
        return;
      }
      dbService.createPage(page.getString("name"), page.getString("markdown"), reply -> {
        if (reply.succeeded()) {
          context.response().setStatusCode(201);
          context.response().putHeader("Content-Type", "application/json");
          context.response().end(new JsonObject().put("success", true).encode());
        } else {
          context.response().setStatusCode(500);
          context.response().putHeader("Content-Type", "application/json");
          context.response().end(new JsonObject()
            .put("success", false)
            .put("error", reply.cause().getMessage()).encode());
        }
      });
    } else {
      context.fail(401);
    }
  }

  private void apiGetPage(RoutingContext context) {
    int id = Integer.valueOf(context.request().getParam("id"));
    dbService.fetchPageById(id, reply -> {
      JsonObject response = new JsonObject();
      if (reply.succeeded()) {
        JsonObject dbObject = reply.result();
        if (dbObject.getBoolean("found")) {
          JsonObject payload = new JsonObject()
            .put("name", dbObject.getString("name"))
            .put("id", dbObject.getInteger("id"))
            .put("markdown", dbObject.getString("content"))
            .put("html", Processor.process(dbObject.getString("content")));
          response
            .put("success", true)
            .put("page", payload);
          context.response().setStatusCode(200);
        } else {
          context.response().setStatusCode(404);
          response
            .put("success", false)
            .put("error", "There is no page with ID " + id);
        }
      } else {
        response
          .put("success", false)
          .put("error", reply.cause().getMessage());
        context.response().setStatusCode(500);
      }
      context.response().putHeader("Content-Type", "application/json");
      context.response().end(response.encode());
    });
  }

  private void apiRoot(RoutingContext context) {
    dbService.fetchAllPagesData(reply -> {
      JsonObject response = new JsonObject();
      if (reply.succeeded()) {
        List<JsonObject> pages = reply.result()
          .stream()
          .map(obj -> new JsonObject()
            .put("id", obj.getInteger("ID"))
            .put("name", obj.getString("NAME")))
          .collect(Collectors.toList());
        response
          .put("success", true)
          .put("pages", pages);
        context.response().setStatusCode(200);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(response.encode());
      } else {
        response
          .put("success", false)
          .put("error", reply.cause().getMessage());
        context.response().setStatusCode(500);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(response.encode());
      }
    });
  }

  private void indexHandler(RoutingContext context) {
    context.user().rxIsAuthorised("create")
      .flatMap(canCreatePage -> {
        context.put("canCreatePage", canCreatePage);
        return dbService.rxFetchAllPages();
      })
      .flatMap(result -> {
        context.put("title", "Wiki home");
        context.put("pages", result.getList());
        context.put("username", context.user().principal().getString("username"));
        return templateEngine.rxRender(context, "templates/index.ftl");
      })
      .subscribe(markup -> {
        context.response().putHeader("Content-Type", "text/html");
        context.response().end(markup);
      }, context::fail);
  }

  private void pageRenderingHandler(RoutingContext context) {
    User user = context.user();
    user.rxIsAuthorised("update")
      .flatMap(canSavePage -> {
        context.put("canSavePage", canSavePage);
        return user.rxIsAuthorised("delete");
      })
      .flatMap(canDeletePage -> {
        String requestedPage = context.request().getParam("page");
        context.put("title", requestedPage);
        return dbService.rxFetchPage(requestedPage);
      })
      .flatMap(payLoad -> {
        boolean found = payLoad.getBoolean("found");
        String rawContent = payLoad.getString("rawContent", EMPTY_PAGE_MARKDOWN);
        context.put("id", payLoad.getInteger("id", -1));
        context.put("newPage", found ? "no" : "yes");
        context.put("rawContent", rawContent);
        context.put("content", Processor.process(rawContent));
        context.put("timestamp", new Date().toString());
        context.put("username", user.principal().getString("username"));
        return templateEngine.rxRender(context, "templates/page.ftl");
      })
      .subscribe(
        markup -> {
          context.response().putHeader("Content-Type", "text/html");
          context.response().end(markup);
        },
        context::fail);
  }

  private void loginHandler(RoutingContext context) {
    context.put("title", "Login");
    templateEngine.render(context, "templates/login.ftl", ar -> {
      if (ar.succeeded()) {
        context.response().putHeader("Content-Type", "text/html");
        context.response().end(ar.result());
      } else {
        context.fail(ar.cause());
      }
    });
  }

  private void pageUpdateHandler(RoutingContext context) {
    String title = context.request().getParam("title");
    boolean pageCreation = "yes".equals(context.request().getParam("newPage"));
    String markdown = context.request().getParam("markdown");
    checkAuthorised(context, pageCreation ? "create" : "update")
      .flatMap(v -> {
        if (pageCreation) {
          return dbService.rxCreatePage(title, markdown);
        } else {
          return dbService.rxSavePage(Integer.valueOf(context.request().getParam("id")), markdown);
        }
      })
      .subscribe(v -> {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/wiki/" + title);
        context.response().end();
      });
  }

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

  private void pageDeletionHandler(RoutingContext context) {
    checkAuthorised(context, "delete")
      .flatMap(canDelete -> dbService.rxDeletePage(Integer.valueOf(context.request().getParam("id"))))
      .subscribe(v -> {
        context.response().setStatusCode(303);
        context.response().putHeader("Location", "/");
        context.response().end();
      });
  }

  private void backupHandler(RoutingContext context) {
    checkAuthorised(context, "role:writer")
      .flatMap(v -> dbService.rxFetchAllPagesData())
      .map(pages -> {
        JsonObject filesObject = new JsonObject();
        pages.forEach(page -> {
          JsonObject fileObject = new JsonObject();
          filesObject.put(page.getString("NAME"), fileObject);
        });
        return new JsonObject()
          .put("files", filesObject)
          .put("description", "A wiki backup")
          .put("public", true);
      })
      .flatMap(body -> webClient
        .post(443, "api.github.com", "/gists")
        .putHeader("User-Agent", "vert-x3")
        .putHeader("Accept", "application/vnd.github.v3+json")
        .putHeader("Content-Type", "application/json")
        .as(BodyCodec.jsonObject()).rxSendJsonObject(body))
      .subscribe(response -> {
        if (response.statusCode() == 201) {
          context.put("backup_gist_url", response.body().getString("html_url"));
          indexHandler(context);
        } else {
          context.fail(502);
        }
      });
  }
}
