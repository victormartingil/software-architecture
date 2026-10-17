package com.reference.analytics;

import java.util.Map;
import org.apache.kafka.streams.state.RocksDBConfigSetter;
import org.rocksdb.*;

/** Bounds native memory per store; partition count still determines the total memory budget. */
public final class BoundedRocksDb implements RocksDBConfigSetter {
  private Cache cache;

  @Override
  public void setConfig(String storeName, Options options, Map<String, Object> configs) {
    cache = new LRUCache(4L * 1024 * 1024);
    var table = (BlockBasedTableConfig) options.tableFormatConfig();
    table.setBlockCache(cache).setCacheIndexAndFilterBlocks(true);
    options.setTableFormatConfig(table).setWriteBufferSize(1024 * 1024).setMaxWriteBufferNumber(2);
  }

  @Override
  public void close(String storeName, Options options) {
    if (cache != null) cache.close();
  }
}
