package org.jboss.perf.hibernate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;

import org.jboss.perf.hibernate.model.Employee;
import org.jboss.perf.hibernate.model.Employer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class EmployerBenchmark extends BenchmarkBase<Employer> {

    @State(Scope.Benchmark)
    public static class EmployerBenchmarkState extends BenchmarkState<Employer> {

        private ListAttribute<Employer, Employee> employees;
        private SingularAttribute<? super Employee, String> employeeName;

        @Setup
        public void setupAttributes() {
            employees = (ListAttribute<Employer, Employee>) getEntityManagerFactory().getMetamodel().entity(Employer.class).getAttribute("employees");
            employeeName = getEntityManagerFactory().getMetamodel().entity(Employee.class).getSingularAttribute("name", String.class);
        }

        @Override
        public Class<Employer> getClazz() {
            return Employer.class;
        }

        @Override
        protected boolean hasForeignKeys() {
            return true;
        }

        @Override
        public Employer randomEntity(ThreadLocalRandom random) {
            int employees = random.nextInt(5, 20);
            ArrayList<Employee> employeesList = new ArrayList<Employee>(employees);
            Employer employer = new Employer(Randomizer.randomString(5, 20), employeesList);
            for (int i = 0; i < employees; ++i) {
                employeesList.add(new Employee(Randomizer.randomString(5, 20), employer));
            }
            return employer;
        }

        @Override
        public void modify(Employer employer, ThreadLocalRandom random) {
            if (employer.getEmployees() == null || employer.getEmployees().isEmpty()) {
                throw new IllegalArgumentException(employer.getEmployees().toString());
            }
            List<Employee> employees = employer.getEmployees();
            // sack and hire up to 3 employees
            for (int i = 0; i < 3; ++i) {
                int index = random.nextInt(employees.size());
                employees.set(index, new Employee(Randomizer.randomString(5, 20), employer));
            }
        }
    }

    @Benchmark
    public void testCreate(EmployerBenchmarkState benchmarkState, ThreadState threadState) throws Exception {
        super.testCreate(benchmarkState, threadState);
    }

    @Benchmark
    public void testRead(EmployerBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testRead(benchmarkState, threadState, blackhole);
    }

    @Benchmark
    public void testCriteriaRead(EmployerBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testCriteriaRead(benchmarkState, threadState, blackhole);
    }

    @Benchmark
    public void testUpdate(EmployerBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        super.testUpdate(benchmarkState, threadState, threadParams);
    }

    @Benchmark
    public void testCriteriaUpdate(EmployerBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        super.testCriteriaUpdate(benchmarkState, threadState, threadParams);
    }

    @Benchmark
    public void testDelete(EmployerBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testDelete(benchmarkState, threadState);
    }

    @Benchmark
    public void testQueryEmployerFor(final EmployerBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        final String prefix = Randomizer.randomStringBuilder(2, 2, threadState.random).append('%').toString();
        super.testQuery(benchmarkState, blackhole, new QueryRunner() {
            @Override
            public Collection<?> runQuery(EntityManager entityManager, CriteriaBuilder cb) {
                CriteriaQuery<Employer> query = cb.createQuery(Employer.class);
                Root<Employer> root = query.from(Employer.class);
                Path<String> path = root.join(benchmarkState.employees).get(benchmarkState.employeeName);
                query.select(root).where(cb.like(path, prefix));
                return entityManager.createQuery(query).getResultList();
            }
        });
    }
}
