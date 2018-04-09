package org.jboss.perf.hibernate;

import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.mock.jdbc.MockResultSet;
import org.jboss.perf.hibernate.model.Hundred;
import org.jboss.perf.hibernate.model.Person;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;
import org.perfmock.PerfMockDriver;

import javax.persistence.metamodel.SingularAttribute;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Designed to test enhanced/non-enhanced entities.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HundredBenchmark extends BenchmarkBase {

   @State(Scope.Benchmark)
   public static class HundredBenchmarkState extends BenchmarkState<Hundred> {

      @Param("1")
      int modifications;

      private SingularAttribute<Person, String> firstName;
      private SingularAttribute<Person, String> middleName;
      private SingularAttribute<Person, String> lastName;

      @Setup
      public void setupAttributes() {
         firstName = (SingularAttribute<Person, String>) getEntityManagerFactory().getMetamodel().entity(Person.class).getSingularAttribute("firstName");
         middleName = (SingularAttribute<Person, String>) getEntityManagerFactory().getMetamodel().entity(Person.class).getSingularAttribute("middleName");
         lastName = (SingularAttribute<Person, String>) getEntityManagerFactory().getMetamodel().entity(Person.class).getSingularAttribute("lastName");
      }

      @Override
      public void setupMock() {
         super.setupMock();
         PreparedStatementResultSetHandler handler = PerfMockDriver.getInstance().getPreparedStatementHandler();

         MockResultSet all = handler.createResultSet();
         all.addColumn("col_0_0_", seq(0, dbSize));
         handler.prepareResultSet("select hundred0_\\.id as col_0_0_ from Hundred hundred0_", all);

         MockResultSet single = handler.createResultSet();
         single.addColumn("id1_5_0_", Collections.singletonList(0));
         for (int i = 0; i < 100; ++i) {
            single.addColumn(String.format("p%d_5_0_", i + 2), Collections.singletonList(0));
         }
         handler.prepareResultSet("select hundred0_\\.id as id1_5_0_,.* where hundred0_\\.id=\\?", single);

         MockResultSet many = handler.createResultSet();
         many.addColumn("id1_5_", seq(0, 10));
         List<Object> zeroes = list(transactionSize, 0);
         for (int i = 0; i < 100; ++i) {
            many.addColumn(String.format("p%d_5_", i + 2), zeroes);
         }
         handler.prepareResultSet("select hundred0_\\.id as id1_5_,.* where hundred0_\\.id in .*", many);

         handler.prepareGeneratedKeys("insert into Hundred .*", getIncrementing(dbSize));
         handler.prepareUpdateCount("update Hundred set .* where id=\\?", 1);

         handler.prepareUpdateCount("delete from Hundred where id=\\?", 1);
         handler.prepareUpdateCount("delete from Hundred where id in \\(.*\\)", transactionSize);
      }

      @Override
      public Class<Hundred> getClazz() {
         return Hundred.class;
      }

      @Override
      protected boolean hasForeignKeys() {
         return false;
      }

      @Override
      public Hundred randomEntity(ThreadLocalRandom random) {
         Hundred hundred = new Hundred();
         for (int i = 0; i < 100; ++i) {
            hundred.set(i, random.nextInt());
         }
         return hundred;
      }

      @Override
      public void modify(Hundred entity, ThreadLocalRandom random) {
         for (int i = 0; i < modifications; ++i) {
            entity.set(random.nextInt(100), random.nextInt());
         }
      }
   }

   @Benchmark
   public void testCreate(HundredBenchmarkState benchmarkState, ThreadState threadState) throws Exception {
      super.testCreate(benchmarkState, threadState);
   }

   @Benchmark
   public void testRead(HundredBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
      super.testRead(benchmarkState, threadState, blackhole);
   }

   @Benchmark
   public void testCriteriaRead(HundredBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
      super.testCriteriaRead(benchmarkState, threadState, blackhole);
   }

   @Benchmark
   public void testUpdate(HundredBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
      super.testUpdate(benchmarkState, threadState, threadParams);
   }

   @Benchmark
   public void testCriteriaUpdate(HundredBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
      super.testCriteriaUpdate(benchmarkState, threadState, threadParams);
   }

   @Benchmark
   public void testDelete(HundredBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
      super.testDelete(benchmarkState, threadState);
   }

   @Benchmark
   public void testCriteriaDelete(HundredBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
      super.testCriteriaDelete(benchmarkState, threadState, blackhole);
   }

}
