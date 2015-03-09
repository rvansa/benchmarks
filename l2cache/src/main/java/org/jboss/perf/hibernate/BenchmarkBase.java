/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

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
import javax.persistence.LockModeType;
import javax.persistence.Persistence;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

public abstract class BenchmarkBase<T> {
    private static final Map<String, String> l2Properties;

    static {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("javax.persistence.sharedCache.mode", "ALL");
        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.infinispan.InfinispanRegionFactory");
        properties.put("hibernate.cache.use_second_level_cache", "true");
        properties.put("hibernate.cache.use_query_cache", "true");
        properties.put("hibernate.cache.default_cache_concurrency_strategy", "transactional");
        properties.put("hibernate.transaction.factory_class", "org.hibernate.transaction.JTATransactionFactory");
        properties.put("hibernate.transaction.manager_lookup_class", "org.hibernate.transaction.JBossTransactionManagerLookup");
        l2Properties = Collections.unmodifiableMap(properties);
    }

    protected static final String C3P0 = "org.jboss.perf.hibernate.c3p0";
    protected static final String HIKARI = "org.jboss.perf.hibernate.hikari";
    protected static final String IRON_JACAMAR = "org.jboss.perf.hibernate.ironjacamar";

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

        @Setup
        public void setup() throws Throwable {
            if (persistenceUnit.startsWith(IRON_JACAMAR)) {
                jacamarHelper.start();
                managedTransaction = persistenceUnit.endsWith(".xa");
            } else {
                jndiHelper.start();
                jtaHelper.start();
            }
            entityManagerFactory = Persistence.createEntityManagerFactory(persistenceUnit, useL2Cache ? l2Properties : Collections.EMPTY_MAP);
            persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
            regularIds = new ArrayList<Long>(dbSize);
            preload();
        }

