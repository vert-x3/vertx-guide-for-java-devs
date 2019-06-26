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

import io.vertx.core.*;
import io.vertx.guides.wiki.database.WikiDatabaseVerticle;
import io.vertx.guides.wiki.http.AuthInitializerVerticle;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
// tag::code[]
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> promise) {

    Promise<String> dbDeploymentPromise = Promise.promise();
    vertx.deployVerticle(new WikiDatabaseVerticle(), dbDeploymentPromise);

    Future<String> authDeploymentFuture = dbDeploymentPromise.future().compose(id -> {
      Promise<String> deployPromise = Promise.promise();
      vertx.deployVerticle(new AuthInitializerVerticle(), deployPromise);
      return deployPromise.future();
    });

    authDeploymentFuture.compose(id -> {
      Promise<String> deployPromise = Promise.promise();
      vertx.deployVerticle("io.vertx.guides.wiki.http.HttpServerVerticle", new DeploymentOptions().setInstances(2), deployPromise);
      return deployPromise.future();
    });

    authDeploymentFuture.setHandler(ar -> {
      if (ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
  }
}
// end::code[]
