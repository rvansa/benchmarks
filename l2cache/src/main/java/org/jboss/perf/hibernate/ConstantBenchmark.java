package org.jboss.perf.hibernate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.persistence.EntityManager;

import org.jboss.perf.hibernate.model.Constant;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ConstantBenchmark extends BenchmarkBase<Constant> {
    public static class ConstantState extends BenchmarkState<Constant> {

        @Override
        protected Map<String, String> getSecondLevelCacheProperties() {
            Map<String, String> properties = super.getSecondLevelCacheProperties();
            if (secondLevelCache.equals("nontx")) {
                properties.put("hibernate.cache.infinispan." + Constant.class.getName() + ".cfg", "immutable-entity");
            }
            return properties;
        }

        @Override
        public Class<Constant> getClazz() {
            return Constant.class;
        }

        @Override
        protected boolean hasForeignKeys() {
            return false;
        }

        @Override
        public Constant randomEntity(ThreadLocalRandom random) {
            return new Constant(Randomizer.randomString(20, 20, random));
        }

        @Override
        public void modify(Constant entity, ThreadLocalRandom random) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized Long getRandomId(ThreadLocalRandom random) {
            return super.getRandomId(random);
        }

        @Override
        public synchronized Long getRandomId(ThreadLocalRandom random, int rangeStart, int rangeEnd) {
            return super.getRandomId(random, rangeStart, rangeEnd);
        }

        public synchronized Long getIdAt(int index) {
            return regularIds.get(index);
        }

        public synchronized void replaceId(int index, Long newId) {
            regularIds.set(index, newId);
        }
    }

    @Override
    protected void onReadNull(Long id) {
        // don't worry, be happy...
    }

    @Group
    @GroupThreads(10)
    @Benchmark
    public void testRead(ConstantState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testRead(benchmarkState, threadState, blackhole);
    }

    @Group
    @GroupThreads(1)
    @Benchmark
    public void mutate(ConstantState benchmarkState, ThreadState threadState, ThreadParams threadParams, Blackhole blackhole) throws Exception {
        EntityManager entityManager = benchmarkState.getEntityManagerFactory().createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                ArrayList<Integer> indices = new ArrayList<>(benchmarkState.transactionSize);
                ArrayList<Constant> newEntities = new ArrayList<>(benchmarkState.transactionSize);
                int rangeBegin = threadParams.getGroupThreadIndex() * benchmarkState.dbSize / threadParams.getGroupThreadCount();
                int rangeEnd = (threadParams.getGroupThreadIndex() + 1) * benchmarkState.dbSize / threadParams.getGroupThreadCount();
                while (indices.size() < benchmarkState.transactionSize) {
                    Integer index = threadState.random.nextInt(rangeBegin, rangeEnd);
                    if (indices.contains(index)) {
                        continue;
                    }
                    Long id = benchmarkState.getIdAt(index);
                    Constant entity = entityManager.find(benchmarkState.getClazz(), id);
                    if (entity == null) {
                        throw new IllegalStateException();
                        //continue;
                    }
                    entityManager.remove(entity);
                    entity = benchmarkState.randomEntity(threadState.random);
                    entityManager.persist(entity);
                    indices.add(index);
                    newEntities.add(entity);
                }
                benchmarkState.commitTransaction(entityManager);
                Iterator<Integer> iit = indices.iterator();
                Iterator<Constant> cit = newEntities.iterator();
                for (; iit.hasNext() && cit.hasNext(); ) {
                    benchmarkState.replaceId(iit.next(), cit.next().getId());
                }
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

    /*static Logger log = Logger.getLogger(ConstantBenchmark.class);

    @Benchmark
    public void rollbackOnly(ConstantState benchmarkState) throws Exception{
        EntityManager entityManager = benchmarkState.getEntityManagerFactory().createEntityManager();
        try {
            log.info("BEFORE ALL");
            benchmarkState.beginTransaction(entityManager);
            Constant foo = new Constant("FOO").setId(1);
            log.info("BEFORE PERSIST FOO");
            entityManager.persist(foo);
            log.info("AFTER PERSIST FOO");
            benchmarkState.commitTransaction(entityManager);
            log.info("AFTER COMMIT FOO");

            benchmarkState.beginTransaction(entityManager);
            Constant bar = new Constant("BAR").setId(2);
            log.info("BEFORE PERSIST BAR");
            entityManager.persist(bar);
            log.info("AFTER PERSIST BAR");
            benchmarkState.rollbackTransaction(entityManager);
            log.info("AFTER ROLLBACK BAR");

            benchmarkState.beginTransaction(entityManager);
            System.err.println("FOO is : " + entityManager.find(Constant.class, foo.getId()) + ", BAR is : " + entityManager.find(Constant.class, bar.getId()));
            benchmarkState.commitTransaction(entityManager);
        } finally {
            entityManager.close();
        }
    }*/
}
