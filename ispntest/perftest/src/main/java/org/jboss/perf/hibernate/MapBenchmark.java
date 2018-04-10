package org.jboss.perf.hibernate;

import org.cache2k.CacheBuilder;
import org.infinispan.AdvancedCache;
import org.infinispan.cache.impl.CacheImpl;
import org.infinispan.cache.impl.SimpleCacheImpl;
import org.infinispan.commons.equivalence.AnyEquivalence;
import org.infinispan.commons.util.concurrent.jdk8backported.BoundedEquivalentConcurrentHashMapV8;
import org.infinispan.commons.util.concurrent.jdk8backported.ConcurrentParallelHashMapV8;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.container.DataContainer;
import org.infinispan.container.DefaultDataContainer;
import org.infinispan.container.InternalEntryFactory;
import org.infinispan.container.InternalEntryFactoryImpl;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.eviction.EvictionManager;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.eviction.EvictionThreadPolicy;
import org.infinispan.eviction.EvictionType;
import org.infinispan.eviction.impl.ActivationManagerImpl;
import org.infinispan.eviction.impl.PassivationManagerImpl;
import org.infinispan.functional.decorators.FunctionalAdvancedCache;
import org.infinispan.interceptors.locking.ClusteringDependentLogic;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.metadata.EmbeddedMetadata;
import org.infinispan.metadata.Metadata;
import org.infinispan.persistence.manager.PersistenceManagerImpl;
import org.infinispan.util.DefaultTimeService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

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

   @State(Scope.Thread)
   public static class ThreadState {
      int counter;
      int stride;

      @Setup
      public void initStride() {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         for (;;) {
            // pick a random even number between 3 and 1999
            stride = random.nextInt(1, 1000) * 2 + 1;
            if (BigInteger.valueOf(stride).isProbablePrime(10)) {
               break;
            }
         }
         counter = stride;
      }
   }

   @State(Scope.Benchmark)
   public static class BaseState {
      protected String keys[];
      protected String values[];

      @Param({"0", "1000"})
      public int maxSize;

      @Param({"2048"})
      public int numKeys;

      @Setup
      public void initKeys() {
         keys = new String[numKeys];
         values = new String[numKeys];
         for (int i = 0; i < numKeys; ++i) {
            keys[i] = "key" + i;
            values[i] = "value" + i;
            keys[i].hashCode(); // cache the hash
         }
      }

      public String nextKey(ThreadState threadState) {
         int pos = threadState.counter;
         String next = keys[pos];
         threadState.counter = (pos + threadState.stride) % keys.length;
         return next;
      }

      public String nextValue(ThreadState threadState) {
         return values[threadState.counter];
      }
   }

   @State(Scope.Benchmark)
   public static class ChmState extends BaseState {
      private ConcurrentMap<String, Object> map;

      @Setup
      public void setup(){
         if (maxSize > 0) {
            map = new BoundedEquivalentConcurrentHashMapV8<>(maxSize, AnyEquivalence.getInstance(), AnyEquivalence.getInstance());
         } else {
            map = new ConcurrentParallelHashMapV8<>(AnyEquivalence.getInstance(), AnyEquivalence.getInstance());
         }
         int i = 0;
         for (String key : keys) {
            map.put(key, values[i++]);
         }
         map.put(KEY, VALUE);
      }
   }

   @State(Scope.Benchmark)
   public static class DCState extends BaseState {
      private final AnyEquivalence<Object> equivalence = AnyEquivalence.getInstance();
      private DefaultDataContainer<String, Object> dataContainer;
      private DefaultTimeService timeService;

      @Setup
      public void setup() {
         if (maxSize > 0) {
            dataContainer = DefaultDataContainer.boundedDataContainer(1000, maxSize, EvictionStrategy.LIRS, EvictionThreadPolicy.DEFAULT, equivalence, EvictionType.COUNT);
         } else {
            dataContainer = DefaultDataContainer.unBoundedDataContainer(1000, equivalence);
         }
         timeService = new DefaultTimeService();
         EvictionManager evictionManager = new EvictionManager() {
            @Override
            public void onEntryEviction(Map evicted) {
            }
         };
         PassivationManagerImpl passivationManager = new PassivationManagerImpl();
         InternalEntryFactoryImpl entryFactory = new InternalEntryFactoryImpl();
         PersistenceManagerImpl persistenceManager = new PersistenceManagerImpl();
         ActivationManagerImpl activationManager = new ActivationManagerImpl();
         activationManager.inject(persistenceManager, new ConfigurationBuilder().build(), new ClusteringDependentLogic.LocalLogic());
         entryFactory.injectTimeService(timeService);
         dataContainer.initialize(evictionManager, passivationManager, entryFactory, activationManager, persistenceManager, timeService, null, null);

         int i = 0;
         for (String key : keys) {
            dataContainer.put(key, values[i++], METADATA);
         }
         dataContainer.put(KEY, VALUE, METADATA);
      }
   }

   public static class IspnBaseState extends BaseState {
      protected static final String CACHE_NAME = "myCache";
      protected EmbeddedCacheManager cacheManager;

      protected void setup() {
         GlobalConfigurationBuilder gcb = getGlobalConfigurationBuilder();
         ConfigurationBuilder configurationBuilder = getConfigurationBuilder();

         cacheManager = new DefaultCacheManager(gcb.build());
         cacheManager.defineConfiguration(CACHE_NAME, configurationBuilder.build());
      }

      protected ConfigurationBuilder getConfigurationBuilder() {
         ConfigurationBuilder builder = new ConfigurationBuilder();
         builder.jmxStatistics().available(false).enabled(false);
         if (maxSize > 0) {
            builder.eviction().type(EvictionType.COUNT).size(maxSize).strategy(EvictionStrategy.LIRS);
         }
         return builder;
      }

      protected GlobalConfigurationBuilder getGlobalConfigurationBuilder() {
         GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
         gcb.transport().transport(null).clearProperties();
         gcb.globalJmxStatistics().allowDuplicateDomains(true).enabled(false);
         return gcb;
      }
   }

   @State(Scope.Benchmark)
   public static class IspnState extends IspnBaseState {
      private CacheImpl cache;

      @Setup
      public void setup() {
         super.setup();
         cache = (CacheImpl) cacheManager.getCache(CACHE_NAME);
         int i = 0;
         for (String key : keys) {
            cache.put(key, values[i++]);
         }
         cache.put(KEY, VALUE);
      }
   }

   @State(Scope.Benchmark)
   public static class SimpleIspnState extends IspnBaseState {
      private SimpleCacheImpl cache;

      @Setup(Level.Iteration)
      public void setup() {
         super.setup();
         cache = (SimpleCacheImpl) cacheManager.getCache(CACHE_NAME);
         int i = 0;
         for (String key : keys) {
            cache.put(key, values[i++]);
         }
         cache.put(KEY, VALUE);
      }

      @Override
      protected ConfigurationBuilder getConfigurationBuilder() {
         ConfigurationBuilder builder = super.getConfigurationBuilder();
         builder.simpleCache(true);
         return builder;
      }
   }

   @State(Scope.Benchmark)
   public static class FunctionalIspnState extends IspnBaseState {
      private AdvancedCache cache;

      @Setup
      public void setup() {
         super.setup();
         AdvancedCache cache = cacheManager.getCache(CACHE_NAME).getAdvancedCache();
         this.cache = FunctionalAdvancedCache.create(cache);
         int i = 0;
         for (String key : keys) {
            this.cache.put(key, values[i++]);
         }
         cache.put(KEY, VALUE);
      }
   }

   @State(Scope.Benchmark)
   public static class Cache2kState extends BaseState {
      private org.cache2k.Cache<Object, Object> cache;
      private static int nameCounter = 0;

      @Setup
      public void setup() {
         CacheBuilder<Object, Object> builder = CacheBuilder.newCache(Object.class, Object.class)
               .name("myCache" + nameCounter++).eternal(true);
         if (maxSize > 0) {
            builder.maxSize(maxSize);
         }
         cache = builder.build();
         int i = 0;
         for (String key : keys) {
            cache.put(key, values[i++]);
         }
         cache.put(KEY, VALUE);
      }

      @TearDown
      public void shutdown() {
         cache.close();
      }
   }

   @Benchmark
   public void testGetOneChm(ChmState state, Blackhole blackhole) {
      Object value = state.map.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetOneDataContainer(DCState state, Blackhole blackhole) {
      Object value = state.dataContainer.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetOneRegularCache(IspnState state, Blackhole blackhole) {
      Object value = state.cache.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetOneSimpleCache(SimpleIspnState state, Blackhole blackhole) {
      Object value = state.cache.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetOneFunctionalCache(FunctionalIspnState state, Blackhole blackhole) {
      Object value = state.cache.get(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetOneCache2k(Cache2kState state, Blackhole blackhole) {
      Object value = state.cache.peek(KEY);
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetAnyChm(ChmState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.map.get(state.nextKey(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetAnyDataContainer(DCState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.dataContainer.get(state.nextKey(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetAnyRegularCache(IspnState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.cache.get(state.nextKey(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetAnySimpleCache(SimpleIspnState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.cache.get(state.nextKey(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetAnyFunctionalCache(FunctionalIspnState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.cache.get(state.nextKey(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testGetAnyCache2k(Cache2kState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.cache.peek(state.nextKey(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutOneChm(ChmState state, Blackhole blackhole) {
      Object value = state.map.put(KEY, VALUE);
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutOneDataContainer(DCState state, Blackhole blackhole) {
      state.dataContainer.put(KEY, VALUE, METADATA);
   }

   @Benchmark
   public void testPutOneDataContainer2(DCState state, Blackhole blackhole) {
      InternalCacheEntry<String, Object> newEntry = state.dataContainer.compute(KEY, new DataContainer.ComputeAction<String, Object>() {
         @Override
         public InternalCacheEntry<String, Object> compute(String key, InternalCacheEntry<String, Object> oldEntry, InternalEntryFactory factory) {
            return factory.create(KEY, (Object) VALUE, METADATA);
         }
      });
      blackhole.consume(newEntry);
   }

   @Benchmark
   public void testPutOneRegularCache(IspnState state, Blackhole blackhole) {
      Object value = state.cache.put(KEY, VALUE);
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutOneSimpleCache(SimpleIspnState state, Blackhole blackhole) {
      Object value = state.cache.put(KEY, VALUE);
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutOneFunctionalCache(FunctionalIspnState state, Blackhole blackhole) {
      Object value = state.cache.put(KEY, VALUE);
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutOneCache2k(Cache2kState state, Blackhole blackhole) {
      state.cache.put(KEY, VALUE);
   }

   @Benchmark
   public void testPutAnyChm(ChmState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.map.put(state.nextKey(threadState), state.nextValue(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutAnyDataContainer(DCState state, Blackhole blackhole, ThreadState threadState) {
      state.dataContainer.put(state.nextKey(threadState), state.nextValue(threadState), METADATA);
   }

   @Benchmark
   public void testPutAnyDataContainer2(final DCState state, Blackhole blackhole, final ThreadState threadState) {
      InternalCacheEntry<String, Object> newEntry = state.dataContainer.compute(state.nextKey(threadState),
            (key, oldEntry, factory) -> factory.create(key, state.nextValue(threadState), METADATA));
      blackhole.consume(newEntry);
   }

   @Benchmark
   public void testPutAnyRegularCache(IspnState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.cache.put(state.nextKey(threadState), state.nextValue(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutAnySimpleCache(SimpleIspnState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.cache.put(state.nextKey(threadState), state.nextValue(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutAnyFunctionalCache(FunctionalIspnState state, Blackhole blackhole, ThreadState threadState) {
      Object value = state.cache.put(state.nextKey(threadState), state.nextValue(threadState));
      blackhole.consume(value);
   }

   @Benchmark
   public void testPutAnyCache2k(Cache2kState state, Blackhole blackhole, ThreadState threadState) {
      state.cache.put(state.nextKey(threadState), state.nextValue(threadState));
   }
}
