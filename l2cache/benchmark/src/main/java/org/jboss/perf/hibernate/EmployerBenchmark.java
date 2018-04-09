package org.jboss.perf.hibernate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SingularAttribute;

import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.mock.jdbc.EvaluableResultSet;
import com.mockrunner.mock.jdbc.MockResultSet;
import org.jboss.perf.hibernate.model.Employee;
import org.jboss.perf.hibernate.model.Employer;
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
        public void setupMock() {
            super.setupMock();
            PreparedStatementResultSetHandler handler = PerfMockDriver.getInstance().getPreparedStatementHandler();
            EvaluableResultSet.Factory evaluableResultSetFactory = new EvaluableResultSet.Factory(true);

            MockResultSet all = handler.createResultSet();
            all.addColumn("col_0_0_", seq(0, dbSize));
            handler.prepareResultSet("select employer0_\\.id as col_0_0_ from Employer employer0_", all);

            handler.prepareUpdateCount("insert into Employer \\(name, id\\) values \\(\\?, \\?\\)", 1);
            handler.prepareUpdateCount("insert into Employee \\(employer_id, name, id\\) values \\(\\?, \\?, \\?\\)", 1);
            handler.prepareGeneratedKeys("insert into Employer \\(id, name\\) values \\(null, \\?\\)", getIncrementing(dbSize));
            handler.prepareGeneratedKeys("insert into Employee \\(id, employer_id, name\\) values \\(null, \\?, \\?\\)", getIncrementing(dbSize));

            MockResultSet single = handler.createResultSet();
            single.addColumn("id1_4_0_", Collections.singletonList(1L));
            single.addColumn("name2_4_0_", Collections.singletonList("employer"));
            handler.prepareResultSet("select employer0_\\.id as id1_4_0_, employer0_\\.name as name2_4_0_ from Employer employer0_ where employer0_\\.id=\\?", single);

            EvaluableResultSet withEmployees = evaluableResultSetFactory.create("withEmployees");
            List<Object> idList = list(10, BenchmarkBase::getFirstParam);
            List<Object> idSeq = seq(0, 10);
            withEmployees.addColumn("id1_4_0_", idList);
            withEmployees.addColumn("name2_4_0_", list(10, "employer"));
            withEmployees.addColumn("employer3_4_1_", idList);
            withEmployees.addColumn("employer3_3_1_", idList);
            withEmployees.addColumn("id1_3_1_", idSeq);
            withEmployees.addColumn("id1_3_2_", idSeq);
            withEmployees.addColumn("employer3_3_2_", idList);
            withEmployees.addColumn("name2_3_2_", list(10, "employee"));
            handler.prepareResultSet("select employer0_\\.id as id1_4_0_, employer0_\\.name as name2_4_0_, employees1_\\.employer_id as employer3_._1_, "
                  + "employees1_\\.id as id1_3_1_, employees1_\\.id as id1_3_2_, employees1_\\.employer_id as employer3_3_2_, employees1_\\.name as name2_3_2_ "
                  + "from Employer employer0_ left outer join Employee employees1_ on employer0_\\.id=employees1_\\.employer_id where employer0_\\.id=\\?", withEmployees);


            EvaluableResultSet employee = evaluableResultSetFactory.create("employees");
            employee.addColumn("employer3_4_0_", idList);
            employee.addColumn("employer3_3_0_", idList);
            employee.addColumn("id1_3_0_", idSeq);
            employee.addColumn("id1_3_1_", idSeq);
            employee.addColumn("employer3_3_1_", idList);
            employee.addColumn("name2_3_1_", list(10, "employee"));
            handler.prepareResultSet("select employees0_\\.employer_id as employer3_._0_, employees0_\\.id as id1_3_0_, employees0_\\.id as id1_3_1_, "
                  + "employees0_\\.employer_id as employer3_3_1_, employees0_\\.name as name2_3_1_ from Employee employees0_ where employees0_\\.employer_id=\\?", employee);

            EvaluableResultSet employers = evaluableResultSetFactory.create("employers");
            employers.addColumn("id1_4_", list(10, BenchmarkBase::addIdFromRange));
            employers.addColumn("name2_4_", list(10, "employer"));
            handler.prepareResultSet("select employer0_\\.id as id1_4_, employer0_\\.name as name2_4_ from Employer employer0_ where employer0_\\.id in \\(.*\\)", employers);

            MockResultSet queryResult = handler.createResultSet();
            queryResult.addColumn("id1_4_", new Object[] { 0 });
            queryResult.addColumn("name2_4_", new Object[] { "employer" });
            handler.prepareResultSet("select employer0_\\.id as id1_4_, employer0_\\.name as name2_4_ from Employer employer0_ inner join Employee employees1_ " +
                  "on employer0_\\.id=employees1_\\.employer_id where employees1_\\.name like \\?", queryResult);

            handler.prepareUpdateCount("update Employee set employer_id=\\?, name=\\? where id=\\?", 1);
            handler.prepareUpdateCount("delete from Employer where id=\\?", 1);
            handler.prepareUpdateCount("delete from Employee where id=\\?", 1);
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
                // do not replace new hire, NPE thrown: HHH-12464
                if (employees.get(index).getId() == 0) {
                    --i;
                } else {
                    employees.set(index, new Employee(Randomizer.randomString(5, 20), employer));
                }
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
        super.testQuery(benchmarkState, blackhole, (entityManager, cb) -> {
            CriteriaQuery<Employer> query = cb.createQuery(Employer.class);
            Root<Employer> root = query.from(Employer.class);
            Path<String> path = root.join(benchmarkState.employees).get(benchmarkState.employeeName);
            query.select(root).where(cb.like(path, prefix));
            return entityManager.createQuery(query).getResultList();
        });
    }
}
