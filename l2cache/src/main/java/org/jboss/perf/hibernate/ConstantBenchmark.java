package org.jboss.perf.hibernate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.jboss.perf.hibernate.model.Constant;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ConstantBenchmark extends BenchmarkBase<Constant> {
    private final static int QUERY_MAX_RESULTS = 1000;

    public static class ConstantState extends BenchmarkState<Constant> {
        private AtomicReferenceArray loadedIds;

        @Override
        protected Map<String, String> getSecondLevelCacheProperties() {
            Map<String, String> properties = super.getSecondLevelCacheProperties();
            if (secondLevelCache.equals("nontx")) {
                properties.put("hibernate.cache.infinispan." + Constant.class.getName() + ".cfg", "immutable-entity");
            }
            return properties;
        }

        @Override
        public void setup() throws Throwable {
            super.setup();
            loadedIds = new AtomicReferenceArray(dbSize);
        }

        private boolean first = true;

        @Override
        public void refreshDB() throws Exception {
            if (first) {
                super.refreshDB();
                first = false;
            }
        }

        @Override
        public void shutdown() throws Throwable {
//            Statistics stats = getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
//            System.err.println(stats);
            super.shutdown();
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
    protected void onReadNull(Object id) {
        // don't worry, be happy...
    }

    @State(Scope.Thread)
    public static class UpdaterState {
        private HashMap<Object, EntityRecord> idToIndex = new HashMap<>();
    }

    private static class EntityRecord {
        int index;
        boolean found;

        public EntityRecord(int index, boolean found) {
            this.index = index;
            this.found = found;
        }
    }

    @Group
    @GroupThreads(1)
    @Benchmark
    public void updater(ConstantState benchmarkState, UpdaterState updaterState) throws Exception {
        ArrayList<Object> newIds = new ArrayList<Object>();
        boolean[] found = new boolean[benchmarkState.loadedIds.length()];

        EntityManager entityManager = benchmarkState.getEntityManagerFactory().createEntityManager();
        benchmarkState.beginTransaction(entityManager);
        int existing = 0;
        try {
            for (int offset = 0;; offset += QUERY_MAX_RESULTS) {
                List<Object> list = dirtyList(benchmarkState, entityManager, offset, QUERY_MAX_RESULTS);
                for (Object id : list) {
                    EntityRecord record = updaterState.idToIndex.get(id);
                    if (record != null) {
                        found[record.index] = true;
                        record.found = true;
                        ++existing;
                    } else {
                        newIds.add(id);
                    }
                }
                if (list.size() < QUERY_MAX_RESULTS) break;
            }
        } finally {
            benchmarkState.commitTransaction(entityManager);
            entityManager.close();
        }
        trace("Finished dirty enumeration, " + existing + " existing, " + newIds.size() + " new IDs");

        for (Iterator<EntityRecord> iterator = updaterState.idToIndex.values().iterator(); iterator.hasNext(); ) {
            EntityRecord record = iterator.next();
            if (record.found) {
                record.found = false;
            } else {
                iterator.remove();
            }
        }

        int newIdsIndex = 0;
        for (int index = 0; index < found.length; ++index) {
            if (found[index]) {
                continue;
            }
            if (newIdsIndex < newIds.size()) {
                Object newId = newIds.get(newIdsIndex);
                //if (trace) log.tracef("Replacing removed entry with %s on %d", newId, index);
                benchmarkState.loadedIds.set(index, newId);
                updaterState.idToIndex.put(newId, new EntityRecord(index, false));
            } else {
                //if (trace) log.tracef("Nothing to replace with on %d", index);
                benchmarkState.loadedIds.set(index, null);
            }
            newIdsIndex++;
        }
        if (newIdsIndex < newIds.size()) {
            //log.debugf("Finished dirty update, %d new entities ignored", newIds.size() - newIdsIndex);
        } else {
            //log.debugf("Finished dirty update, %d left empty", newIdsIndex - newIds.size());
        }

    }

    private List<Object> dirtyList(ConstantState benchmarkState, EntityManager entityManager, int offset, int maxResults) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root root = query.from(benchmarkState.getClazz());
        Path idPath = root.get(benchmarkState.getIdProperty());
        query.select(idPath);
        return entityManager.createQuery(query).setLockMode(LockModeType.NONE)
              .setFirstResult(offset).setMaxResults(maxResults)
              .getResultList();
    }

    private Object getIdNotNull(AtomicReferenceArray loadedIds, int index) {
        Object id;
        for (;;) {
            id = loadedIds.get(index);
            if (id != null) {
                return id;
            }
            index = (index + 1) % loadedIds.length();
        }
    }

    @Group
    @GroupThreads(10)
    @Benchmark
    public void testRead(ConstantState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        EntityManager entityManager = benchmarkState.getEntityManagerFactory().createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            try {
                for (int i = 0; i < benchmarkState.transactionSize; ++i) {
                    Object id = getIdNotNull(benchmarkState.loadedIds, threadState.random.nextInt(benchmarkState.dbSize));
                    Constant entity = entityManager.find(benchmarkState.getClazz(), id);
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

    @Group
    @GroupThreads(2)
    @Benchmark
    public void mutate(ConstantState benchmarkState, ThreadState threadState, ThreadParams threadParams, Blackhole blackhole) throws Exception {
        EntityManager entityManager = benchmarkState.getEntityManagerFactory().createEntityManager();
        try {
            benchmarkState.beginTransaction(entityManager);
            for (int i = 0; i < benchmarkState.transactionSize; ++i) {
                int index;
                Object id, entity;
                do {
                    index = threadState.random.nextInt(benchmarkState.loadedIds.length());
                    id = getIdNotNull(benchmarkState.loadedIds, index);
                    entity = entityManager.find(benchmarkState.getClazz(), id);
                } while (entity == null);
                entityManager.remove(entity);
                entity = benchmarkState.randomEntity(threadState.random);
                entityManager.persist(entity);
            }
        } finally {
            try {
                benchmarkState.commitTransaction(entityManager);
            } catch (Exception e) {
                benchmarkState.rollbackTransaction(entityManager);
            }
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
