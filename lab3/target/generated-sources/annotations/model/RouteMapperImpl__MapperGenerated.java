package model;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.internal.mapper.DaoCacheKey;
import com.datastax.oss.driver.internal.mapper.DefaultMapperContext;
import java.lang.Override;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Do not instantiate this class directly, use {@link RouteMapperBuilder} instead.
 *
 * <p>Generated by the DataStax driver mapper, do not edit directly.
 */
public class RouteMapperImpl__MapperGenerated implements RouteMapper {
  private final DefaultMapperContext context;

  private final ConcurrentMap<DaoCacheKey, RouteDao> routeDaoCache = new ConcurrentHashMap<>();

  public RouteMapperImpl__MapperGenerated(DefaultMapperContext context) {
    this.context = context;
  }

  @Override
  public RouteDao routeDao(CqlIdentifier keyspace) {
    DaoCacheKey key = new DaoCacheKey(keyspace, (CqlIdentifier)null, null, null);
    return routeDaoCache.computeIfAbsent(key, k -> RouteDaoImpl__MapperGenerated.init(context.withDaoParameters(k.getKeyspaceId(), k.getTableId(), k.getExecutionProfileName(), k.getExecutionProfile())));
  }
}
