package org.jboss.perf.hibernate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.SingularAttribute;

import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.mock.jdbc.MockResultSet;
import org.jboss.perf.hibernate.model.Person;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.perfmock.PerfMockDriver;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PersonBenchmark extends BenchmarkBase<Person> {

    @State(Scope.Benchmark)
    public static class PersonBenchmarkState extends BenchmarkState<Person> {

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
            handler.prepareResultSet("select person0_.id as col_0_0_ from Person person0_", all);

            handler.prepareUpdateCount("insert into Person \\(firstName, lastName, middleName, id\\) values \\(\\?, \\?, \\?, \\?\\)", 1);
            // for Hibernate 4.x
            handler.prepareGeneratedKeys("insert into Person \\(id, firstName, lastName, middleName\\) values \\(null, \\?, \\?, \\?\\)", getIncrementing(dbSize));

            MockResultSet readPerson = handler.createResultSet();
            readPerson.addColumn("id1_8_0_", Collections.singletonList(1L));
            readPerson.addColumn("firstNam2_8_0_", Collections.singletonList("firstName"));
            readPerson.addColumn("lastName3_8_0_", Collections.singletonList("lastName"));
            readPerson.addColumn("middleNa4_8_0_", Collections.singletonList("middleName"));
            handler.prepareResultSet("select person0_\\.id as id1_8_0_, person0_\\.firstName as firstNam2_8_0_, person0_\\.lastName as lastName3_8_0_, person0_\\.middleName as middleNa4_8_0_ from Person person0_ where person0_\\.id=\\?", readPerson);

            handler.prepareUpdateCount("update Person set firstName=\\?, lastName=\\?, middleName=\\? where id=\\?", 1);

            handler.prepareUpdateCount("delete from Person where id=\\?", 1);

            MockResultSet readPersons = handler.createResultSet();
            readPersons.addColumn("id1_8_", seq(0, transactionSize));
            readPersons.addColumn("firstNam2_8_", list(transactionSize, "firstName"));
            readPersons.addColumn("lastName3_8_", list(transactionSize, "lastName"));
            readPersons.addColumn("middleNa4_8_", list(transactionSize, "middleName"));
            handler.prepareResultSet("select person0_\\.id as id1_8_, person0_\\.firstName as firstNam2_8_, person0_\\.lastName as lastName3_8_, person0_\\.middleName as middleNa4_8_ from Person person0_ where person0_\\.id in \\(.*\\)", readPersons);

            handler.prepareResultSet("select person0_\\.id as id1_8_, person0_\\.firstName as firstNam2_8_, person0_\\.lastName as lastName3_8_, person0_\\.middleName as middleNa4_8_ from Person person0_ where person0_\\.firstName like \\?", readPersons);
            handler.prepareResultSet("select person0_\\.id as id1_8_, person0_\\.firstName as firstNam2_8_, person0_\\.lastName as lastName3_8_, person0_\\.middleName as middleNa4_8_ from Person person0_ where \\(person0_\\.firstName like \\?\\) and \\(person0_\\.middleName like \\?\\)", readPersons);
            handler.prepareResultSet("select person0_\\.id as id1_8_, person0_\\.firstName as firstNam2_8_, person0_\\.lastName as lastName3_8_, person0_\\.middleName as middleNa4_8_ from Person person0_ where \\(person0_\\.firstName like \\?\\) and \\(person0_\\.middleName like \\?\\) and \\(person0_.lastName like \\?\\)", readPersons);

            handler.prepareUpdateCount("delete from Person where id in \\(.*\\)", transactionSize);
        }

        @Override
        public Class<Person> getClazz() {
            return Person.class;
        }

        @Override
        protected boolean hasForeignKeys() {
            return false;
        }

        @Override
        public Person randomEntity(ThreadLocalRandom random) {
            return new Person(Randomizer.randomString(2, 12, random), Randomizer.randomString(2, 12, random), Randomizer.randomString(6, 12, random));
        }

        @Override
        public void modify(Person entity, ThreadLocalRandom random) {
            entity.setFirstName(Randomizer.randomString(2, 12, random));
        }
    }

    @Benchmark
    public void testCreate(PersonBenchmarkState benchmarkState, ThreadState threadState) throws Exception {
        super.testCreate(benchmarkState, threadState);
    }

    @Benchmark
    public void testRead(PersonBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testRead(benchmarkState, threadState, blackhole);
    }

    @Benchmark
    public void testCriteriaRead(PersonBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testCriteriaRead(benchmarkState, threadState, blackhole);
    }

    @Benchmark
    public void testUpdate(PersonBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        super.testUpdate(benchmarkState, threadState, threadParams);
    }

    @Benchmark
    public void testCriteriaUpdate(PersonBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        super.testCriteriaUpdate(benchmarkState, threadState, threadParams);
    }

    @Benchmark
    public void testDelete(PersonBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testDelete(benchmarkState, threadState);
    }

    @Benchmark
    public void testCriteriaDelete(PersonBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testCriteriaDelete(benchmarkState, threadState, blackhole);
    }

    @Benchmark
    public void testQuery1Field(final PersonBenchmarkState benchmarkState, final ThreadState threadState, Blackhole blackhole) throws Exception {
        final String pattern = Randomizer.randomStringBuilder(2, 2, threadState.random).append("%").toString();
        super.testQuery(benchmarkState, blackhole, (entityManager, cb) -> {
            CriteriaQuery<Person> query = cb.createQuery(Person.class);
            Predicate predicate = cb.like(query.from(Person.class).get(benchmarkState.firstName), pattern);
            query.where(predicate);
            List<Person> resultList = entityManager.createQuery(query).getResultList();
            return resultList;
        });
    }

    @Benchmark
    public void testQuery2Fields(final PersonBenchmarkState benchmarkState, final ThreadState threadState, Blackhole blackhole) throws Exception {
        final String pattern = Randomizer.randomStringBuilder(2, 2, threadState.random).append("%").toString();
        super.testQuery(benchmarkState, blackhole, (entityManager, cb) -> {
            CriteriaQuery<Person> query = cb.createQuery(Person.class);
            Root<Person> root = query.from(Person.class);
            Predicate predicate = cb.like(root.get(benchmarkState.firstName), pattern);
            predicate = cb.and(predicate, cb.like(root.get(benchmarkState.middleName), pattern));
            query.where(predicate);
            List<Person> resultList = entityManager.createQuery(query).getResultList();
            return resultList;
        });
    }

    @Benchmark
    public void testQuery3Fields(final PersonBenchmarkState benchmarkState, final ThreadState threadState, Blackhole blackhole) throws Exception {
        final String pattern = Randomizer.randomStringBuilder(2, 2, threadState.random).append("%").toString();
        super.testQuery(benchmarkState, blackhole, (entityManager, cb) -> {
            CriteriaQuery<Person> query = cb.createQuery(Person.class);
            Root<Person> root = query.from(Person.class);
            Predicate predicate = cb.like(root.get(benchmarkState.firstName), pattern);
            predicate = cb.and(predicate, cb.like(root.get(benchmarkState.middleName), pattern));
            predicate = cb.and(predicate, cb.like(root.get(benchmarkState.lastName), pattern));
            query.where(predicate);
            List<Person> resultList = entityManager.createQuery(query).getResultList();
            return resultList;
        });
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
              .include(PersonBenchmark.class.getSimpleName())
              .warmupIterations(5)
              .measurementIterations(5)
              .forks(1)
              .jvmArgs(
                    //"-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005",
                    "-Djava.net.preferIPv4Stack=true",
                    "-Dcom.arjuna.ats.arjuna.common.propertiesFile=default-jbossts-properties.xml")
              //.threads(5)
              .build();

        new Runner(opt).run();
    }
}
