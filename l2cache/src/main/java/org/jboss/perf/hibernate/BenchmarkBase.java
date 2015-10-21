package org.jboss.perf.hibernate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.SharedCacheMode;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.SingularAttribute;
import javax.transaction.Status;
import javax.transaction.TransactionManager;

import com.mockrunner.jdbc.ResultSetFactory;
import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.mock.jdbc.MockResultSet;
import com.mockrunner.util.regexp.StartsEndsPatternMatcher;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.cache.infinispan.InfinispanRegionFactory;
import org.hibernate.cfg.AvailableSettings;
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
    private static final Map<String, String> l2Properties = new HashMap<String, String>();
    private static final Map<String, String> nonCachedProperties = new HashMap<String, String>();
    private static final boolean printStackTraces = Boolean.valueOf(System.getProperty("printStackTraces", "true"));

    static {
//        l2Properties.put("javax.persistence.sharedCache.mode", SharedCacheMode.ALL.toString());
        l2Properties.put(org.hibernate.jpa.AvailableSettings.SHARED_CACHE_MODE, SharedCacheMode.ALL.toString());
        //properties.put(AvailableSettings.TRANSACTION_TYPE, PersistenceUnitTransactionType.JTA.toString());
        //properties.put("hibernate.transaction.factory_class", JdbcTransactionFactory.class.getName());
        //properties.put("hibernate.transaction.factory_class", JtaTransactionFactory.class.getName());
        //properties.put(Environment.JTA_PLATFORM, "org.hibernate.service.jta.platform.internal.JBossStandAloneJtaPlatform");
        l2Properties.put(AvailableSettings.CACHE_REGION_FACTORY, InfinispanRegionFactory.class.getName());
        l2Properties.put(InfinispanRegionFactory.INFINISPAN_CONFIG_RESOURCE_PROP, "second-level-cache-cfg.xml");
        l2Properties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, "true");
