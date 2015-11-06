package org.jboss.perf.hibernate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;

import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.mock.jdbc.EvaluableResultSet;
import com.mockrunner.mock.jdbc.MockResultSet;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.jboss.perf.hibernate.model.Constant;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;
import org.perfmock.PerfMockDriver;

/**
 * This benchmark is now set up to use mocking. You can switch mutation
 * with mutate=true|false params. If used without mocking you need to execute
 * the mutations against the DB and update set of ids; for that, set mutate=false
 * and uncomment the annotations on {@link #updater(ConstantState, UpdaterState)},
 * {@link #mutate(ConstantState, ThreadState, ThreadParams, Blackhole)} and
 * {@link #testRead(ConstantState, ThreadState, Blackhole)} methods.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ConstantBenchmark extends BenchmarkBase<Constant> {
    private final static int QUERY_MAX_RESULTS = 1000;

    @State(Scope.Benchmark)
    public static class ConstantState extends BenchmarkState<Constant> {
        private AtomicReferenceArray loadedIds;
        private ScheduledExecutorService mutator = Executors.newScheduledThreadPool(1);

        @Param({ "100" })
        long mutationPeriod;

        @Param({ "1000" })
        int mutationCount;

        @Param("true")
        boolean mutate;

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
            for (int i = 0; i < dbSize; ++i) {
                loadedIds.set(i, (long) i);
            }
            AtomicLong nextId = new AtomicLong(dbSize);
            if (mutate) {
                mutator.scheduleAtFixedRate(() -> {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < mutationCount; ++i) {
                        loadedIds.set(random.nextInt(dbSize), nextId.incrementAndGet());
                    }
                }, 0, mutationPeriod, TimeUnit.MILLISECONDS);
            }
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
            mutator.shutdown();
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

        @Override
        public void setupMock() {
            super.setupMock();
            PreparedStatementResultSetHandler handler = PerfMockDriver.getInstance().getPreparedStatementHandler();

            MockResultSet all = handler.createResultSet();
            all.addColumn("col_0_0_", seq(0, dbSize));
            handler.prepareResultSet("select constant0_\\.id as col_0_0_ from Constant constant0_", all);

            MockResultSet single = handler.createResultSet();
            single.addColumn("id1_1_0_", new Object[] { 0L });
            single.addColumn("value2_1_0_", new Object[] { "foo" });
            handler.prepareResultSet("select constant0_\\.id as id1_1_0_, constant0_\\.value as value2_1_0_ from Constant constant0_ where constant0_.id=\\?", single);
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

//    @Group
//    @GroupThreads(1)
//    @Benchmark
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
//        if (newIdsIndex < newIds.size()) {
//            log.debugf("Finished dirty update, %d new entities ignored", newIds.size() - newIdsIndex);
//        } else {
//            log.debugf("Finished dirty update, %d left empty", newIdsIndex - newIds.size());
//        }
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

//    @Group
//    @GroupThreads(10)
    @Benchmark
    public void testRead(ConstantState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        EntityManager entityManager = benchmarkState.getEntityManagerFactory().createEntityManager();
        try {
            if (benchmarkState.useTx) {
                benchmarkState.beginTransaction(entityManager);
            }
            try {
                for (int i = 0; i < benchmarkState.transactionSize; ++i) {
                    Object id = getIdNotNull(benchmarkState.loadedIds, threadState.random.nextInt(benchmarkState.dbSize));
                    Constant entity = entityManager.find(benchmarkState.getClazz(), id);
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
                benchmarkState.rollbackTransaction(entityManager);
                throw e;
            }
        } finally {
            entityManager.close();
        }
    }

//    @Group
//    @GroupThreads(2)
//    @Benchmark
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
}
