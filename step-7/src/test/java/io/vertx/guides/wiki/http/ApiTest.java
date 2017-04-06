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

    JsonObject dbConf = new JsonObject()
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);
    vertx.deployVerticle(new WikiDatabaseVerticle(),
      new DeploymentOptions().setConfig(dbConf), context.asyncAssertSuccess());

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

    Future<String> tokenRequest = Future.future();
    webClient.get("/api/token")
      .putHeader("login", "foo")  // <1>
      .putHeader("password", "bar")
      .as(BodyCodec.string()) // <2>
      .send(ar -> {
        if (ar.succeeded()) {
          tokenRequest.complete(ar.result().body());  // <3>
        } else {
          context.fail(ar.cause());
        }
      });
      // (...)
  // end::fetch-token[]

    JsonObject page = new JsonObject()
      .put("name", "Sample")
      .put("markdown", "# A page");

    // tag::use-token[]
    Future<JsonObject> postRequest = Future.future();
    tokenRequest.compose(token -> {
      jwtTokenHeaderValue = "Bearer " + token;  // <1>
      webClient.post("/api/pages")
        .putHeader("Authorization", jwtTokenHeaderValue)  // <2>
        .as(BodyCodec.jsonObject())
        .sendJsonObject(page, ar -> {
          if (ar.succeeded()) {
            HttpResponse<JsonObject> postResponse = ar.result();
            postRequest.complete(postResponse.body());
          } else {
            context.fail(ar.cause());
          }
        });
    }, postRequest);

    Future<JsonObject> getRequest = Future.future();
    postRequest.compose(h -> {
      webClient.get("/api/pages")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .send(ar -> {
          if (ar.succeeded()) {
            HttpResponse<JsonObject> getResponse = ar.result();
            getRequest.complete(getResponse.body());
          } else {
            context.fail(ar.cause());
          }
        });
    }, getRequest);
    // (...)
    // end::use-token[]

    Future<JsonObject> putRequest = Future.future();
    getRequest.compose(response -> {
      JsonArray array = response.getJsonArray("pages");
      context.assertEquals(1, array.size());
      context.assertEquals(0, array.getJsonObject(0).getInteger("id"));
      webClient.put("/api/pages/0")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .sendJsonObject(new JsonObject()
          .put("id", 0)
          .put("markdown", "Oh Yeah!"), ar -> {
          if (ar.succeeded()) {
            HttpResponse<JsonObject> putResponse = ar.result();
            putRequest.complete(putResponse.body());
          } else {
            context.fail(ar.cause());
          }
        });
    }, putRequest);

    Future<JsonObject> deleteRequest = Future.future();
    putRequest.compose(response -> {
      context.assertTrue(response.getBoolean("success"));
      webClient.delete("/api/pages/0")
        .putHeader("Authorization", jwtTokenHeaderValue)
        .as(BodyCodec.jsonObject())
        .send(ar -> {
          if (ar.succeeded()) {
            HttpResponse<JsonObject> delResponse = ar.result();
            deleteRequest.complete(delResponse.body());
          } else {
            context.fail(ar.cause());
          }
        });
    }, deleteRequest);

    deleteRequest.compose(response -> {
      context.assertTrue(response.getBoolean("success"));
      async.complete();
    }, Future.failedFuture("Oh?"));

    async.awaitSuccess(5000);
  }
}
