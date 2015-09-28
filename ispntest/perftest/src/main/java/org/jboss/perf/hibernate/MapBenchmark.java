package org.jboss.perf.hibernate;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.cache.impl.CacheImpl;
import org.infinispan.cache.impl.SimpleCacheImpl;
import org.infinispan.commons.equivalence.AnyEquivalence;
import org.infinispan.commons.util.concurrent.jdk8backported.ConcurrentParallelHashMapV8;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.container.DataContainer;
import org.infinispan.container.DefaultDataContainer;
import org.infinispan.container.InternalEntryFactory;
import org.infinispan.container.InternalEntryFactoryImpl;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.eviction.impl.ActivationManagerImpl;
import org.infinispan.functional.decorators.FunctionalAdvancedCache;
import org.infinispan.functional.impl.FunctionalMapImpl;
import org.infinispan.interceptors.locking.ClusteringDependentLogic;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.metadata.EmbeddedMetadata;
import org.infinispan.metadata.Metadata;
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

   static {
      System.setProperty("com.arjuna.ats.arjuna.common.propertiesFile", "default-jbossts-properties.xml");
   }

   protected static final String KEY = "key";
   protected static final String VALUE = "value";
   protected static final Metadata METADATA = new EmbeddedMetadata.Builder().build();

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
         dataContainer.initialize(null, null, entryFactory, activationManager, persistenceManager, timeService, null, null);

         dataContainer.put(KEY, VALUE, new EmbeddedMetadata.Builder().build());
      }
   }

   @State(Scope.Benchmark)
   public static class IspnState {
      private EmbeddedCacheManager cacheManager;
      private CacheImpl cache;

      @Setup
      public void setup() {
         GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
         gcb.transport().transport(null).clearProperties();
         gcb.globalJmxStatistics().allowDuplicateDomains(true).enabled(false);

         ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
         configurationBuilder.jmxStatistics().available(false).enabled(false);

         cacheManager = new DefaultCacheManager(gcb.build());
         cacheManager.defineConfiguration("myCache", configurationBuilder.build());
         cache = (CacheImpl) cacheManager.getCache("myCache");
         cache.put(KEY, VALUE);
      }
   }

   @State(Scope.Benchmark)
   public static class SimpleIspnState {
      private EmbeddedCacheManager cacheManager;
      private SimpleCacheImpl cache;

      @Setup
      public void setup() {
         GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
         gcb.transport().transport(null).clearProperties();
         gcb.globalJmxStatistics().allowDuplicateDomains(true).enabled(false);

         ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
         configurationBuilder.jmxStatistics().available(false).enabled(false);
         configurationBuilder.simpleCache(true);

         cacheManager = new DefaultCacheManager(gcb.build());
         cacheManager.defineConfiguration("myCache", configurationBuilder.build());
         cache = (SimpleCacheImpl) cacheManager.getCache("myCache");
         cache.put(KEY, VALUE);
      }
   }

   @State(Scope.Benchmark)
   public static class FunctionalIspnState {
      private EmbeddedCacheManager cacheManager;
      private AdvancedCache cache;

      @Setup
      public void setup() {
         GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
         gcb.transport().transport(null).clearProperties();
         gcb.globalJmxStatistics().allowDuplicateDomains(true).enabled(false);

         ConfigurationBuilder nonTxBuilder = new ConfigurationBuilder();
         nonTxBuilder.jmxStatistics().available(false).enabled(false);

         cacheManager = new DefaultCacheManager(gcb.build());
         cacheManager.defineConfiguration("myCache", nonTxBuilder.build());
         AdvancedCache cache = cacheManager.getCache("myCache").getAdvancedCache();
         this.cache = FunctionalAdvancedCache.create(cache);
         this.cache.put(KEY, VALUE);
      }
   }

   @Benchmark
   public void testGetChm(ChmState state, Blackhole blackhole) {
      Object value = state.map.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetDataContainer(DCState state, Blackhole blackhole) {
      Object value = state.dataContainer.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetRegularCache(IspnState state, Blackhole blackhole) {
      Object value = state.cache.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetSimpleCache(SimpleIspnState state, Blackhole blackhole) {
      Object value = state.cache.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetFunctionalCache(FunctionalIspnState state, Blackhole blackhole) {
      Object value = state.cache.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutChm(ChmState state, Blackhole blackhole) {
      Object value = state.map.put(KEY, VALUE);
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutDataContainer(DCState state, Blackhole blackhole) {
      state.dataContainer.put(KEY, VALUE, METADATA);
   }

   @Benchmark
   public void testPutDataContainer2(DCState state, Blackhole blackhole) {
      InternalCacheEntry<String, Object> newEntry = state.dataContainer.compute(KEY, new DataContainer.ComputeAction<String, Object>() {
         @Override
         public InternalCacheEntry<String, Object> compute(String key, InternalCacheEntry<String, Object> oldEntry, InternalEntryFactory factory) {
            return factory.create(KEY, (Object) VALUE, METADATA);
         }
      });
      blackhole.consume(newEntry);
   }

   @Benchmark
   public void testPutRegularCache(IspnState state, Blackhole blackhole) {
      Object value = state.cache.put(KEY, VALUE);
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutSimpleCache(SimpleIspnState state, Blackhole blackhole) {
      Object value = state.cache.put(KEY, VALUE);
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutFunctionalCache(FunctionalIspnState state, Blackhole blackhole) {
      Object value = state.cache.put(KEY, VALUE);
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
