package org.jboss.perf.hibernate;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.mock.jdbc.MockResultSet;
import org.jboss.perf.hibernate.model.Beagle;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;
import org.perfmock.PerfMockDriver;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class DogBenchmark extends BenchmarkBase<Beagle> {

    @State(Scope.Benchmark)
    public static class BeagleBenchmarkState extends BenchmarkState<Beagle> {
        @Override
        public Class<Beagle> getClazz() {
            return Beagle.class;
        }

        @Override
        protected boolean hasForeignKeys() {
            return false;
        }

        @Override
        public Beagle randomEntity(ThreadLocalRandom random) {
            Beagle beagle = new Beagle();
            beagle.setFoo(Randomizer.randomString(2, 12, random));
            beagle.setBar(Randomizer.randomString(2, 12, random));
            beagle.setGoo(Randomizer.randomString(6, 12, random));
            return beagle;
        }

        @Override
        public void modify(Beagle beagle, ThreadLocalRandom random) {
            beagle.setFoo(Randomizer.randomString(2, 12, random));
        }

        @Override
        public void setupMock() {
            super.setupMock();
            PreparedStatementResultSetHandler handler = PerfMockDriver.getInstance().getPreparedStatementHandler();

            MockResultSet all = handler.createResultSet();
            all.addColumn("col_0_0_", seq(0, dbSize));
            handler.prepareResultSet("select beagle0_.id as col_0_0_ from Beagle beagle0_ inner join Dog beagle0_1_ on beagle0_.id=beagle0_1_.id inner join Mammal beagle0_2_ on beagle0_.id=beagle0_2_.id", all);

            MockResultSet single = handler.createResultSet();
            single.addColumn("id1_5_0_", Collections.singletonList(1L));
            single.addColumn("foo2_5_0_", Collections.singletonList("foo"));
            single.addColumn("bar1_2_0_", Collections.singletonList("bar"));
            single.addColumn("goo1_0_0_", Collections.singletonList("goo"));
            handler.prepareResultSet("select beagle0_.id as id1_5_0_, beagle0_2_.foo as foo2_5_0_, beagle0_1_.bar as bar1_2_0_, beagle0_.goo as goo1_0_0_ from Beagle beagle0_ inner join Dog beagle0_1_ on beagle0_.id=beagle0_1_.id inner join Mammal beagle0_2_ on beagle0_.id=beagle0_2_.id where beagle0_.id=\\?", single);

            handler.prepareUpdateCount("insert into Mammal \\(foo, id\\) values \\(\\?, \\?\\)", 1);
            handler.prepareGeneratedKeys("insert into Mammal \\(id, foo\\) values \\(null, \\?\\)", getIncrementing(dbSize));
            handler.prepareUpdateCount("insert into Dog \\(bar, id\\) values \\(\\?, \\?\\)", 1);
            handler.prepareUpdateCount("insert into Beagle \\(goo, id\\) values \\(\\?, \\?\\)", 1);

            handler.prepareUpdateCount("update Mammal set foo=\\? where id=\\?", 1);

            handler.prepareUpdateCount("delete from Beagle where id=\\?", 1);

            MockResultSet criteriaRead = handler.createResultSet();
            criteriaRead.addColumn("id1_5_", seq(0, transactionSize));
            criteriaRead.addColumn("foo2_5_", list(transactionSize, "foo"));
            criteriaRead.addColumn("bar1_2_", list(transactionSize, "bar"));
            criteriaRead.addColumn("goo1_0_", list(transactionSize, "goo"));
            handler.prepareResultSet("select beagle0_.id as id1_5_, beagle0_2_.foo as foo2_5_, beagle0_1_.bar as bar1_2_, beagle0_.goo as goo1_0_ from Beagle beagle0_ inner join Dog beagle0_1_ on beagle0_.id=beagle0_1_.id inner join Mammal beagle0_2_ on beagle0_.id=beagle0_2_.id where beagle0_.id in \\(.*\\)", criteriaRead);
        }
    }

    @Benchmark
    public void testCreate(BeagleBenchmarkState benchmarkState, ThreadState threadState) throws Exception {
        super.testCreate(benchmarkState, threadState);
    }

    @Benchmark
    public void testRead(BeagleBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testRead(benchmarkState, threadState, blackhole);
    }

    @Benchmark
    public void testCriteriaRead(BeagleBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testCriteriaRead(benchmarkState, threadState, blackhole);
    }

    @Benchmark
    public void testUpdate(BeagleBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        super.testUpdate(benchmarkState, threadState, threadParams);
    }

    @Benchmark
    public void testCriteriaUpdate(BeagleBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        super.testCriteriaUpdate(benchmarkState, threadState, threadParams);
    }

    @Benchmark
    public void testDelete(BeagleBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testDelete(benchmarkState, threadState);
    }
}
