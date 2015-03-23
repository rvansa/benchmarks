package org.jboss.perf.hibernate;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.transaction.TransactionMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

public class IspnBenchmark {
    private static final String NONTX_CACHE = "nonTxCache";
    private static final String SYNC_CACHE = "syncCache";
    private static final String XA_CACHE = "xaCache";

    @State(Scope.Benchmark)
    public static class IspnState {
        private DefaultCacheManager cacheManager;

        @Param(value = { NONTX_CACHE, SYNC_CACHE, XA_CACHE })
        private String cacheName;

        private Cache<Object, Object> cache;

        @Setup
        public void setup() {
            GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
            gcb.transport().transport(null).clearProperties();
            gcb.globalJmxStatistics().allowDuplicateDomains(true).enabled(false);

            ConfigurationBuilder nonTxBuilder = new ConfigurationBuilder();
            ConfigurationBuilder syncBuilder = new ConfigurationBuilder();
            syncBuilder.transaction().transactionMode(TransactionMode.TRANSACTIONAL).useSynchronization(true);
            ConfigurationBuilder xaBuilder = new ConfigurationBuilder();
            xaBuilder.transaction().transactionMode(TransactionMode.TRANSACTIONAL).useSynchronization(false);

            cacheManager = new DefaultCacheManager(gcb.build());
            cacheManager.defineConfiguration(NONTX_CACHE, nonTxBuilder.build());
            cacheManager.defineConfiguration(SYNC_CACHE, syncBuilder.build());
            cacheManager.defineConfiguration(XA_CACHE, xaBuilder.build());

            cache = cacheManager.getCache(cacheName);
            cache.put("key", "value");
        }

        @TearDown
        public void tearDown() {
            cacheManager.stop();
        }

        @Setup(value = Level.Iteration)
        public void reset() {
            Tracer.resetStats();
        }

        @TearDown(value = Level.Iteration)
        public void report() {
            Tracer.printStats(Boolean.getBoolean("tracer.printStackTraces"));
        }
    }

    @Benchmark
    public void testGet(IspnState state, Blackhole blackhole) {
        try {
            Object value = state.cache.get("key");
            blackhole.consume(value);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Benchmark
    public void testPut(IspnState state, Blackhole blackhole) {
        try {
            Object value = state.cache.put("key", "value2");
            blackhole.consume(value);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