//        l2Properties.put(AvailableSettings.USE_QUERY_CACHE, "true");
        l2Properties.put(AvailableSettings.USE_QUERY_CACHE, "false");
        //l2Properties.put(AvailableSettings.DEFAULT_CACHE_CONCURRENCY_STRATEGY, CacheConcurrencyStrategy.READ_ONLY.toAccessType().getExternalName());
        l2Properties.put(AvailableSettings.DEFAULT_CACHE_CONCURRENCY_STRATEGY, CacheConcurrencyStrategy.TRANSACTIONAL.toAccessType().getExternalName());
        l2Properties.put("hibernate.transaction.manager_lookup_class", "org.hibernate.transaction.JBossTransactionManagerLookup");

        nonCachedProperties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, "false");

        PerfMockDriver.getInstance(); // make sure PerfMockDriver is classloaded
    }

    protected static final String C3P0 = "c3p0";
    protected static final String HIKARI = "hikari";
    protected static final String IRON_JACAMAR = "ironjacamar";

    //protected static Log log = LogFactory.getLog("Benchmark");

    protected static void trace(String msg) {
//        System.err.println(msg);
    }

    protected static void error(String msg, Throwable t) {
        //System.err.println(msg);
        //if (printStackTraces) t.printStackTrace();
    }

    protected static void log(Throwable t) {
        if (printStackTraces) t.printStackTrace();
    }

    @State(Scope.Benchmark)
    public abstract static class BenchmarkState<T> {

//        @Param({C3P0, HIKARI, IRON_JACAMAR + ".local", IRON_JACAMAR + ".xa"})
        @Param({ C3P0 + ".mock", HIKARI + ".mock", IRON_JACAMAR + ".mock.local", IRON_JACAMAR + ".mock.xa"})
        String persistenceUnit;

        @Param("10000")
        int dbSize;

        @Param("100")
        int batchLoadSize;

        @Param("10")
        int transactionSize;

        @Param({ "none", "tx"})
        String secondLevelCache;

        private JndiHelper jndiHelper = new JndiHelper();
        private JtaHelper jtaHelper = new JtaHelper();
        private JacamarHelper jacamarHelper = new JacamarHelper();
        private EntityManagerFactory entityManagerFactory;
        // IDs of Persons that are commonly in the DB to make it big
        protected ArrayList<Long> regularIds;
        private PersistenceUnitUtil persistenceUnitUtil;
        private TransactionManager tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
        private boolean managedTransaction;
        private SingularAttribute idProperty;
        private String entityName;

        protected EntityManagerFactory getEntityManagerFactory() {
            return entityManagerFactory;
        }

        protected SingularAttribute getIdProperty() {
            return idProperty;
        }

        @Setup
        public void setup() throws Throwable {
            setupMock();
            tm.setTransactionTimeout(1200);
            try {
                if (persistenceUnit.startsWith(IRON_JACAMAR)) {
                    jacamarHelper.start();
                    managedTransaction = persistenceUnit.endsWith(".xa");
                } else {
                    jndiHelper.start();
                    jtaHelper.start();
                }
//                if (useL2Cache) {
                    // we always need managed transactions with 2LC since there are multiple participants
//                    managedTransaction = true;
//                }
                entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnit,
                      secondLevelCache.equals("none") ? nonCachedProperties : getSecondLevelCacheProperties());
                persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
                regularIds = new ArrayList<Long>(dbSize);
                Metamodel metamodel = entityManagerFactory.getMetamodel();
                EntityType entity = metamodel.entity(getClazz());
                Set<SingularAttribute> singularAttributes = entity.getSingularAttributes();
                for (SingularAttribute singularAttribute : singularAttributes) {
                    if (singularAttribute.isId()){
                        idProperty=singularAttribute;
                        break;
                    }
                }
                entityName = entityManagerFactory.getMetamodel().entity(getClazz()).getName();
                PerfMockDriver.getInstance().setMocking(true);
            } catch (Throwable t) {
                log(t);
                throw t;
            }
            System.err.println("setup() finished");
        }

        public void setupMock() {
            PreparedStatementResultSetHandler handler = PerfMockDriver.getInstance().getPreparedStatementHandler();
            // case-sensitive comparison is more performant
            handler.setResultSetFactory(new ResultSetFactory.Default(true));
            // we need regexp for the select ... where x in ( ... )
//            handler.setUseRegularExpressions(true);
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

        protected MockResultSet getIncrementing(int initialValue) {
            MockResultSet newId = new FunctionalMockResultSet("newId");
            newId.setColumnsCaseSensitive(true);
            AtomicLong counter = new AtomicLong(initialValue);
            newId.addRow(Collections.<Object>singletonList((Supplier) counter::getAndIncrement));
            return newId;
        }

        protected Map<String, String> getSecondLevelCacheProperties() {
            return new HashMap<>(l2Properties);
        }

        @Setup(Level.Iteration)
        public void refreshDB() throws Exception {
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
                    query = query.select(root.<Long>get(idProperty));
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
                    try {
                        long post = getSize(entityManager);
                        System.out.printf("DB contained %d entries, %d created, now %d, ids %d\n", pre, created, post, regularIds.size());
                        if (regularIds.size() != post) {
                            throw new IllegalStateException();
                        }
                    } finally {
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
            ArrayList<T> batchEntities = new ArrayList<T>(batchLoadSize);
            for (int i = 0; i < numEntries; i++) {
                T entity = randomEntity(random);
                trace("Persisting entity " + entity);
                entityManager.persist(entity);
                batchEntities.add(entity);
                if ((i + 1) % batchLoadSize == 0) {
                    trace("Flushing " + batchEntities.size() + " entities");
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
            trace("Flushing " + batchEntities.size() + " entities");
            entityManager.flush();
            for (T e : batchEntities) {
                Long id = (Long) persistenceUnitUtil.getIdentifier(e);
                regularIds.add(id);
            }
            return numEntries;
        }

        public void beginTransaction(EntityManager entityManager) throws Exception {
            trace("Transaction begin, state is " + tm.getStatus());
            try {
                if (managedTransaction) {
                    tm.begin();
                    entityManager.joinTransaction();
                } else {
                    entityManager.getTransaction().begin();
                }
                trace("Transaction began, state is " + tm.getStatus());
            } catch (Exception e) {
                error("Failed starting TX", e);
                throw e;
            }
        }

        public void commitTransaction(EntityManager entityManager) throws Exception {
            trace("Transaction commit, state is " + tm.getStatus());
            try {
                if (managedTransaction) {
                    tm.commit();
                } else {
                    entityManager.getTransaction().commit();
                }
                trace("Transaction commited, state is " + tm.getStatus());
            } catch (Exception e) {
                error("Failed committing TX", e);
                throw e;
            }
        }

        public void rollbackTransaction(EntityManager entityManager) throws Exception {
            trace("Rolling back");
            try {
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
            } catch (Exception e) {
                error("Failed rolling back TX", e);
                throw e;
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
                            Predicate allowedCondition = cb.not(root.<Long>get(idProperty).in(allowedIds));
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
//                    String jpql = "DELETE FROM " + entityName;
//                    if (allowedIds != null) {
//                        jpql += " WHERE " + idProperty.getName() + " NOT IN (" + collectionToString(allowedIds) + ")";
//                    }
//                    deleted = entityManager.createQuery(jpql).executeUpdate();
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

        private int flushEntities(EntityManager entityManager, Map<Long, Integer> ids) throws Exception {
            if (ids.isEmpty()) return 0;
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            ArrayList<T> newEntities = new ArrayList<T>(batchLoadSize);
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<T> root = query.from(getClazz());
            Path<Long> idPath = root.get(idProperty);
            for (long id : entityManager.createQuery(query.select(idPath).where(idPath.in(ids.keySet()))).getResultList()) {
                ids.remove(id);
            }
            for (int j = ids.size(); j >= 0; --j) {
                T entity = randomEntity(ThreadLocalRandom.current());
                entityManager.persist(entity);
                newEntities.add(entity);
            }
            entityManager.flush();
            commitTransaction(entityManager);
            beginTransaction(entityManager);
            // replace all ids
            int j = 0;
            for (int index : ids.values()) {
                regularIds.set(index, (Long) persistenceUnitUtil.getIdentifier(newEntities.get(j++)));
            }
            ids.clear();
            entityManager.clear();
            return newEntities.size();
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
                PerfMockDriver.getInstance().setMocking(false);
                entityManagerFactory.close();
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

       protected List<Object> seq(int from, int to) {
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
    }

    @State(Scope.Thread)
    public static class ThreadState {
        ThreadLocalRandom random = ThreadLocalRandom.current();
    }

    protected static String collectionToString(Collection collection) {
        StringBuilder sb = new StringBuilder();
        for (Object element : collection) {
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(element);
        }
        return sb.toString();
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
            benchmarkState.beginTransaction(entityManager);
            try {
                for (Long id : randomIds(benchmarkState, threadState.random)) {
                    T entity = entityManager.find(benchmarkState.getClazz(), id);
                    if (entity == null) {
                        onReadNull(id);
                    }
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
//                System.out.printf("Thread %d/%d updating %s\n", threadParams.getThreadIndex(), threadParams.getThreadCount(), ids);
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
                for (T entity : getEntities(benchmarkState, entityManager, ids)) {
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

    protected void testCriteriaDelete(BenchmarkState<T> benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                Set<Long> randomIds = randomIds(benchmarkState, threadState.random);
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaDelete<T> query = cb.createCriteriaDelete(benchmarkState.getClazz());
                query.where(query.from(benchmarkState.getClazz()).get(benchmarkState.idProperty).in(randomIds));
                int deleted = entityManager.createQuery(query).executeUpdate();
//                String jpql = "DELETE FROM " + benchmarkState.entityName + " WHERE " + benchmarkState.idProperty + " IN (" + collectionToString(randomIds) + ")";
//                int deleted = entityManager.createQuery(jpql).executeUpdate();
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

    protected void testQuery(BenchmarkState<T> benchmarkState, Blackhole blackhole, QueryRunner queryRunner) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                Collection<?> resultList = queryRunner.runQuery(entityManager, cb);
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

    protected static boolean isLockException(Throwable e) {
        /*if (e instanceof OptimisticLockException) {
            return true;
        } else if (e instanceof PessimisticLockException) {
            return true;
        } else if (e.getMessage() != null && e.getMessage().startsWith("Row not found")) {
            return true;
        } else if (e.getCause() != null && isLockException(e.getCause())) {
            return true;
        }*/
        return false;
    }

    private Collection<Long> randomIds(BenchmarkState<T> benchmarkState, ThreadLocalRandom random, int threadId, int threadCount) {
        Set<Long> ids = new HashSet<Long>(benchmarkState.transactionSize);
        int rangeStart = (benchmarkState.dbSize * threadId) / threadCount;
        int rangeEnd = (benchmarkState.dbSize * (threadId + 1)) / threadCount;
        while (ids.size() < benchmarkState.transactionSize) {
            Long id = benchmarkState.getRandomId(random, rangeStart, rangeEnd);
            ids.add(id);
        }
        return ids;
    }


    protected Set<Long> randomIds(BenchmarkState<T> benchmarkState, ThreadLocalRandom random) {
        Set<Long> ids = new HashSet<Long>(benchmarkState.transactionSize);
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
