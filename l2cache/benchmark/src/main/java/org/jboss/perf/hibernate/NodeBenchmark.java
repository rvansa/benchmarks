package org.jboss.perf.hibernate;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Subquery;
import javax.persistence.metamodel.SingularAttribute;

import com.mockrunner.jdbc.PreparedStatementResultSetHandler;
import com.mockrunner.mock.jdbc.EvaluableResultSet;
import com.mockrunner.mock.jdbc.MockResultSet;
import org.jboss.perf.hibernate.model.Node;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;
import org.perfmock.PerfMockDriver;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class NodeBenchmark extends BenchmarkBase<Node> {

    @State(Scope.Benchmark)
    public static class NodeBenchmarkState extends BenchmarkState<Node> {
        @Param(value = "20")
        public int treeSize;

        @Param(value = "5")
        public int maxModifiedSize;

        private SingularAttribute<? super Node, Node> left;
        private SingularAttribute<? super Node, Node> right;
        private SingularAttribute<? super Node, Integer> leftSize;
        private SingularAttribute<? super Node, Integer> rightSize;

        @Setup
        public void setupAttributes() {
            left = getEntityManagerFactory().getMetamodel().entity(Node.class).getSingularAttribute("left", Node.class);
            leftSize = getEntityManagerFactory().getMetamodel().entity(Node.class).getSingularAttribute("leftSize", int.class);
            right = getEntityManagerFactory().getMetamodel().entity(Node.class).getSingularAttribute("right", Node.class);
            rightSize = getEntityManagerFactory().getMetamodel().entity(Node.class).getSingularAttribute("rightSize", int.class);
        }

        @Override
        public void setupMock() {
            super.setupMock();
            EvaluableResultSet.Factory factory = new EvaluableResultSet.Factory(true);
            PreparedStatementResultSetHandler handler = PerfMockDriver.getInstance().getPreparedStatementHandler();

            MockResultSet all = handler.createResultSet();
            all.addColumn("col_0_0_", seq(0, dbSize));
            handler.prepareResultSet("select node0_.id as col_0_0_ from Node node0_ where node0_.root=\\?", all);

            handler.prepareGeneratedKeys("insert into Node \\(id, left_id, leftSize, right_id, rightSize, root\\) values \\(null, \\?, \\?, \\?, \\?, \\?\\)", getIncrementing(dbSize));

            MockResultSet single = handler.createResultSet();
            single.addColumn("id1_6_0_", Collections.singletonList(1L));
            single.addColumn("left_id5_6_0_", Collections.singletonList(2L));
            single.addColumn("leftSize2_6_0_", Collections.singletonList(49));
            single.addColumn("right_id6_6_0_", Collections.singletonList(3L));
            single.addColumn("rightSiz3_6_0_", Collections.singletonList(50));
            single.addColumn("root4_6_0_", Collections.singletonList(true));
            handler.prepareResultSet("select node0_.id as id1_6_0_, node0_.left_id as left_id5_6_0_, node0_.leftSize as leftSize2_6_0_, node0_.right_id as right_id6_6_0_, node0_.rightSize as rightSiz3_6_0_, node0_.root as root4_6_0_ from Node node0_ where node0_.id=\\?", single);

            EvaluableResultSet singleAlt = factory.create("single");
            singleAlt.addColumn("id1_7_0_", Collections.singletonList((EvaluableResultSet.Evaluable) BenchmarkBase::getFirstParam));
            singleAlt.addColumn("left_id5_7_0_", Collections.singletonList(2L));
            singleAlt.addColumn("leftSize2_7_0_", Collections.singletonList(49));
            singleAlt.addColumn("right_id6_7_0_", Collections.singletonList(3L));
            singleAlt.addColumn("rightSiz3_7_0_", Collections.singletonList(50));
            singleAlt.addColumn("root4_7_0_", Collections.singletonList(true));
            handler.prepareResultSet("select node0_.id as id1_7_0_, node0_.left_id as left_id5_7_0_, node0_.leftSize as leftSize2_7_0_, node0_.right_id as right_id6_7_0_, node0_.rightSize as rightSiz3_7_0_, node0_.root as root4_7_0_ from Node node0_ where node0_.id=\\?", singleAlt);

            EvaluableResultSet nodes = factory.create("nodes");
            nodes.addColumn("id1_7_", list(10, BenchmarkBase::addIdFromRange));
            nodes.addColumn("left_id5_7_", list(10, 1L));
            nodes.addColumn("leftSize2_7_", list(10, 0));
            nodes.addColumn("right_id6_7_", list(10, 2L));
            nodes.addColumn("rightSiz3_7_", list(10, 0));
            nodes.addColumn("root4_7_", list(10, true));
            handler.prepareResultSet("select node0_.id as id1_7_, node0_.left_id as left_id5_7_, node0_.leftSize as leftSize2_7_, node0_.right_id as right_id6_7_, node0_.rightSize as rightSiz3_7_, node0_.root as root4_7_ from Node node0_ where node0_.id in \\(.*\\)", nodes);

            MockResultSet roots = handler.createResultSet();
            roots.addColumn("id1_7_", seq(1, 10));
            roots.addColumn("left_id5_7_", seq(11, 20));
            roots.addColumn("leftSize2_7_", list(10, 1));
            roots.addColumn("right_id6_7_", seq(21, 30));
            roots.addColumn("rightSiz3_7_", list(10, 2));
            roots.addColumn("root4_7_", list(10, true));
            handler.prepareResultSet("select node0_.id as id1_7_, node0_.left_id as left_id5_7_, node0_.leftSize as leftSize2_7_, node0_.right_id as right_id6_7_, node0_.rightSize as rightSiz3_7_, node0_.root as root4_7_ from Node node0_ where \\(node0_.id not in  \\(select node1_.left_id from Node node1_ cross join Node node2_ where node1_.left_id=node2_.id and \\(node1_.left_id is not null\\)\\)\\) and \\(node0_.id not in  \\(select node3_.right_id from Node node3_ cross join Node node4_ where node3_.right_id=node4_.id and \\(node3_.right_id is not null\\)\\)\\) and node0_.leftSize=.*", roots);
        }

        @Override
        public Class<Node> getClazz() {
            return Node.class;
        }

        @Override
        protected boolean hasForeignKeys() {
            return true;
        }

        @Override
        protected boolean checkRootEntity(Node entity) {
            return entity.isRoot();
        }

        @Override
        protected Predicate getRootLevelCondition(CriteriaBuilder criteriaBuilder, Root<Node> root) {
            return criteriaBuilder.equal(root.get("root"), Boolean.TRUE);
        }

        @Override
        public Node randomEntity(ThreadLocalRandom random) {
            return randomEntity(random, true, treeSize);
        }

        private Node randomEntity(ThreadLocalRandom random, boolean isRoot, int branchSize) {
            if (branchSize == 0) {
                return null;
            }
            Node root = new Node(isRoot);
            if (branchSize == 1) {
                return root;
            }
            int leftBranchSize = random.nextInt(branchSize - 1);
            int rightBranchSize = branchSize - 1 - leftBranchSize;
            root.setLeft(randomEntity(random, false, leftBranchSize));
            root.setLeftSize(leftBranchSize);
            root.setRight(randomEntity(random, false, rightBranchSize));
            root.setRightSize(rightBranchSize);
            return root;
        }

        @Override
        public void modify(Node node, ThreadLocalRandom random) {
            node.setLeft(randomEntity(random, false, node.getLeftSize()));
            node.setRight(randomEntity(random, false, node.getRightSize()));
        }
    }

    @Benchmark
    public void testCreate(NodeBenchmarkState benchmarkState, ThreadState threadState) throws Exception {
        super.testCreate(benchmarkState, threadState);
    }

    @Benchmark
    public void testRead(NodeBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testRead(benchmarkState, threadState, blackhole);
    }

    @Benchmark
    public void testCriteriaRead(NodeBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testCriteriaRead(benchmarkState, threadState, blackhole);
    }

    @Benchmark
    public void testUpdate(NodeBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        super.testUpdate(benchmarkState, threadState, threadParams);
    }

    @Benchmark
    public void testCriteriaUpdate(NodeBenchmarkState benchmarkState, ThreadState threadState, ThreadParams threadParams) throws Exception {
        super.testCriteriaUpdate(benchmarkState, threadState, threadParams);
    }

    @Benchmark
    public void testDelete(NodeBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testDelete(benchmarkState, threadState);
    }

    @Benchmark
    public void testQueryRoot(final NodeBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        final int randomLeftSize = threadState.random.nextInt(benchmarkState.treeSize - 1);
        super.testQuery(benchmarkState, blackhole, (entityManager, cb) -> {
            CriteriaQuery<Node> query = cb.createQuery(Node.class);
            Root<Node> root = query.from(Node.class);

            Subquery<Node> subqueryLeft = query.subquery(Node.class);
            Path<Node> leftPath = subqueryLeft.from(Node.class).get(benchmarkState.left);
            subqueryLeft.select(leftPath).where(leftPath.isNotNull());

            Subquery<Node> subqueryRight = query.subquery(Node.class);
            Path<Node> rightPath = subqueryRight.from(Node.class).get(benchmarkState.right);
            subqueryRight.select(rightPath).where(rightPath.isNotNull());

            Predicate condition = cb.and(
                  cb.not(root.in(subqueryLeft.getSelection())),
                  cb.not(root.in(subqueryRight.getSelection())),
                  cb.equal(root.get(benchmarkState.leftSize), randomLeftSize));
            query.select(root).where(condition);
            return entityManager.createQuery(query).getResultList();
        });
    }
}
