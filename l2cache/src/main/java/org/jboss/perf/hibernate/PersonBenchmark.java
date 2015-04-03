package org.jboss.perf.hibernate;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.SingularAttribute;

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
        super.testQuery(benchmarkState, blackhole, new QueryRunner() {
            @Override
            public Collection<?> runQuery(EntityManager entityManager, CriteriaBuilder cb) {
                CriteriaQuery<Person> query = cb.createQuery(Person.class);
                Predicate predicate = cb.like(query.from(Person.class).get(benchmarkState.firstName), pattern);
                query.where(predicate);
                List<Person> resultList = entityManager.createQuery(query).getResultList();
                return resultList;
            }
        });
    }

    @Benchmark
    public void testQuery2Fields(final PersonBenchmarkState benchmarkState, final ThreadState threadState, Blackhole blackhole) throws Exception {
        final String pattern = Randomizer.randomStringBuilder(2, 2, threadState.random).append("%").toString();
        super.testQuery(benchmarkState, blackhole, new QueryRunner() {
            @Override
            public Collection<?> runQuery(EntityManager entityManager, CriteriaBuilder cb) {
                CriteriaQuery<Person> query = cb.createQuery(Person.class);
                Root<Person> root = query.from(Person.class);
                Predicate predicate = cb.like(root.get(benchmarkState.firstName), pattern);
                predicate = cb.and(predicate, cb.like(root.get(benchmarkState.middleName), pattern));
                query.where(predicate);
                List<Person> resultList = entityManager.createQuery(query).getResultList();
                return resultList;
            }
        });
    }

    @Benchmark
    public void testQuery3Fields(final PersonBenchmarkState benchmarkState, final ThreadState threadState, Blackhole blackhole) throws Exception {
        final String pattern = Randomizer.randomStringBuilder(2, 2, threadState.random).append("%").toString();
        super.testQuery(benchmarkState, blackhole, new QueryRunner() {
            @Override
            public Collection<?> runQuery(EntityManager entityManager, CriteriaBuilder cb) {
                CriteriaQuery<Person> query = cb.createQuery(Person.class);
                Root<Person> root = query.from(Person.class);
                Predicate predicate = cb.like(root.get(benchmarkState.firstName), pattern);
                predicate = cb.and(predicate, cb.like(root.get(benchmarkState.middleName), pattern));
                predicate = cb.and(predicate, cb.like(root.get(benchmarkState.lastName), pattern));
                query.where(predicate);
                List<Person> resultList = entityManager.createQuery(query).getResultList();
                return resultList;
            }
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
