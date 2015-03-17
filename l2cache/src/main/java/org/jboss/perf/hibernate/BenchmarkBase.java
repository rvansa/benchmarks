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
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
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
import javax.transaction.TransactionManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.jpa.AvailableSettings;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

public abstract class BenchmarkBase<T> {
    private static final Map<String, String> l2Properties;
    private static final boolean printStackTraces = Boolean.valueOf(System.getProperty("printStackTraces", "true"));

    static {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(AvailableSettings.SHARED_CACHE_MODE, SharedCacheMode.ALL.toString());
        //properties.put(AvailableSettings.TRANSACTION_TYPE, PersistenceUnitTransactionType.JTA.toString());
        //properties.put("hibernate.transaction.factory_class", JdbcTransactionFactory.class.getName());
        //properties.put("hibernate.transaction.factory_class", JtaTransactionFactory.class.getName());
        //properties.put(Environment.JTA_PLATFORM, "org.hibernate.service.jta.platform.internal.JBossStandAloneJtaPlatform");
        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.infinispan.InfinispanRegionFactory");
        properties.put("hibernate.cache.use_second_level_cache", "true");
        properties.put("hibernate.cache.use_query_cache", "true");
        properties.put("hibernate.cache.default_cache_concurrency_strategy", "transactional");
        properties.put("hibernate.transaction.manager_lookup_class", "org.hibernate.transaction.JBossTransactionManagerLookup");
        l2Properties = Collections.unmodifiableMap(properties);
    }

    protected static final String C3P0 = "c3p0";
    protected static final String HIKARI = "hikari";
    protected static final String IRON_JACAMAR = "ironjacamar";

    protected static Log log = LogFactory.getLog("Benchmark");

    protected static void log(Throwable t) {
        if (printStackTraces) t.printStackTrace();
    }

    @State(Scope.Benchmark)
    public abstract static class BenchmarkState<T> {

        @Param({C3P0, HIKARI, IRON_JACAMAR + ".local", IRON_JACAMAR + ".xa"})
        String persistenceUnit;

        @Param("10000")
        int dbSize;

        @Param("100")
        int batchLoadSize;

        @Param("10")
        int transactionSize;

        @Param({ "true", "false"})
        boolean useL2Cache;

        private JndiHelper jndiHelper = new JndiHelper();
        private JtaHelper jtaHelper = new JtaHelper();
        private JacamarHelper jacamarHelper = new JacamarHelper();
        private EntityManagerFactory entityManagerFactory;
        // IDs of Persons that are commonly in the DB to make it big
        private ArrayList<Long> regularIds;
        private PersistenceUnitUtil persistenceUnitUtil;
        private TransactionManager tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
        private boolean managedTransaction;
        private SingularAttribute idProperty;

        @Setup
        public void setup() throws Throwable {
            System.err.printf("setup() called in thread %s(%d) on object %08x\n", Thread.currentThread().getName(), Thread.currentThread().getId(), this.hashCode());
            tm.setTransactionTimeout(1200);
            try {
                if (persistenceUnit.startsWith(IRON_JACAMAR)) {
                    jacamarHelper.start();
                    managedTransaction = persistenceUnit.endsWith(".xa");
                } else {
                    jndiHelper.start();
                    jtaHelper.start();
                }
                if (useL2Cache) {
                    // we always need managed transactions with 2LC since there are multiple participants
                    managedTransaction = true;
                }
                entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnit, useL2Cache ? l2Properties : Collections.EMPTY_MAP);
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
            } catch (Throwable t) {
                log(t);
                throw t;
            }
            System.err.println("setup() finished");
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
                    for (Long id : entityManager.createQuery(query).getResultList()) {
                        if (regularIds.size() == dbSize) break;
                        regularIds.add(id);
                    }
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
                    beginTransaction(entityManager);
                    try {
                        /*Map<Long, Integer> ids = new HashMap<Long, Integer>(batchLoadSize);
                        for (int i = 0; i < regularIds.size(); ++i) {
                            ids.put(regularIds.get(i), i);
                            if (ids.size() == batchLoadSize) {
                                created += flushEntities(entityManager, ids);
                            }
                        }
                        created += flushEntities(entityManager, ids);
                        System.out.printf("Replaced %d ids\n", created);*/
                        created += addRandomEntities(dbSize - regularIds.size(), ThreadLocalRandom.current(), entityManager);
                    } catch (Exception e) {
                        log(e);
                        throw e;
                    } finally {
                        commitTransaction(entityManager);
                    }
                    try {
                        long post = getSize(entityManager);
                        System.out.printf("DB contained %d entries, %d created, now %d, ids %d\n", pre, created, post, regularIds.size());
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
                log.trace("Persisting entity " + entity);
                entityManager.persist(entity);
                batchEntities.add(entity);
                if ((i + 1) % batchLoadSize == 0) {
                    log.trace("Flushing " + batchEntities.size() + " entities");
                    entityManager.flush();
                    entityManager.clear();
                    for (T e : batchEntities) {
                        Long id = (Long) persistenceUnitUtil.getIdentifier(e);
                        regularIds.add(id);
                    }
                    batchEntities.clear();
                    // let's commit the transaction in order not to timeout
                    commitTransaction(entityManager);
                    beginTransaction(entityManager);
                }
            }
            log.trace("Flushing " + batchEntities.size() + " entities");
            entityManager.flush();
            for (T e : batchEntities) {
                Long id = (Long) persistenceUnitUtil.getIdentifier(e);
                regularIds.add(id);
            }
            return numEntries;
        }

