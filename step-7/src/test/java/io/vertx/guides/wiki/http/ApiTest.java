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

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.guides.wiki.database.WikiDatabaseVerticle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
@RunWith(VertxUnitRunner.class)
public class ApiTest {

  private Vertx vertx;
  private WebClient webClient;

  // tag::tokenField[]
  private String jwtTokenHeaderValue;
  // end::tokenField[]

  @Before
  public void prepare(TestContext context) {
    vertx = Vertx.vertx();

    // tag::prepare-db[]
    JsonObject dbConf = new JsonObject()
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

    vertx.deployVerticle(new AuthInitializerVerticle(),
      new DeploymentOptions().setConfig(dbConf), context.asyncAssertSuccess());

    vertx.deployVerticle(new WikiDatabaseVerticle(),
      new DeploymentOptions().setConfig(dbConf), context.asyncAssertSuccess());
    // end::prepare-db[]

    vertx.deployVerticle(new HttpServerVerticle(), context.asyncAssertSuccess());

    // tag::test-https[]
    webClient = WebClient.create(vertx, new WebClientOptions()
      .setDefaultHost("localhost")
      .setDefaultPort(8080)
      .setSsl(true) // <1>
      .setTrustOptions(new JksOptions().setPath("server-keystore.jks").setPassword("secret"))); // <2>
    // end::test-https[]
  }

  @After
  public void finish(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  // tag::fetch-token[]
  @Test
  public void play_with_api(TestContext context) {
    Async async = context.async();

    Promise<HttpResponse<String>> tokenPromise = Promise.promise();
    webClient.get("/api/token")
      .putHeader("login", "foo")  // <1>
      .putHeader("password", "bar")
      .as(BodyCodec.string())   // <2>
      .send(tokenPromise);
    Future<HttpResponse<String>> tokenFuture = tokenPromise.future(); // <3>

    JsonObject page = new JsonObject()
      .put("name", "Sample")
      .put("markdown", "# A page");
    // (...)
    // end::fetch-token[]

    // tag::use-token[]
    Future<HttpResponse<JsonObject>> postPageFuture = tokenFuture.compose(tokenResponse -> {
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      jwtTokenHeaderValue = "Bearer " + tokenResponse.body();   // <1>
      webClient.post("/api/pages")
        .putHeader("Authorization", jwtTokenHeaderValue)  // <2>
        .as(BodyCodec.jsonObject())
        .sendJsonObject(page, promise);
      return promise.future();
    });

    Future<HttpResponse<JsonObject>> getPageFuture = postPageFuture.compose(resp -> {
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      webClient.get("/api/pages")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .send(promise);
      return promise.future();
    });
    // (...)
    // end::use-token[]

    Future<HttpResponse<JsonObject>> updatePageFuture = getPageFuture.compose(resp -> {
      JsonArray array = resp.body().getJsonArray("pages");
      context.assertEquals(1, array.size());
      context.assertEquals(0, array.getJsonObject(0).getInteger("id"));
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      JsonObject data = new JsonObject()
        .put("id", 0)
        .put("markdown", "Oh Yeah!");
      webClient.put("/api/pages/0")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .sendJsonObject(data, promise);
      return promise.future();
    });

    Future<HttpResponse<JsonObject>> deletePageFuture = updatePageFuture.compose(resp -> {
      context.assertTrue(resp.body().getBoolean("success"));
      Promise<HttpResponse<JsonObject>> promise = Promise.promise();
      webClient.delete("/api/pages/0")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .send(promise);
      return promise.future();
    });

    deletePageFuture.setHandler(ar -> {
      if (ar.succeeded()) {
        context.assertTrue(ar.result().body().getBoolean("success"));
        async.complete();
      } else {
        context.fail(ar.cause());
      }
    });

    async.awaitSuccess(5000);
  }
}
