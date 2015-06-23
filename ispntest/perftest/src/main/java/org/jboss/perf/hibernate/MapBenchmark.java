package org.jboss.perf.hibernate;

import org.infinispan.Cache;
import org.infinispan.commons.equivalence.AnyEquivalence;
import org.infinispan.commons.util.concurrent.jdk8backported.ConcurrentParallelHashMapV8;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.container.DefaultDataContainer;
import org.infinispan.container.InternalEntryFactoryImpl;
import org.infinispan.eviction.impl.ActivationManagerImpl;
import org.infinispan.interceptors.locking.ClusteringDependentLogic;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.metadata.EmbeddedMetadata;
import org.infinispan.persistence.manager.PersistenceManagerImpl;
import org.infinispan.util.DefaultTimeService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class MapBenchmark {

   protected static final String KEY = "key";
   protected static final String VALUE = "value";

   @State(Scope.Benchmark)
   public static class ChmState {
      private ConcurrentParallelHashMapV8<String, Object> map
            = new ConcurrentParallelHashMapV8<>(AnyEquivalence.getInstance(Object.class), AnyEquivalence.getInstance());

      @Setup
      public void setup() {
         map.put(KEY, VALUE);
      }
   }

   @State(Scope.Benchmark)
   public static class DCState {
      private final AnyEquivalence<Object> equivalence = AnyEquivalence.getInstance();
      private DefaultDataContainer<String, Object> dataContainer = new DefaultDataContainer<>(1000, equivalence);
      private DefaultTimeService timeService;

      @Setup
      public void setup() {
         timeService = new DefaultTimeService();
         InternalEntryFactoryImpl entryFactory = new InternalEntryFactoryImpl();
         PersistenceManagerImpl persistenceManager = new PersistenceManagerImpl();
         ActivationManagerImpl activationManager = new ActivationManagerImpl();
         activationManager.inject(persistenceManager, new ConfigurationBuilder().build(), new ClusteringDependentLogic.LocalLogic());
         entryFactory.injectTimeService(timeService);
         dataContainer.initialize(null, null, entryFactory, activationManager, persistenceManager, timeService);

         dataContainer.put(KEY, VALUE, new EmbeddedMetadata.Builder().build());
      }
   }

   @State(Scope.Benchmark)
   public static class IspnState {
      private EmbeddedCacheManager cacheManager;
      private Cache cache;

      @Setup
      public void setup() {
         GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
         gcb.transport().transport(null).clearProperties();
         gcb.globalJmxStatistics().allowDuplicateDomains(true).enabled(false);

         ConfigurationBuilder nonTxBuilder = new ConfigurationBuilder();
         nonTxBuilder.jmxStatistics().enabled(false);

         cacheManager = new DefaultCacheManager(gcb.build());
         cacheManager.defineConfiguration("myCache", nonTxBuilder.build());
         cache = cacheManager.getCache("myCache");
         cache.put(KEY, VALUE);
      }
   }

   @Benchmark
   public void testChm(ChmState state, Blackhole blackhole) {
      Object value = state.map.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testDataContainer(DCState state, Blackhole blackhole) {
      Object value = state.dataContainer.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testCache(IspnState state, Blackhole blackhole) {
      Object value = state.cache.get(KEY);
      blackhole.consume(value);
   }

//   @Benchmark
//   public void testWallClockTime(DCState state, Blackhole blackhole) {
//      long time = state.timeService.wallClockTime();
//      blackhole.consume(time);
//   }
//
//   @Benchmark
//   public void testEquivalence(DCState state, Blackhole blackhole) {
//      blackhole.consume(state.equivalence.equals("foo", KEY));
//   }
}
