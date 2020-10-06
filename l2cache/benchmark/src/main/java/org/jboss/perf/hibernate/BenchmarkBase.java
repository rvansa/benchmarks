package org.jboss.perf.hibernate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.PessimisticLockException;
import javax.persistence.SharedCacheMode;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.SingularAttribute;
import javax.transaction.Status;
import javax.transaction.TransactionManager;

import com.mockrunner.jdbc.ResultSetFactory;
import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.mock.jdbc.EvaluableResultSet;
import com.mockrunner.mock.jdbc.MockParameterMap;
import com.mockrunner.mock.jdbc.MockResultSet;
import com.mockrunner.mock.jdbc.ParameterReference;
import com.mockrunner.util.regexp.StartsEndsPatternMatcher;

import org.hibernate.SessionFactory;
import org.hibernate.cache.internal.NoCachingRegionFactory;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.CacheImplementor;
import org.infinispan.hibernate.cache.spi.InfinispanProperties;
import org.infinispan.manager.EmbeddedCacheManager;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;
import org.perfmock.FunctionalMockResultSet;
import org.perfmock.PerfMockDriver;

public abstract class BenchmarkBase<T> {
    private static final Map<String, String> NON_CACHED_PROPERTIES = new HashMap<>();
    private static final boolean PRINT_STACK_TRACES = Boolean.valueOf(System.getProperty("printStackTraces", "true"));
    private static final boolean THROW_ON_LOCK_EXCEPTIONS = Boolean.valueOf(System.getProperty("throwOnLockExceptions", "true"));