        public void beginTransaction(EntityManager entityManager) throws Exception {
            log.trace("Transaction begin, state is " + tm.getStatus());
            try {
                if (managedTransaction) {
                    tm.begin();
                    entityManager.joinTransaction();
                } else {
                    entityManager.getTransaction().begin();
                }
                log.trace("Transaction began, state is " + tm.getStatus());
            } catch (Exception e) {
                log.error("Failed starting TX", e);
                throw e;
            }
        }

        public void commitTransaction(EntityManager entityManager) throws Exception {
            log.trace("Transaction commit, state is " + tm.getStatus());
            try {
                if (managedTransaction) {
                    tm.commit();
                } else {
                    entityManager.getTransaction().commit();
                }
                log.trace("Transaction commited, state is " + tm.getStatus());
            } catch (Exception e) {
                log.error("Failed committing TX", e);
                throw e;
            }
        }

        public void rollbackTransaction(EntityManager entityManager) throws Exception {
            log.trace("Rolling back");
            try {
                if (managedTransaction) {
                    tm.rollback();
                } else {
                    entityManager.getTransaction().rollback();
                }
            } catch (Exception e) {
                log.error("Failed rolling back TX", e);
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
            }
            return entityManager.createQuery(query).getSingleResult();
        }

        public abstract Class<T> getClazz();

        protected abstract boolean hasForeignKeys();

        public abstract T randomEntity(ThreadLocalRandom random);

        public abstract void modify(T entity, ThreadLocalRandom random);
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
            } catch (RuntimeException e) {
                log(e);
                throw e;
            } finally {
                benchmarkState.commitTransaction(entityManager);
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
                        throw new IllegalStateException("Entity " + id + " is null");
                    }
                    blackhole.consume(entity);
                }
            } catch (RuntimeException e) {
                log(e);
                throw e;
            } finally {
                benchmarkState.commitTransaction(entityManager);
            }
        } finally {
            entityManager.close();
        }
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
            } catch (RuntimeException e) {
                log(e);
                throw e;
            } finally {
                benchmarkState.commitTransaction(entityManager);
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
                        throw new IllegalStateException("Entity for id " + id + " is null!");
                    }
                    benchmarkState.modify(entity, threadState.random);
                    entityManager.persist(entity);
                }
            } catch (RuntimeException e) {
                log(e);
                throw e;
            } finally {
                benchmarkState.commitTransaction(entityManager);
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
            } catch (RuntimeException e) {
                log(e);
                throw e;
            } finally {
                benchmarkState.commitTransaction(entityManager);
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
            } catch (RuntimeException e) {
                log(e);
                throw e;
            } finally {
                benchmarkState.commitTransaction(entityManager);
            }
        } finally {
            entityManager.close();
        }
    }

    protected void testCriteriaDelete(BenchmarkState<T> benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            boolean shouldCommit = true;
            try {
                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaDelete<T> query = cb.createCriteriaDelete(benchmarkState.getClazz());
                query.where(query.from(benchmarkState.getClazz()).get(benchmarkState.idProperty).in(randomIds(benchmarkState, threadState.random)));
                int deleted = entityManager.createQuery(query).executeUpdate();
                // it's possible that some of the entries are already deleted
                blackhole.consume(deleted);
            } catch (RuntimeException e) {
                shouldCommit = false;
                if (!isOptimisticException(e)) {
                    log(e);
                    throw e;
                }
            } finally {
                if (shouldCommit) {
                    benchmarkState.commitTransaction(entityManager);
                } else {
                    benchmarkState.rollbackTransaction(entityManager);
                }
            }
        } finally {
            entityManager.close();
        }
    }

    private boolean isOptimisticException(Throwable e) {
        if (e instanceof OptimisticLockException) {
            return true;
        } else if (e.getMessage().startsWith("Row not found")) {
            return true;
        } else if (e.getCause() != null && isOptimisticException(e.getCause())) {
            return true;
        }
        return false;
    }

    private Collection<Long> randomIds(BenchmarkState<T> benchmarkState, ThreadLocalRandom random, int threadId, int threadCount) {
        Set<Long> ids = new HashSet<Long>(benchmarkState.transactionSize);
        int rangeStart = (benchmarkState.dbSize * threadId) / threadCount;
        int rangeEnd = (benchmarkState.dbSize * (threadId + 1)) / threadCount;
        while (ids.size() < benchmarkState.transactionSize) {
            Long id = benchmarkState.regularIds.get(random.nextInt(rangeStart, rangeEnd));
            ids.add(id);
        }
        return ids;
    }


    private Set<Long> randomIds(BenchmarkState<T> benchmarkState, ThreadLocalRandom random) {
        Set<Long> ids = new HashSet<Long>(benchmarkState.transactionSize);
        while (ids.size() < benchmarkState.transactionSize) {
            Long id = benchmarkState.regularIds.get(random.nextInt(benchmarkState.dbSize));
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
