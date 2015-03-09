package org.jboss.perf.hibernate;

import java.util.concurrent.ThreadLocalRandom;

import org.jboss.perf.hibernate.model.Beagle;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

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
           return null;
        }

        @Override
        public void modify(Beagle beagle, ThreadLocalRandom random) {

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
    public void testUpdate(BeagleBenchmarkState benchmarkState, ThreadState threadState) throws Exception {
        super.testUpdate(benchmarkState, threadState);
    }

    @Benchmark
    public void testCriteriaUpdate(BeagleBenchmarkState benchmarkState, ThreadState threadState) throws Exception {
        super.testCriteriaUpdate(benchmarkState, threadState);
    }

    @Benchmark
    public void testDelete(BeagleBenchmarkState benchmarkState, ThreadState threadState, Blackhole blackhole) throws Exception {
        super.testDelete(benchmarkState, threadState);
    }
}
