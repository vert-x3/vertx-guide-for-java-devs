package io.vertx.guides.wiki.database;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
@RunWith(VertxUnitRunner.class)
public class WikiDatabaseVerticleTest {

  private Vertx vertx;

  @Before
  public void prepare(TestContext context) throws InterruptedException {
    vertx = Vertx.vertx();
    JsonObject conf = new JsonObject()
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
      .put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);
    vertx.deployVerticle(new WikiDatabaseVerticle(), new DeploymentOptions().setConfig(conf), context.asyncAssertSuccess());
  }

  @After
  public void finish(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void foo(TestContext context) {

  }
}
