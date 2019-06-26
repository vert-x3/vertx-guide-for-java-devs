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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
// tag::main[]
public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> promise) throws Exception {

    Promise<String> dbVerticleDeployment = Promise.promise();  // <1>
    vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment);  // <2>

    dbVerticleDeployment.future().compose(id -> {  // <3>

      Promise<String> httpVerticleDeployment = Promise.promise();
      vertx.deployVerticle(
        "io.vertx.guides.wiki.HttpServerVerticle",  // <4>
        new DeploymentOptions().setInstances(2),    // <5>
        httpVerticleDeployment);

      return httpVerticleDeployment.future();  // <6>

    }).setHandler(ar -> {   // <7>
      if (ar.succeeded()) {
        promise.complete();
      } else {
        promise.fail(ar.cause());
      }
    });
  }
}
// end::main[]
