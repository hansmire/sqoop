/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sqoop.repository;

import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.pool.KeyedObjectPoolFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPoolFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.log4j.Logger;
import org.apache.sqoop.core.Context;
import org.apache.sqoop.core.SqoopConfiguration;
import org.apache.sqoop.core.SqoopException;
import org.apache.sqoop.utils.ClassLoadingUtils;


public class JdbcRepositoryProvider implements RepositoryProvider {

  private static final Logger LOG =
      Logger.getLogger(JdbcRepositoryProvider.class);

  private JdbcRepositoryContext repoContext;

  private JdbcRepositoryHandler handler;
  private GenericObjectPool connectionPool;
  private KeyedObjectPoolFactory statementPool;
  private DataSource dataSource;

  public JdbcRepositoryProvider() {
    // Default constructor
  }

  @Override
  public synchronized void initialize(Context context) {
    repoContext = new JdbcRepositoryContext(SqoopConfiguration.getContext());

    String jdbcHandlerClassName = repoContext.getHandlerClassName();

    Class<?> handlerClass = ClassLoadingUtils.loadClass(jdbcHandlerClassName);

    if (handlerClass == null) {
      throw new SqoopException(RepositoryError.JDBCREPO_0001,
          jdbcHandlerClassName);
    }

    try {
      handler = (JdbcRepositoryHandler) handlerClass.newInstance();
    } catch (Exception ex) {
      throw new SqoopException(RepositoryError.JDBCREPO_0001,
          jdbcHandlerClassName, ex);
    }

    String connectUrl = repoContext.getConnectionUrl();
    if (connectUrl == null || connectUrl.trim().length() == 0) {
      throw new SqoopException(RepositoryError.JDBCREPO_0002);
    }

    String jdbcDriverClassName = repoContext.getDriverClass();
    if (jdbcDriverClassName == null || jdbcDriverClassName.trim().length() == 0)
    {
      throw new SqoopException(RepositoryError.JDBCREPO_0003);
    }

    // Initialize a datasource
    if (ClassLoadingUtils.loadClass(jdbcDriverClassName) == null) {
      throw new SqoopException(RepositoryError.JDBCREPO_0003,
          jdbcDriverClassName);
    }

    Properties jdbcProps = repoContext.getConnectionProperties();

    ConnectionFactory connFactory =
        new DriverManagerConnectionFactory(connectUrl, jdbcProps);

    connectionPool = new GenericObjectPool();
    connectionPool.setMaxActive(repoContext.getMaximumConnections());

    statementPool = new GenericKeyedObjectPoolFactory(null);

    // creating the factor automatically wires the connection pool
    new PoolableConnectionFactory(connFactory, connectionPool, statementPool,
        /* FIXME validation query */null, false, false,
        repoContext.getTransactionIsolation().getCode());

    dataSource = new PoolingDataSource(connectionPool);

    handler.initialize(dataSource, repoContext);

    LOG.info("JdbcRepository initialized.");
  }

  @Override
  public synchronized Repository getRepository() {
    return handler.getRepository();
  }
}