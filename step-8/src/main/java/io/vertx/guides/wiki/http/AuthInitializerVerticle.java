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

// tag::code[]
package io.vertx.guides.wiki.http;

import io.reactivex.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static io.vertx.guides.wiki.DatabaseConstants.*;

public class AuthInitializerVerticle extends AbstractVerticle {

  private final Logger logger = LoggerFactory.getLogger(AuthInitializerVerticle.class);

  @Override
  public void start() throws Exception {

    List<String> schemaCreation = Arrays.asList(
      "create table if not exists user (username varchar(255), password varchar(255), password_salt varchar(255));",
      "create table if not exists user_roles (username varchar(255), role varchar(255));",
      "create table if not exists roles_perms (role varchar(255), perm varchar(255));"
    );

    /*
     * Passwords:
     *    root / admin
     *    foo / bar
     *    bar / baz
     *    baz / baz
     */
    List<String> dataInit = Arrays.asList(
      "insert into user values ('root', 'C705F9EAD3406D0C17DDA3668A365D8991E6D1050C9A41329D9C67FC39E53437A39E83A9586E18C49AD10E41CBB71F0C06626741758E16F9B6C2BA4BEE74017E', '017DC3D7F89CD5E873B16E6CCE9A2307C8E3D9C5758741EEE49A899FFBC379D8');",
      "insert into user values ('foo', 'C3F0D72C1C3C8A11525B4563BAFF0E0F169114DE36796A595B78A373C522C0FF81BC2A683E2CB882A077847E8FD4DA09F1993072A4E9D7671313E4E5DB898F4E', '017DC3D7F89CD5E873B16E6CCE9A2307C8E3D9C5758741EEE49A899FFBC379D8');",
      "insert into user values ('bar', 'AEDD3E9FFCB847596A0596306A4303CC61C43D9904A0184951057D07D2FE2F36FA855C58EBCA9F3AEC9C65C46656F393E3D0F8711881F250D0D860F143A7A281', '017DC3D7F89CD5E873B16E6CCE9A2307C8E3D9C5758741EEE49A899FFBC379D8');",
      "insert into user values ('baz', 'AEDD3E9FFCB847596A0596306A4303CC61C43D9904A0184951057D07D2FE2F36FA855C58EBCA9F3AEC9C65C46656F393E3D0F8711881F250D0D860F143A7A281', '017DC3D7F89CD5E873B16E6CCE9A2307C8E3D9C5758741EEE49A899FFBC379D8');",
      "insert into roles_perms values ('editor', 'create');",
      "insert into roles_perms values ('editor', 'delete');",
      "insert into roles_perms values ('editor', 'update');",
      "insert into roles_perms values ('writer', 'update');",
      "insert into roles_perms values ('admin', 'create');",
      "insert into roles_perms values ('admin', 'delete');",
      "insert into roles_perms values ('admin', 'update');",
      "insert into user_roles values ('root', 'admin');",
      "insert into user_roles values ('foo', 'editor');",
      "insert into user_roles values ('foo', 'writer');",
      "insert into user_roles values ('bar', 'writer');"
    );

    JDBCClient dbClient = JDBCClient.createShared(vertx, new JsonObject()
      .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, DEFAULT_WIKIDB_JDBC_URL))
      .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, DEFAULT_WIKIDB_JDBC_DRIVER_CLASS))
      .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, DEFAULT_JDBC_MAX_POOL_SIZE)));

    dbClient.rxGetConnection().flatMapCompletable(connection -> connection
      .rxBatch(schemaCreation)
      .flatMap(rs -> connection.rxQuery("select count(*) from user;"))
      .flatMapCompletable(rs -> {
        if (rs.getResults().get(0).getInteger(0) == 0) {
          logger.info("Need to insert data");
          return connection
            .rxBatch(dataInit)
            .toCompletable();
        } else {
          logger.info("No need to insert data");
          return Completable.complete();
        }
      }).doFinally(connection::close)
    ).subscribe(
      () -> logger.info("Authentication database prepared"),
      t -> logger.error("Could not prepare the authentication database", t));
  }
}
// end::code[]