    static {
        NON_CACHED_PROPERTIES.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, "false");
        //noinspection ResultOfMethodCallIgnored
        PerfMockDriver.getInstance(); // make sure PerfMockDriver is classloaded
    }

    static final String IRON_JACAMAR = "ironjacamar";

    static void log(Throwable t) {
        if (PRINT_STACK_TRACES) t.printStackTrace();
    }

    @SuppressWarnings("unused")
    static String addIdFromRange(String sql, MockParameterMap parameters, String columnName, int row) {
        int openParIndex = sql.indexOf('(');
        int closeParIndex = sql.lastIndexOf(')');
        if (openParIndex < 0 || closeParIndex < openParIndex) {
            throw new IllegalStateException("Unexpected sql: " + sql);
        }
        String[] ids = sql.substring(openParIndex + 1, closeParIndex).split(",");
        return ids[row].trim(); // row index is 1-based
    }

    @SuppressWarnings("unused")
    public static Object getFirstParam(String sql, MockParameterMap parameters, String columnName, int row) {
       return parameters.get(1);
    }

    @State(Scope.Benchmark)
    public abstract static class BenchmarkState<T> {

        @Param("c3p0.mock")
        String persistenceUnit;

        @Param("10000")
        int dbSize;

        @Param("100")
        int batchLoadSize;

        @Param("10")
        int transactionSize;

        @Param("none")
        String secondLevelCache;

        @Param("true")
        boolean useTx;

        @Param("true")
        boolean queryCache;

        @Param("true")
        boolean directReferenceEntries;

        @Param("")
        String keysFactory;

        @Param("false")
        boolean minimalPuts;

        @Param("false")
        boolean lazyLoadNoTrans;

        @Param("0")
        int numOtherNodes;

        @Param("")
        String config;

        @Param("false")
        boolean useReplication;

        private JndiHelper jndiHelper = new JndiHelper();
        private JtaHelper jtaHelper = new JtaHelper();
        private JacamarHelper jacamarHelper = new JacamarHelper();
        private EntityManagerFactory entityManagerFactory;
        private EntityManagerFactory[] otherNodes;
        // IDs of Persons that are commonly in the DB to make it big
        ArrayList<Long> regularIds;
        private PersistenceUnitUtil persistenceUnitUtil;
        private TransactionManager tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
        private boolean managedTransaction;
        private SingularAttribute<T, Long> idProperty;

        protected EntityManagerFactory getEntityManagerFactory() {
            return entityManagerFactory;
        }

        protected SingularAttribute getIdProperty() {
            return idProperty;
        }

        @Setup
        public void setup() throws Throwable {
            if (persistenceUnit.contains("mock")) {
                setupMock();
            }
            tm.setTransactionTimeout(1200);
            try {
                if (persistenceUnit.startsWith(IRON_JACAMAR)) {
                    jacamarHelper.start();
                    managedTransaction = persistenceUnit.endsWith(".xa");
                } else {
                    jndiHelper.start();
                    jtaHelper.start();
                }
                Map<String, String> properties = secondLevelCache.equals("none") ? NON_CACHED_PROPERTIES : getSecondLevelCacheProperties();
                System.out.println(properties);
                entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnit, properties);
                otherNodes = new EntityManagerFactory[numOtherNodes];
                for (int node = 0; node < numOtherNodes; ++node) {
                   otherNodes[node] = Persistence.createEntityManagerFactory(persistenceUnit, properties);
                }
                waitForCluster(entityManagerFactory);
                for (EntityManagerFactory node : otherNodes) {
                    waitForCluster(node);
                }
                persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
                regularIds = new ArrayList<>(dbSize);
                Metamodel metamodel = entityManagerFactory.getMetamodel();
                EntityType<T> entity = metamodel.entity(getClazz());
                Set<SingularAttribute<? super T, ?>> singularAttributes = entity.getSingularAttributes();
                for (SingularAttribute<? super T, ?> singularAttribute : singularAttributes) {
                    if (singularAttribute.isId()){
                        idProperty = (SingularAttribute<T, Long>) singularAttribute;
                        break;
                    }
                }
                if (persistenceUnit.contains("mock")) {
                    PerfMockDriver.getInstance().setMocking(true);
                }
            } catch (Throwable t) {
                log(t);
                shutdown();
                throw t;
            }
        }

        public void waitForCluster(EntityManagerFactory entityManagerFactory) throws Exception {
            RegionFactory regionFactory = ((CacheImplementor) entityManagerFactory.unwrap(SessionFactory.class).getCache()).getRegionFactory();
            if (regionFactory instanceof NoCachingRegionFactory) {
                return;
            }
            Field managerField = regionFactory.getClass().getDeclaredField("manager");
            managerField.setAccessible(true);
            EmbeddedCacheManager manager = (EmbeddedCacheManager) managerField.get(regionFactory);
            while (manager.getMembers().size() < numOtherNodes + 1) {
                Thread.sleep(100);
            }
        }

        public void setupMock() {
            PreparedStatementResultSetHandler handler = PerfMockDriver.getInstance().getPreparedStatementHandler();
            // case-sensitive comparison is more performant
            handler.setResultSetFactory(new ResultSetFactory.Default(true));
            // we need regexp for the select ... where x in ( ... )
            handler.setPatternMatcherFactory(new StartsEndsPatternMatcher.Factory());

            handler.prepareResultSet("call next value for hibernate_sequence", getIncrementing(dbSize));

            MockResultSet size = handler.createResultSet();
            size.addColumn("col_0_0_");
            size.addRow(Collections.singletonList((long) dbSize));
            handler.prepareResultSet("select count\\(.*\\) as col_0_0_ from .*", size);

            MockResultSet nonMatching = handler.createResultSet();
            // let's have one column, one row, but not matching to anything
            nonMatching.addColumn(":-)", Collections.singletonList(null));
            handler.prepareGlobalResultSet(nonMatching);
        }

        MockResultSet getIncrementing(int initialValue) {
            MockResultSet newId = new FunctionalMockResultSet("newId");
            newId.setColumnsCaseSensitive(true);
            AtomicLong counter = new AtomicLong(initialValue);
            newId.addRow(Collections.singletonList((Supplier) counter::getAndIncrement));
            return newId;
        }

        protected Map<String, String> getSecondLevelCacheProperties() {
            Map<String, String> l2Properties = new HashMap<>();
            //noinspection deprecation
            l2Properties.put(org.hibernate.jpa.AvailableSettings.SHARED_CACHE_MODE, SharedCacheMode.ALL.toString());
            //properties.put(AvailableSettings.TRANSACTION_TYPE, PersistenceUnitTransactionType.JTA.toString());
            //properties.put("hibernate.transaction.factory_class", JdbcTransactionFactory.class.getName());
            //properties.put("hibernate.transaction.factory_class", JtaTransactionFactory.class.getName());
            //properties.put(Environment.JTA_PLATFORM, "org.hibernate.service.jta.platform.internal.JBossStandAloneJtaPlatform");
            l2Properties.put(AvailableSettings.CACHE_REGION_FACTORY, "infinispan");
            l2Properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, "true");
            l2Properties.put(AvailableSettings.USE_QUERY_CACHE, String.valueOf(queryCache));
            l2Properties.put(AvailableSettings.USE_DIRECT_REFERENCE_CACHE_ENTRIES, String.valueOf(directReferenceEntries));
            l2Properties.put(AvailableSettings.USE_MINIMAL_PUTS, String.valueOf(minimalPuts));
            l2Properties.put(AvailableSettings.ENABLE_LAZY_LOAD_NO_TRANS, String.valueOf(lazyLoadNoTrans));
            if (!keysFactory.isEmpty()) {
                l2Properties.put(AvailableSettings.CACHE_KEYS_FACTORY, keysFactory);
            }
            if (!config.isEmpty()) {
                l2Properties.put(InfinispanProperties.INFINISPAN_CONFIG_RESOURCE_PROP, config);
            }
            if (useReplication) {
                l2Properties.put(InfinispanProperties.ENTITY_CACHE_RESOURCE_PROP, "replicated-entity");
                l2Properties.put(InfinispanProperties.COLLECTION_CACHE_RESOURCE_PROP, "replicated-entity");
                l2Properties.put(InfinispanProperties.NATURAL_ID_CACHE_RESOURCE_PROP, "replicated-entity");
                l2Properties.put(InfinispanProperties.QUERY_CACHE_RESOURCE_PROP, "replicated-query");
            }
            AccessType defaultAccessType = null;
            switch (secondLevelCache) {
                case "tx":
                    defaultAccessType = AccessType.TRANSACTIONAL;
                    break;
                case "ro":
                    defaultAccessType = AccessType.READ_ONLY;
                    break;
                case "rw":
                    defaultAccessType = AccessType.READ_WRITE;
                    break;
                case "ns":
                    defaultAccessType = AccessType.NONSTRICT_READ_WRITE;
                    break;
            }
            if (defaultAccessType != null) {
                l2Properties.put(AvailableSettings.DEFAULT_CACHE_CONCURRENCY_STRATEGY, defaultAccessType.getExternalName());
            }
            l2Properties.put("hibernate.transaction.manager_lookup_class", "org.hibernate.transaction.JBossTransactionManagerLookup");
            return l2Properties;
        }

        @Setup(Level.Iteration)
        public void refreshDB() throws Exception {
           if (persistenceUnit.contains("mock")) {
              regularIds = IntStream.range(0, dbSize).mapToObj(Long::valueOf)
                    .collect(ArrayList::new, ArrayList::add, (a1, a2) -> a1.addAll(a2));
              return;
           }
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                long pre;
                beginTransaction(entityManager);
                try {
                    pre = getSize(entityManager);
                    System.out.printf("Refreshing DB with %d entities\n", pre);

                    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                    CriteriaQuery<Long> query = cb.createQuery(Long.class);
                    Root<T> root = query.from(getClazz());
                    query = query.select(root.get(idProperty));
                    Predicate condition = getRootLevelCondition(cb, root);
                    if (condition != null) {
                        query = query.where(condition);
                    }
                    regularIds.clear();
                    regularIds.ensureCapacity(dbSize);

                    List<Long> resultList = entityManager.createQuery(query).getResultList();
                    for (Long id : resultList) {
                        if (regularIds.size() == dbSize) break;
                        regularIds.add(id);
                    }
                    Collections.sort(regularIds);
                    System.out.printf("Registered %d existing entities\n", regularIds.size());
                } catch (Exception e) {
                    log(e);
                    throw e;
                } finally {
                    commitTransaction(entityManager);
                }
                if (pre > dbSize) {
                    int deleted = delete(entityManager, regularIds);
                    long post = getSize(entityManager);
                    System.out.printf("DB contained %d entities (%s), %d deleted, now %d\n", pre, getClazz().getSimpleName(), deleted, post);
                    pre = post;
                }
                if (pre < dbSize) {
                    int created = 0;
                    boolean failed;
                    do {
                        failed = false;
                        beginTransaction(entityManager);
                        try {
                            created = addRandomEntities(dbSize - regularIds.size(), ThreadLocalRandom.current(), entityManager);
                            commitTransaction(entityManager);
                        } catch (Exception e) {
                            if (isLockException(e)) {
                                rollbackTransaction(entityManager);
                                failed = true;
                            } else {
                                log(e);
                                throw e;
                            }
                        }
                    } while (failed);
                    long post = getSize(entityManager);
                    System.out.printf("DB contained %d entries, %d created, now %d, ids %d\n", pre, created, post, regularIds.size());
                    if (regularIds.size() != post) {
                        throw new IllegalStateException();
                    }
                }
            } catch (Exception e) {
                log(e);
                throw e;
            } finally {
                entityManager.close();
            }
        }

        private int addRandomEntities(int numEntries, ThreadLocalRandom random, EntityManager entityManager) throws Exception {
            ArrayList<T> batchEntities = new ArrayList<>(batchLoadSize);
            for (int i = 0; i < numEntries; i++) {
                T entity = randomEntity(random);
                entityManager.persist(entity);
                batchEntities.add(entity);
                if ((i + 1) % batchLoadSize == 0) {
                    entityManager.flush();
                    entityManager.clear();
                    // let's commit the transaction in order not to timeout
                    commitTransaction(entityManager);
                    beginTransaction(entityManager);
                    // add regularIds after successful commit
                    for (T e : batchEntities) {
                        Long id = (Long) persistenceUnitUtil.getIdentifier(e);
                        regularIds.add(id);
                    }
                    batchEntities.clear();
                }
            }
            entityManager.flush();
            for (T e : batchEntities) {
                Long id = (Long) persistenceUnitUtil.getIdentifier(e);
                regularIds.add(id);
            }
            return numEntries;
        }

        public void beginTransaction(EntityManager entityManager) throws Exception {
            if (managedTransaction) {
                tm.begin();
                entityManager.joinTransaction();
            } else {
                entityManager.getTransaction().begin();
            }
        }

        public void commitTransaction(EntityManager entityManager) throws Exception {
            if (managedTransaction) {
                tm.commit();
            } else {
                entityManager.getTransaction().commit();
            }
        }

        public void rollbackTransaction(EntityManager entityManager) throws Exception {
            if (managedTransaction) {
                if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
                    tm.rollback();
                }
            } else {
                EntityTransaction tx = entityManager.getTransaction();
                if (tx.isActive()) {
                    tx.rollback();
                }
            }
        }


        private int delete(EntityManager entityManager, Collection<Long> allowedIds) throws Exception {
            beginTransaction(entityManager);
            try {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                int deleted = 0;
                if (hasForeignKeys()) {
                    for (; ; ) {
                        CriteriaQuery<T> query = cb.createQuery(getClazz());
                        Root<T> root = query.from(getClazz());
                        Predicate condition = getRootLevelCondition(cb, root);
                        if (allowedIds != null) {
                            // TODO this does not scale well
                            Predicate allowedCondition = cb.not(root.get(idProperty).in(allowedIds));
                            if (condition != null) {
                                condition = cb.and(condition, allowedCondition);
                            } else {
                                condition = allowedCondition;
                            }
                        }
                        if (condition != null) {
                            query = query.where(condition);
                        }
                        List<T> list = entityManager.createQuery(query).setMaxResults(batchLoadSize).getResultList();
                        if (list.isEmpty()) break;
                        for (T entity : list) {
                            if (!checkRootEntity(entity)) {
                                throw new IllegalStateException(String.valueOf(entity));
                            }
                            entityManager.remove(entity);
                            ++deleted;
                        }
                        entityManager.flush();
                        entityManager.clear();
                        commitTransaction(entityManager);
                        beginTransaction(entityManager);
                    }
                } else {
                    CriteriaDelete<T> query = cb.createCriteriaDelete(getClazz());
                    Root<T> root = query.from(getClazz());
                    if (allowedIds != null) {
                        query.where(cb.not(root.get(idProperty).in(allowedIds)));
                    }
                    deleted = entityManager.createQuery(query).executeUpdate();
                }
                return deleted;
            } catch (Exception e) {
                log(e);
                throw e;
            } finally {
                commitTransaction(entityManager);
            }
        }

        protected boolean checkRootEntity(T entity) {
            return true;
        }

        protected Predicate getRootLevelCondition(CriteriaBuilder criteriaBuilder, Root<T> root) {
            return null;
        }

        @TearDown
        public void shutdown() throws Throwable {
            try {
                regularIds.clear();
                EntityManager entityManager = entityManagerFactory.createEntityManager();
                try {
                    System.out.println("There are " + getSize(entityManager) + " entities");
                } finally {
                    entityManager.close();
                }
                if (persistenceUnit.contains("mock")) {
                    PerfMockDriver.getInstance().setMocking(false);
                }
                entityManagerFactory.close();
                for (EntityManagerFactory node : otherNodes) {
                    node.close();
                }
                if (persistenceUnit.startsWith(IRON_JACAMAR)) {
                    jacamarHelper.stop();
                } else {
                    jtaHelper.stop();
                    jndiHelper.stop();
                }
            } catch (Throwable t) {
                log(t);
                throw t;
            }
        }

        private long getSize(EntityManager entityManager) {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<T> root = query.from(getClazz());
            query.select(cb.count(root));
            Predicate condition = getRootLevelCondition(cb, root);
            if (condition != null) {
                query = query.where(condition);
            } else {
                // for some reason H2 sometimes returns incorrect results for
                // 'SELECT COUNT(*) FROM PERSON' while correct for 'SELECT COUNT(*) FROM PERSON WHERE 1=1'
                query = query.where(cb.equal(cb.literal(1), cb.literal(1)));
            }
            return entityManager.createQuery(query).getSingleResult();
        }

       List<Object> seq(int from, int to) {
           ArrayList<Object> list = new ArrayList<>(to - from);
           for (long i = from; i < to; ++i) {
               list.add(i);
           }
           return list;
       }

       protected List<Object> list(int size, Object item) {
           ArrayList<Object> list = new ArrayList<>(size);
           for (int i = 0; i < size; ++i) {
               list.add(item);
           }
           return list;
       }

       protected List<Object> list(int size, EvaluableResultSet.Evaluable evaluable) {
            return list(size, (Object) evaluable);
       }

       public abstract Class<T> getClazz();

        protected abstract boolean hasForeignKeys();

        public abstract T randomEntity(ThreadLocalRandom random);

        public abstract void modify(T entity, ThreadLocalRandom random);

        public Long getRandomId(ThreadLocalRandom random) {
            return regularIds.get(random.nextInt(dbSize));
        }

        public Long getRandomId(ThreadLocalRandom random, int rangeStart, int rangeEnd) {
            return regularIds.get(random.nextInt(rangeStart, rangeEnd));
        }

        // This method stores fake parameters to get the same column value in the row.
        protected Object getRandomIdForRow(String sql, MockParameterMap parameters, String columnName, int row) {
            return parameters.computeIfAbsent(new RowReference(row), r -> getRandomId(ThreadLocalRandom.current()));
        }
    }

    private static class RowReference implements ParameterReference {
        final int row;

        private RowReference(int row) {
            this.row = row;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RowReference that = (RowReference) o;
            return row == that.row;
        }

        @Override
        public int hashCode() {
            return Objects.hash(row);
        }
    }


    @State(Scope.Thread)
    public static class ThreadState {
        ThreadLocalRandom random = ThreadLocalRandom.current();
    }

    protected void testCreate(BenchmarkState<T> benchmarkState, ThreadState threadState) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                for (int i = 0; i < benchmarkState.transactionSize; ++i) {
                    T person = benchmarkState.randomEntity(threadState.random);
                    entityManager.persist(person);
                }
                benchmarkState.commitTransaction(entityManager);
            } catch (Exception e) {
                if (isLockException(e)) {
                    benchmarkState.rollbackTransaction(entityManager);
                } else {
                    log(e);
                    benchmarkState.rollbackTransaction(entityManager);
                    throw e;
                }
            }
        } finally {
            entityManager.close();
        }
    }

    protected void testRead(BenchmarkState<T> benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            if (benchmarkState.useTx) {
                benchmarkState.beginTransaction(entityManager);
            }
            try {
                for (Long id : randomIds(benchmarkState, threadState.random)) {
                    T entity = entityManager.find(benchmarkState.getClazz(), id);
                    if (entity == null) {
                        onReadNull(id);
                    }
                    blackhole.consume(entity);
                }
                if (benchmarkState.useTx) {
                    benchmarkState.commitTransaction(entityManager);
                }
            } catch (Exception e) {
                log(e);
                if (benchmarkState.useTx) {
                    benchmarkState.rollbackTransaction(entityManager);
                }
                throw e;
            }
        } finally {
            entityManager.close();
        }
    }

    protected void onReadNull(Object id) {
        throw new IllegalStateException("Entity " + id + " is null");
    }

    protected void testCriteriaRead(BenchmarkState<T> benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                Set<Long> ids = randomIds(benchmarkState, threadState.random);
                List<T> results = getEntities(benchmarkState, entityManager, ids);
                if (results.size() < benchmarkState.transactionSize) {
                    throw new IllegalStateException();
                }
                for (T entity : results) {
                    blackhole.consume(entity);
                }
                benchmarkState.commitTransaction(entityManager);
            } catch (Exception e) {
                log(e);
                benchmarkState.rollbackTransaction(entityManager);
                throw e;
            }
        } finally {
            entityManager.close();
        }
    }

    protected void testUpdate(BenchmarkState<T> benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                Collection<Long> ids = randomIds(benchmarkState, threadState.random, threadParams.getThreadIndex(), threadParams.getThreadCount());
                for (Long id : ids) {
                    T entity = entityManager.find(benchmarkState.getClazz(), id);
                    if (entity == null) {
                        onReadNull(id);
                    }
                    benchmarkState.modify(entity, threadState.random);
                    entityManager.persist(entity);
                }
                benchmarkState.commitTransaction(entityManager);
            } catch (Exception e) {
                if (isLockException(e)) {
                    benchmarkState.rollbackTransaction(entityManager);
                } else {
                    log(e);
                    benchmarkState.rollbackTransaction(entityManager);
                    throw e;
                }
            }
        } finally {
            entityManager.close();
        }
    }

    protected void testCriteriaUpdate(BenchmarkState<T> benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                Collection<Long> ids = randomIds(benchmarkState, threadState.random, threadParams.getThreadIndex(), threadParams.getThreadCount());
                List<T> entities = getEntities(benchmarkState, entityManager, ids);
                for (T entity : entities) {
                    benchmarkState.modify(entity, threadState.random);
                    entityManager.persist(entity);
                }
                benchmarkState.commitTransaction(entityManager);
            } catch (Exception e) {
                if (isLockException(e)) {
                    benchmarkState.rollbackTransaction(entityManager);
                } else {
                    log(e);
                    benchmarkState.rollbackTransaction(entityManager);
                    throw e;
                }
            }
        } finally {
            entityManager.close();
        }
    }

    protected void testDelete(BenchmarkState<T> benchmarkState, ThreadState threadState) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                for (Long id : randomIds(benchmarkState, threadState.random)) {
                    T entity = entityManager.find(benchmarkState.getClazz(), id);
                    if (entity != null) {
                        entityManager.remove(entity);
                    }
                }
                // TODO it's possible that some of the entries are already deleted
                benchmarkState.commitTransaction(entityManager);
            } catch (Exception e) {
                if (isLockException(e)) {
                    benchmarkState.rollbackTransaction(entityManager);
                } else {
                    log(e);
                    benchmarkState.rollbackTransaction(entityManager);
                    throw e;
                }
            }
        } finally {
            entityManager.close();
        }
    }

    void testCriteriaDelete(BenchmarkState<T> benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                Set<Long> randomIds = randomIds(benchmarkState, threadState.random);
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaDelete<T> query = cb.createCriteriaDelete(benchmarkState.getClazz());
                query.where(query.from(benchmarkState.getClazz()).get(benchmarkState.idProperty).in(randomIds));
                int deleted = entityManager.createQuery(query).executeUpdate();
                // it's possible that some of the entries are already deleted
                blackhole.consume(deleted);
                benchmarkState.commitTransaction(entityManager);
            } catch (Exception e) {
                if (isLockException(e)) {
                    benchmarkState.rollbackTransaction(entityManager);
                } else {
                    log(e);
                    benchmarkState.rollbackTransaction(entityManager);
                    throw e;
                }
            }
        } finally {
            entityManager.close();
        }
    }

    void testQuery(BenchmarkState<T> benchmarkState, Blackhole blackhole, BiFunction<EntityManager, CriteriaBuilder, Collection<?>> queryRunner) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                Collection<?> resultList = queryRunner.apply(entityManager, cb);
                for (Object o : resultList) {
                    blackhole.consume(o);
                }
                benchmarkState.commitTransaction(entityManager);
            } catch (Exception e) {
                log(e);
                benchmarkState.rollbackTransaction(entityManager);
                throw e;
            }
        } finally {
            entityManager.close();
        }
    }

    static boolean isLockException(Throwable e) {
        if (THROW_ON_LOCK_EXCEPTIONS) {
            if (e instanceof OptimisticLockException) {
                return true;
            } else if (e instanceof PessimisticLockException) {
                return true;
            } else if (e.getMessage() != null && e.getMessage().startsWith("Row not found")) {
                return true;
            } else return e.getCause() != null && isLockException(e.getCause());
        }
        return false;
    }

    private Collection<Long> randomIds(BenchmarkState<T> benchmarkState, ThreadLocalRandom random, int threadId, int threadCount) {
        Set<Long> ids = new HashSet<>(benchmarkState.transactionSize);
        int rangeStart = (benchmarkState.dbSize * threadId) / threadCount;
        int rangeEnd = (benchmarkState.dbSize * (threadId + 1)) / threadCount;
        while (ids.size() < benchmarkState.transactionSize) {
            Long id = benchmarkState.getRandomId(random, rangeStart, rangeEnd);
            ids.add(id);
        }
        return ids;
    }


    private Set<Long> randomIds(BenchmarkState<T> benchmarkState, ThreadLocalRandom random) {
        Set<Long> ids = new HashSet<>(benchmarkState.transactionSize);
        while (ids.size() < benchmarkState.transactionSize) {
            Long id = benchmarkState.getRandomId(random);
            ids.add(id);
        }
        return ids;
    }

    private List<T> getEntities(BenchmarkState<T> benchmarkState, EntityManager entityManager, Collection<Long> ids) {
        Class<T> clazz = benchmarkState.getClazz();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(clazz);
        return entityManager.createQuery(query.where(query.from(clazz).get(benchmarkState.idProperty).in(ids))).getResultList();
    }
}
