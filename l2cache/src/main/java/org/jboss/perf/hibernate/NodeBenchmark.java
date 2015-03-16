package org.jboss.perf.hibernate;

import java.util.concurrent.ThreadLocalRandom;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.jboss.perf.hibernate.model.Node;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.infra.ThreadParams;

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
            if (node.getLeftSize() <= maxModifiedSize) {
                node.setLeft(randomEntity(random, false, node.getLeftSize()));
            } else if (node.getRightSize() <= maxModifiedSize) {
                node.setRight(randomEntity(random, false, node.getRightSize()));
            } else {
                modify(node.getLeftSize() < node.getRightSize() ? node.getLeft() : node.getRight(), random);
            }
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
}
