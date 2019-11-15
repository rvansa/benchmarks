package org.jboss;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import io.netty.util.concurrent.ImmediateEventExecutor;

public class ExecutorBenchmark {

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        @Param({ "NONE", "LBQ", "LTQ", "BUSY", "MPMC", "XADD" })
        String impl;

        @Param({"-1", "1000"})
        int capacity;

        @Param("20")
        int workers;

        @Param("2000")
        int pwork;

        @Param("p%04d")
        String workerNamePattern; // perf record/script truncates long thread names

        BlockingQueue<Runnable> queue = null;
        ExecutorService executorService;
        Semaphore semaphore;

        @Setup
        public void setup() {
            if (capacity < 0) {
                semaphore = null;
            } else {
                semaphore = new Semaphore(capacity);
            }
            switch (impl.toUpperCase()) {
                case "NONE":
                    queue = null;
                    executorService = ImmediateEventExecutor.INSTANCE;
                    break;
                case "LBQ":
                    queue = new LinkedBlockingQueue<>();
                    break;
                case "LTQ":
                    queue = new LinkedTransferQueue<>();
                    break;
                case "BUSY":
                    queue = new BusyMpmcArrayQueue<>(Math.max(capacity, 1024));
                    break;
                case "MPMC":
                    queue = new MpmcArrayBlockingQueue<>(Math.max(capacity, 1024), workers);
                    break;
                case "XADD":
                    queue = new MpmcXaddBlockingQueue(Math.max(capacity, 1024), workers);
                    break;
                default:
                    throw new IllegalStateException();
            }
            if (queue != null) {
                executorService = new ThreadPoolExecutor(workers, workers, 1, TimeUnit.MINUTES, queue,
                      new NamingThreadFactory(workerNamePattern), new ThreadPoolExecutor.AbortPolicy());
            }
        }

        @TearDown(Level.Iteration)
        public void awaitPendingTasks() {
            if (queue != null) {
                //it would make the bench to pay awaking the consumer threads on each iteration
                //but it would ensure that no backlog of pending tasks would
                //churn the queue during the whole bench
                while (!queue.isEmpty()) {
                    LockSupport.parkNanos(1L);
                }
            }
        }

        @TearDown
        public void shutdown() {
            //there shouldn't be any pending tasks here
            executorService.shutdown();
        }
    }

    @State(Scope.Thread)
    public static class Task  implements Runnable {
        @Param("2000")
        int cwork;
        Semaphore semaphore;

        @Setup
        public void init(BenchmarkState state) {
            semaphore = state.semaphore;
        }

        @Override
        public void run() {
            Blackhole.consumeCPU(cwork);
            final Semaphore semaphore = this.semaphore;
            if (semaphore != null) {
                semaphore.release();
            }
        }
    }

    @Benchmark
    public void producer(BenchmarkState state, Task task) throws InterruptedException {
        final Semaphore semaphore = state.semaphore;
        if (semaphore != null) {
            semaphore.acquire();
        }
        Blackhole.consumeCPU(state.pwork);
        state.executorService.execute(task);
    }

    private static class NamingThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();
        private final String pattern;

        private NamingThreadFactory(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(String.format(pattern, counter.incrementAndGet()));
            return thread;
        }
    }
}