        private void preload() throws Exception {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                delete(entityManager, null);

                beginTransaction(entityManager);
                try {
                    ArrayList<T> batchEntities = new ArrayList<T>(batchLoadSize);
                    for (int i = 0; i < dbSize; i++) {
                        T entity = randomEntity(random);
                        entityManager.persist(entity);
                        batchEntities.add(entity);
                        if ((i + 1) % batchLoadSize == 0) {
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
                } finally {
                    commitTransaction(entityManager);
                }
                System.out.printf("There are %d entities in DB\n", getSize(entityManager));
            } finally {
                entityManager.close();
            }
        }

        public void beginTransaction(EntityManager entityManager) throws NotSupportedException, SystemException {
            if (managedTransaction) {
                tm.begin();
                entityManager.joinTransaction();
            } else {
                entityManager.getTransaction().begin();
            }
        }

        public void commitTransaction(EntityManager entityManager) throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SystemException {
            if (managedTransaction) {
                tm.commit();
            } else {
                entityManager.getTransaction().commit();
            }
        }

        public void rollbackTransaction(EntityManager entityManager) throws SystemException {
            if (managedTransaction) {
                tm.rollback();
            } else {
                entityManager.getTransaction().rollback();
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
                        if (allowedIds != null) {
                            // TODO this does not scale well
                            query.where(cb.not(query.from(getClazz()).in(allowedIds)));
                        } else {
                            query.from(getClazz());
                        }
                        List<T> list = entityManager.createQuery(query).setMaxResults(batchLoadSize).getResultList();
                        if (list.isEmpty()) break;
                        for (T entity : list) {
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
                    if (allowedIds != null) {
                        query.where(cb.not(query.from(getClazz()).in(allowedIds)));
                    } else {
                        query.from(getClazz());
                    }
                    deleted = entityManager.createQuery(query).executeUpdate();
                }
                return deleted;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            } finally {
                commitTransaction(entityManager);
            }
        }

        @TearDown(Level.Iteration)
        public void refreshDB() throws Exception {
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                long pre = getSize(entityManager);
                System.out.printf("Refreshing DB with %d entities\n", pre);
                if (pre > dbSize) {
                    int deleted = delete(entityManager, regularIds);
                    long post = getSize(entityManager);
                    System.out.printf("DB contained %d entities (%s), %d deleted, now %d\n", pre, getClazz().getSimpleName(), deleted, post);
                } else if (pre < dbSize) {
                    int created = 0;
                    beginTransaction(entityManager);
                    try {
                        Map<Long, Integer> ids = new HashMap<Long, Integer>(batchLoadSize);
                        ArrayList<T> newEntities = new ArrayList<T>(batchLoadSize);
                        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                        for (int i = 0; i < regularIds.size(); ++i) {
                            ids.put(regularIds.get(i), i);
                            if (ids.size() == batchLoadSize) {
                                CriteriaQuery<Long> query = cb.createQuery(Long.class);
                                Root<T> root = query.from(getClazz());
                                Path<Long> idPath = root.get("id");
                                for (long id : entityManager.createQuery(query.select(idPath).where(idPath.in(ids.keySet()))).getResultList()) {
                                    ids.remove(id);
                                }
                                for (int j = ids.size(); j >= 0; --j) {
                                    T entity = randomEntity(ThreadLocalRandom.current());
                                    entityManager.persist(entity);
                                    newEntities.add(entity);
                                }
                                entityManager.flush();
                                // replace all ids
                                int j = 0;
                                for (int index : ids.values()) {
                                    regularIds.set(index, (Long) persistenceUnitUtil.getIdentifier(newEntities.get(j++)));
                                }
                                created += newEntities.size();
                                newEntities.clear();
                                ids.clear();
                                entityManager.clear();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    } finally {
                        commitTransaction(entityManager);
                    }
                    long post = getSize(entityManager);
                    System.out.printf("Cache contained %d entries, %d created, now %d\n", pre, created, post);
                }
            } finally {
                entityManager.close();
            }
        }

        @TearDown
        public void shutdown() throws Throwable {
            regularIds.clear();
            EntityManager entityManager = entityManagerFactory.createEntityManager();
            try {
                System.out.println("There are " + getSize(entityManager) + " entities");
            } finally {
                entityManagerFactory.close();
            }
            if (persistenceUnit.startsWith(IRON_JACAMAR)) {
                jacamarHelper.stop();
            } else {
                jtaHelper.stop();
                jndiHelper.stop();
            }
        }

        private long getSize(EntityManager entityManager) {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            query.select(cb.count(query.from(getClazz())));
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
                e.printStackTrace();
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
                    blackhole.consume(entity);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
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
                List<T> results = getEntities(entityManager, benchmarkState.getClazz(), ids, LockModeType.NONE);
                if (results.size() < benchmarkState.transactionSize) {
                    throw new IllegalStateException();
                }
                for (T entity : results) {
                    blackhole.consume(entity);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
            } finally {
                benchmarkState.commitTransaction(entityManager);
            }
        } finally {
            entityManager.close();
        }
    }

    protected void testUpdate(BenchmarkState<T> benchmarkState, ThreadState threadState) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                for (Long id : randomIds(benchmarkState, threadState.random)) {
                    T entity = entityManager.find(benchmarkState.getClazz(), id);
                    benchmarkState.modify(entity, threadState.random);
                    entityManager.persist(entity);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
            } finally {
                benchmarkState.commitTransaction(entityManager);
            }
        } finally {
            entityManager.close();
        }
    }

    protected void testCriteriaUpdate(BenchmarkState<T> benchmarkState, ThreadState threadState) throws Exception {
        EntityManager entityManager = benchmarkState.entityManagerFactory.createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                for (T entity : getEntities(entityManager, benchmarkState.getClazz(),
                      randomIds(benchmarkState, threadState.random), LockModeType.PESSIMISTIC_WRITE)) {
                    benchmarkState.modify(entity, threadState.random);
                    entityManager.persist(entity);
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
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
                e.printStackTrace();
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
                query.where(query.from(benchmarkState.getClazz()).get("id").in(randomIds(benchmarkState, threadState.random)));
                int deleted = entityManager.createQuery(query).executeUpdate();
                // it's possible that some of the entries are already deleted
                blackhole.consume(deleted);
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                e.printStackTrace();
                shouldCommit = false;
                boolean shouldThrow = true;
                if (e.getCause() != null && e.getCause().getCause() != null) {
                    String msg = e.getCause().getCause().getMessage();
                    if (msg.startsWith("Row not found")) {
                        shouldThrow = false;
                    }
                }
                if (shouldThrow) {
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

    private Set<Long> randomIds(BenchmarkState<T> benchmarkState, ThreadLocalRandom random) {
        Set<Long> ids = new HashSet<Long>(benchmarkState.transactionSize);
        while (ids.size() < benchmarkState.transactionSize) {
            Long id = benchmarkState.regularIds.get(random.nextInt(benchmarkState.dbSize));
            ids.add(id);
        }
        return ids;
    }

    private List<T> getEntities(EntityManager entityManager, Class<T> clazz, Collection<Long> ids, LockModeType lockMode) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(clazz);
        return entityManager.createQuery(query.where(query.from(clazz).get("id").in(ids))).setLockMode(lockMode).getResultList();
    }
}
