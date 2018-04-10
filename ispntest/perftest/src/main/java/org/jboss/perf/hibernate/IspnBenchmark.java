package org.jboss.perf.hibernate;

import com.arjuna.ats.jta.TransactionManager;
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

import java.lang.reflect.Method;

public class IspnBenchmark {
    private static final String NONTX_CACHE = "nonTxCache";
    private static final String SYNC_CACHE = "syncCache";
    private static final String XA_CACHE = "xaCache";
    private static final int N = 20;
    private static final String[] KEYS = new String[N];
    private static final Method RESET_STATS;
    private static final Method PRINT_STATS;

    static {
        for (int i = 0; i < N; ++i) KEYS[i] = "key" + i;
        Method resetStats = null, printStats = null;
        try {
            printStats = Class.forName("org.jboss.perf.hibernate.Tracer").getDeclaredMethod("printStats", boolean.class, int.class);
            resetStats = Class.forName("org.jboss.perf.hibernate.Tracer").getDeclaredMethod("resetStats");
        } catch (Exception e) {
        }
        PRINT_STATS = printStats;
        RESET_STATS = resetStats;
    }

    @State(Scope.Benchmark)
    public static class IspnState {
        private DefaultCacheManager cacheManager;
        private javax.transaction.TransactionManager transactionManager;

        @Param(value = { NONTX_CACHE, SYNC_CACHE, XA_CACHE })
        private String cacheName;

        private Cache<Object, Object> cache;
        private int invocations;

        @Setup
        public void setup() {
            GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
            gcb.transport().transport(null).clearProperties();
            gcb.globalJmxStatistics().allowDuplicateDomains(true).enabled(false);

            ConfigurationBuilder nonTxBuilder = new ConfigurationBuilder();
            nonTxBuilder.jmxStatistics().enabled(false);//.available(false);
            ConfigurationBuilder syncBuilder = new ConfigurationBuilder();
            syncBuilder.transaction().transactionMode(TransactionMode.TRANSACTIONAL).useSynchronization(true);//.notifications(false);
            syncBuilder.jmxStatistics().enabled(false);//.available(false);
            ConfigurationBuilder xaBuilder = new ConfigurationBuilder();
            xaBuilder.transaction().transactionMode(TransactionMode.TRANSACTIONAL).useSynchronization(false);//.notifications(false);
            xaBuilder.jmxStatistics().enabled(false);//.available(false);

            cacheManager = new DefaultCacheManager(gcb.build());
            cacheManager.defineConfiguration(NONTX_CACHE, nonTxBuilder.build());
            cacheManager.defineConfiguration(SYNC_CACHE, syncBuilder.build());
            cacheManager.defineConfiguration(XA_CACHE, xaBuilder.build());

            transactionManager = TransactionManager.transactionManager();

            cache = cacheManager.getCache(cacheName);
            cache.put("key", "value");
        }

        @TearDown
        public void tearDown() {
            cacheManager.stop();
        }

        @Setup(value = Level.Iteration)
        public void reset() {
            invocations = 0;
            if (RESET_STATS != null) {
                try {
                    RESET_STATS.invoke(null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @TearDown(value = Level.Iteration)
        public void report() {
            if (PRINT_STATS != null) {
                try {
                    PRINT_STATS.invoke(null, Boolean.getBoolean("tracer.printStackTraces"), invocations);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Benchmark
    public void testGet(IspnState state, Blackhole blackhole) {
        state.invocations++;
        Object value = state.cache.get("key");
        blackhole.consume(value);
    }

    @Benchmark
    public void testPutImplicit(IspnState state, Blackhole blackhole) {
        state.invocations++;
        try {
            Object value = state.cache.put("key", "value2");
            blackhole.consume(value);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Benchmark
    public void testPutExplicit(IspnState state, Blackhole blackhole) {
        state.invocations++;
        try {
            state.transactionManager.begin();
            Object value = state.cache.put("key", "value2");
            blackhole.consume(value);
            state.transactionManager.commit();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Benchmark
    public void testPut5Explicit(IspnState state, Blackhole blackhole) {
        state.invocations++;
        try {
            state.transactionManager.begin();
            for (int i = 0; i < 5; ++i) {
                Object value = state.cache.put(KEYS[i], "value2");
                blackhole.consume(value);
            }
            state.transactionManager.commit();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Benchmark
    public void testPut20Explicit(IspnState state, Blackhole blackhole) {
        state.invocations++;
        try {
            state.transactionManager.begin();
            for (int i = 0; i < 20; ++i) {
                Object value = state.cache.put(KEYS[i], "value2");
                blackhole.consume(value);
            }
            state.transactionManager.commit();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
