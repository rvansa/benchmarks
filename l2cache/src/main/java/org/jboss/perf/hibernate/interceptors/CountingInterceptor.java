package org.jboss.perf.hibernate.interceptors;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.infinispan.commands.VisitableCommand;
import org.infinispan.commands.read.GetKeyValueCommand;
import org.infinispan.commands.tx.CommitCommand;
import org.infinispan.commands.tx.PrepareCommand;
import org.infinispan.commands.tx.RollbackCommand;
import org.infinispan.commands.write.InvalidateCommand;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.commands.write.RemoveCommand;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.interceptors.base.BaseCustomInterceptor;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CountingInterceptor extends BaseCustomInterceptor {
    //counters
    private final Stats puts = new Stats();
    private final Stats removes = new Stats();
    private final Stats gets = new Stats();
    private final Stats invalidations = new Stats();
    private final Stats prepares = new Stats();
    private final Stats commits = new Stats();
    private final Stats rollbacks = new Stats();

    public void CountingIntercept() {
    }

    private static class Stats {
        static AtomicLongFieldUpdater<Stats> hitsUpdater = AtomicLongFieldUpdater.newUpdater(Stats.class, "hits");
        static AtomicLongFieldUpdater<Stats> hitsDurationUpdater = AtomicLongFieldUpdater.newUpdater(Stats.class, "hitsDuration");
        static AtomicLongFieldUpdater<Stats> missesUpdater = AtomicLongFieldUpdater.newUpdater(Stats.class, "misses");
        static AtomicLongFieldUpdater<Stats> missesDurationUpdater = AtomicLongFieldUpdater.newUpdater(Stats.class, "missesDuration");

        volatile long hits;
        volatile long hitsDuration;
        volatile long misses;
        volatile long missesDuration;

        public void hit(long nanos) {
            hitsUpdater.incrementAndGet(this);
            hitsDurationUpdater.addAndGet(this, nanos);
        }

        public void miss(long nanos) {
            missesUpdater.incrementAndGet(this);
            missesDurationUpdater.addAndGet(this, nanos);
        }

        public void reset() {
            hits = 0;
            hitsDuration = 0;
            misses = 0;
            missesDuration = 0;
        }

        public long total() {
            return hits + misses;
        }

        @Override
        public String toString() {
            return String.format("hits=%12d\thitsDuration=%12d\tmisses=%12d\tmissesDuration=%12d",
                  hits, TimeUnit.NANOSECONDS.toMillis(hitsDuration), misses, TimeUnit.NANOSECONDS.toMillis(missesDuration));
        }
    }

    @Override
    protected void start() {
        puts.reset();
        removes.reset();
        gets.reset();
        prepares.reset();
        commits.reset();
        rollbacks.reset();
    }

    @Override
    protected void stop() {
        System.err.println("\n\n----------------------------");
        System.err.printf("Cache: %s, mode: %s\n", cache.getName(), cacheConfiguration.transaction().transactionMode());
        if (puts.total() + removes.total() + gets.total() + invalidations.total() + prepares.total() + rollbacks.total() + commits.total() > 0) {
            System.err.printf("Puts:          %s\nRemoves:       %s\nGets:          %s\nInvalidations: %s\n" +
                        "Prepares:      %s\nCommits:       %s\nRollbacks:     %s\n",
                  puts, removes, gets, invalidations, prepares, commits, rollbacks);
        }
        System.err.println("----------------------------");
    }

    private Object invokeNextMeasured(InvocationContext ctx, VisitableCommand command, Stats stats) throws Throwable {
        long start = System.nanoTime();
        Object value = invokeNextInterceptor(ctx, command);
        long end = System.nanoTime();
        if (value != null) stats.hit(end - start);
        else stats.miss(end - start);
        return value;
    }

    @Override
    public Object visitPutKeyValueCommand(InvocationContext ctx, PutKeyValueCommand command) throws Throwable {
        return invokeNextMeasured(ctx, command, puts);
    }

    @Override
    public Object visitInvalidateCommand(InvocationContext ctx, InvalidateCommand command) throws Throwable {
        return invokeNextMeasured(ctx, command, invalidations);
    }

    @Override
    public Object visitRemoveCommand(InvocationContext ctx, RemoveCommand command) throws Throwable {
        return invokeNextMeasured(ctx, command, removes);
    }

    @Override
    public Object visitGetKeyValueCommand(InvocationContext ctx, GetKeyValueCommand command) throws Throwable {
        return invokeNextMeasured(ctx, command, gets);
    }

    @Override
    public Object visitPrepareCommand(TxInvocationContext ctx, PrepareCommand command) throws Throwable {
        return invokeNextMeasured(ctx, command, prepares);
    }

    @Override
    public Object visitCommitCommand(TxInvocationContext ctx, CommitCommand command) throws Throwable {
        return invokeNextMeasured(ctx, command, commits);
    }

    @Override
    public Object visitRollbackCommand(TxInvocationContext ctx, RollbackCommand command) throws Throwable {
        return invokeNextMeasured(ctx, command, rollbacks);
    }

}
