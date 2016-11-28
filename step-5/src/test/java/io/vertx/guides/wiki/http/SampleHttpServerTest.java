package io.vertx.guides.wiki.http;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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

        HttpClient httpClient = vertx.createHttpClient();

        httpClient.get(8080, "localhost", "/", response -> {
          response.exceptionHandler(throwable -> async.resolve(Future.failedFuture(throwable)));
          response.bodyHandler(body -> {
            context.assertTrue(response.headers().contains("Content-Type"));
            context.assertEquals("text/plain", response.getHeader("Content-Type"));
            context.assertEquals("Ok", body.toString());
            httpClient.close();
            async.complete();
          });
        }).exceptionHandler(throwable -> async.resolve(Future.failedFuture(throwable)))
          .end();

      }));
  }
}
