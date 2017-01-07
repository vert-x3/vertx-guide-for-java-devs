/*
 *  Copyright (c) 2016 Red Hat, Inc. and/or its affiliates.
 *  Copyright (c) 2016 INSA Lyon, CITI Laboratory.
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
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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
  private HttpClient httpClient;
  private String jwtTokenHeaderValue;

  @Before
  public void prepare(TestContext context) {
    vertx = Vertx.vertx();

    JsonObject dbConf = new JsonObject()
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);
    vertx.deployVerticle(new WikiDatabaseVerticle(),
      new DeploymentOptions().setConfig(dbConf), context.asyncAssertSuccess());

    vertx.deployVerticle(new HttpServerVerticle(), context.asyncAssertSuccess());

    httpClient = vertx.createHttpClient(new HttpClientOptions()
      .setDefaultHost("localhost")
      .setDefaultPort(8080)
      .setSsl(true)
      .setTrustOptions(new JksOptions().setPath("server-keystore.jks").setPassword("secret")));
  }

  @After
  public void finish(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void play_with_api(TestContext context) {
    Async async = context.async();

    Future<String> tokenRequest = Future.future();
    httpClient.get("/api/token", getResponse -> {
      context.assertEquals(200, getResponse.statusCode());
      getResponse.bodyHandler(buffer -> tokenRequest.complete(buffer.toString("UTF-8")));
    })
      .exceptionHandler(context::fail)
      .putHeader("login", "foo")
      .putHeader("password", "bar")
      .end();

    JsonObject page = new JsonObject()
      .put("name", "Sample")
      .put("markdown", "# A page");

    Future<JsonObject> postRequest = Future.future();
    tokenRequest.compose(token -> {
      jwtTokenHeaderValue = "Bearer " + token;
      httpClient.post("/api/pages", postResponse -> {
        postResponse.bodyHandler(buffer -> postRequest.complete(buffer.toJsonObject()));
      })
        .exceptionHandler(context::fail)
        .putHeader("Authorization", jwtTokenHeaderValue)
        .end(page.encode());
    }, postRequest);

    Future<JsonObject> getRequest = Future.future();
    postRequest.compose(h -> {
      httpClient.get("/api/pages", getResponse -> {
        getResponse.bodyHandler(buffer -> getRequest.complete(buffer.toJsonObject()));
      })
        .exceptionHandler(context::fail)
        .putHeader("Authorization", jwtTokenHeaderValue)
        .end();
    }, getRequest);

    Future<JsonObject> putRequest = Future.future();
    getRequest.compose(response -> {
      JsonArray array = response.getJsonArray("pages");
      context.assertEquals(1, array.size());
      context.assertEquals(0, array.getJsonObject(0).getInteger("id"));
      httpClient.put("/api/pages/0", putResponse -> {
        putResponse.bodyHandler(buffer -> putRequest.complete(buffer.toJsonObject()));
      })
        .exceptionHandler(context::fail)
        .putHeader("Authorization", jwtTokenHeaderValue)
        .end(new JsonObject()
          .put("id", 0)
          .put("markdown", "Oh Yeah!").encode());
    }, putRequest);

    Future<JsonObject> deleteRequest = Future.future();
    putRequest.compose(response -> {
      context.assertTrue(response.getBoolean("success"));
      httpClient.delete("/api/pages/0", delResponse -> {
        delResponse.bodyHandler(buffer -> deleteRequest.complete(buffer.toJsonObject()));
      })
        .exceptionHandler(context::fail)
        .putHeader("Authorization", jwtTokenHeaderValue)
        .end();
    }, deleteRequest);

    deleteRequest.compose(response -> {
      context.assertTrue(response.getBoolean("success"));
      async.complete();
    }, Future.failedFuture("Oh?"));

    async.awaitSuccess(5000);
  }
}
