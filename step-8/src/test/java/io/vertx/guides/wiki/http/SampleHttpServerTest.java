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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
@RunWith(VertxUnitRunner.class)
public class SampleHttpServerTest {

  private Vertx vertx;

  @Before
  public void prepare() {
    vertx = Vertx.vertx();
  }

  @After
  public void finish(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void start_http_server(TestContext context) {
    Async async = context.async();

    vertx
      .createHttpServer().requestHandler(req ->
      req.response().putHeader("Content-Type", "text/plain").end("Ok"))
      .listen(8080, context.asyncAssertSuccess(server -> {

        WebClient webClient = WebClient.create(vertx);

        webClient.get(8080, "localhost", "/").send(ar -> {
          if (ar.succeeded()) {
            HttpResponse<Buffer> response = ar.result();
            context.assertTrue(response.headers().contains("Content-Type"));
            context.assertEquals("text/plain", response.getHeader("Content-Type"));
            context.assertEquals("Ok", response.bodyAsString());
            webClient.close();
            async.complete();
          } else {
            async.resolve(Future.failedFuture(ar.cause()));
          }
        });
      }));
  }
}
