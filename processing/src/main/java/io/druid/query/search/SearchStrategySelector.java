/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.query.search;

import com.google.common.base.Supplier;
import com.google.inject.Inject;
import com.metamx.emitter.EmittingLogger;
import io.druid.java.util.common.ISE;
import io.druid.query.search.search.AutoStrategy;
import io.druid.query.search.search.CursorOnlyStrategy;
import io.druid.query.search.search.UseIndexesStrategy;
import io.druid.query.search.search.SearchQuery;
import io.druid.query.search.search.SearchQueryConfig;
import io.druid.query.search.search.SearchStrategy;

public class SearchStrategySelector
{
  private static final EmittingLogger log = new EmittingLogger(SearchStrategySelector.class);
  private final SearchQueryConfig config;

  @Inject
  public SearchStrategySelector(Supplier<SearchQueryConfig> configSupplier)
  {
    this.config = configSupplier.get();
  }

  public SearchStrategy strategize(SearchQuery query)
  {
    final String strategyString = config.withOverrides(query).getSearchStrategy();

    switch (strategyString) {
      case AutoStrategy.NAME:
        log.debug("Auto strategy is selected, query id [%s]", query.getId());
        return new AutoStrategy(query);
      case UseIndexesStrategy.NAME:
        log.debug("Index-only execution strategy is selected, query id [%s]", query.getId());
        return new UseIndexesStrategy(query);
      case CursorOnlyStrategy.NAME:
        log.debug("Cursor-based execution strategy is selected, query id [%s]", query.getId());
        return new CursorOnlyStrategy(query);
      default:
        throw new ISE("Unknown strategy[%s], query id [%s]", strategyString, query.getId());
    }
  }
}
