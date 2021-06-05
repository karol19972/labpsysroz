package model;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.internal.mapper.DaoCacheKey;
import com.datastax.oss.driver.internal.mapper.DefaultMapperContext;
import java.lang.Override;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Do not instantiate this class directly, use {@link CarMapperBuilder} instead.
 *
 * <p>Generated by the DataStax driver mapper, do not edit directly.
 */
public class CarMapperImpl__MapperGenerated implements CarMapper {
  private final DefaultMapperContext context;

  private final ConcurrentMap<DaoCacheKey, CarDao> carDaoCache = new ConcurrentHashMap<>();

  public CarMapperImpl__MapperGenerated(DefaultMapperContext context) {
    this.context = context;
  }

  @Override
  public CarDao carDao(CqlIdentifier keyspace) {
    DaoCacheKey key = new DaoCacheKey(keyspace, (CqlIdentifier)null, null, null);
    return carDaoCache.computeIfAbsent(key, k -> CarDaoImpl__MapperGenerated.init(context.withDaoParameters(k.getKeyspaceId(), k.getTableId(), k.getExecutionProfileName(), k.getExecutionProfile())));
  }
}
