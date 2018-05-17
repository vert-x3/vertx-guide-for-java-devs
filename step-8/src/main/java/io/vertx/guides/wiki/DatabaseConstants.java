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
package io.vertx.guides.wiki;

public interface DatabaseConstants {

  String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
  String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class";
  String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";

  String DEFAULT_WIKIDB_JDBC_URL = "jdbc:hsqldb:file:db/wiki";
  String DEFAULT_WIKIDB_JDBC_DRIVER_CLASS = "org.hsqldb.jdbcDriver";
  int DEFAULT_JDBC_MAX_POOL_SIZE = 30;
}
// end::code[]
