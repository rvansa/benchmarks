package org.jboss;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import io.netty.util.concurrent.ImmediateEventExecutor;

public class ExecutorBenchmark {
    static BenchmarkState state;

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        @Param({ "NONE", "LBQ", "LTQ", "BUSY", "MPMC", "XADD" })
        String impl;

        @Param("1000")
        int capacity;

        @Param("20")
        int workers;

        @Param("2000")
        int pwork;

        BlockingQueue<Runnable> queue = null;
        ExecutorService executorService;
        Semaphore semaphore;

        @Setup
        public void setup() {
            state = this;
            semaphore = new Semaphore(capacity);
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
                    queue = new BusyMpmcArrayQueue<>(capacity);
                    break;
                case "MPMC":
                    queue = new MpmcArrayBlockingQueue<>(capacity, workers);
                    break;
                case "XADD":
                    queue = new MpmcXaddBlockingQueue(workers);
                    break;
                default:
                    throw new IllegalStateException();
            }
            if (queue != null) {
                executorService = new ThreadPoolExecutor(workers, workers, 1, TimeUnit.MINUTES, queue, new ThreadPoolExecutor.DiscardPolicy());
            }
        }

        @TearDown
        public void shutdown() {
            executorService.shutdownNow();
        }
    }

    @State(Scope.Thread)
    public static class Task  implements Runnable {
        @Param("2000")
        int cwork;

        @Override
        public void run() {
            Blackhole.consumeCPU(cwork);
            state.semaphore.release();
        }
    }

    @Benchmark
    public void producer(BenchmarkState state, Task task) throws InterruptedException {
        state.semaphore.acquire();
        Blackhole.consumeCPU(state.pwork);
        state.executorService.execute(task);
    }
}
