package org.jboss.perf.hibernate;

import java.lang.reflect.Constructor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import sun.misc.Unsafe;

public class ProducerConsumerBenchmark {
   private static final Object DUMMY = new Object();

   @State(Scope.Benchmark)
   public static class S {

      @Param({ "sync", "array", "ara", "unsafe", "arasc", "arascmp"})
      String type;

      Queue queue;

      @Setup
      public void init() {
         switch (type) {
            case "sync":
               queue = new SyncQueue();
               break;
            case "array":
               queue = new ArrayQueue();
               break;
            case "ara":
               queue = new ARAQueue();
               break;
            case "unsafe":
               queue = new UnsafeQueue();
               break;
            case "arasc":
               queue = new ARASCQueue();
               break;
            case "arascmp":
               queue = new ARASCMPQueue();
               break;
            default:
               throw new IllegalStateException();
         }
      }
   }


   @Benchmark
   @Group("g")
   @GroupThreads(1)
   public void produce(S state) throws InterruptedException {
      state.queue.put(DUMMY);
   }

   @Benchmark
   @Group("g")
   @GroupThreads(1)
   public Object consume(S state) throws InterruptedException {
      return state.queue.take();
   }

   public interface Queue<T> {
      T take() throws InterruptedException;
      void put(T t) throws InterruptedException;
   }

   public static class SyncQueue<T> implements Queue<T> {
      private final BlockingQueue<T> q = new SynchronousQueue<>();

      @Override
      public T take() throws InterruptedException {
         return q.take();
      }

      @Override
      public void put(T t) throws InterruptedException {
         q.put(t);
      }
   }

   public static class ArrayQueue<T> implements Queue<T> {
      private final BlockingQueue<T> q = new ArrayBlockingQueue<T>(1024);

      @Override
      public T take() throws InterruptedException {
         return q.take();
      }

      @Override
      public void put(T t) throws InterruptedException {
         q.put(t);
      }
   }

   public static class ARAQueue<T> implements Queue<T> {
      private static final int LENGTH = 1024;
      private static final int SHIFT = 5;
      private final AtomicReferenceArray<T> a = new AtomicReferenceArray<>(LENGTH);

      @Override
      public T take() throws InterruptedException {
         ThreadLocalRandom rand = ThreadLocalRandom.current();
         for (int cycle = 0;; ++cycle) {
            int offset = rand.nextInt(LENGTH >> SHIFT) << SHIFT;
            int end = offset + (1 << SHIFT);
            for ( ; offset < end; ++offset) {
               T t = a.get(offset);
               if (t != null) {
                  if (a.weakCompareAndSet(offset, t, null)) {
                     return t;
                  }
               }
            }
            if (cycle > 100) {
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
               cycle = 0;
            }
         }
      }

      @Override
      public void put(T t) throws InterruptedException {
         ThreadLocalRandom rand = ThreadLocalRandom.current();
         for (int cycle = 0;; ++cycle) {
            int offset = rand.nextInt(LENGTH >> SHIFT) << SHIFT;
            int end = offset + (1 << SHIFT);
            for ( ; offset < end; ++offset) {
               if (a.get(offset) == null) {
                  if (a.weakCompareAndSet(offset, null, t)) {
                     return;
                  }
               }
            }
            if (cycle > 100) {
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
               cycle = 0;
            }
         }
      }
   }

   public static class UnsafeQueue<T> implements Queue<T> {
      private static final int LENGTH = 1024;
      private static final int SHIFT = 5;
      private final Object[] a = new Object[LENGTH];
      private static final Unsafe unsafe;

      static {
         try {
            Constructor<Unsafe> unsafeConstructor = Unsafe.class.getDeclaredConstructor();
            unsafeConstructor.setAccessible(true);
            unsafe = unsafeConstructor.newInstance();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
      private static final int SCALE = unsafe.arrayIndexScale(Object[].class);
      private static final int SCALE_SHIFT = Integer.numberOfLeadingZeros(SCALE);
      private static final int BASE = unsafe.arrayBaseOffset(Object[].class);


      @Override
      public T take() throws InterruptedException {
         ThreadLocalRandom rand = ThreadLocalRandom.current();
         for (int cycle = 0; ; ++cycle) {
            int offset = (rand.nextInt(LENGTH >> SHIFT) << (SHIFT + SCALE_SHIFT)) + BASE;
            int end = offset + (1 << (SHIFT + SCALE_SHIFT));
            for (; offset < end; offset += SCALE) {
               int pos = (offset << SCALE) + BASE;
               Object t = unsafe.getObjectVolatile(a, pos);
               if (t != null) {
                  if (unsafe.compareAndSwapObject(a, pos, t, null)) {
                     return (T) t;
                  }
               }
            }
            if (cycle > 100) {
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
               cycle = 0;
            }
         }
      }

      @Override
      public void put(T t) throws InterruptedException {
         ThreadLocalRandom rand = ThreadLocalRandom.current();
         for (int cycle = 0; ; ++cycle) {
            int offset = (rand.nextInt(LENGTH >> SHIFT) << (SHIFT + SCALE_SHIFT)) + BASE;
            int end = offset + (1 << (SHIFT + SCALE_SHIFT));
            for (; offset < end; offset += SCALE) {
               int pos = (offset << SCALE) + BASE;
               if (unsafe.getObjectVolatile(a, pos) == null) {
                  if (unsafe.compareAndSwapObject(a, pos, null, t)) {
                     return;
                  }
               }
            }
            if (cycle > 100) {
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
               cycle = 0;
            }
         }
      }
   }

   public static class ARASCQueue<T> implements Queue<T> {
      private static final int LENGTH = 1024;
      private static final int SHIFT = 5;
      private final AtomicReferenceArray<T> a = new AtomicReferenceArray<>(LENGTH);
      private int lastPos;

      @Override
      public T take() throws InterruptedException {
         int offset = lastPos;
         for (;;) {
            T t = a.get(offset);
            if (t != null) {
               if (a.weakCompareAndSet(offset, t, null)) {
                  lastPos = (offset + 1) & (LENGTH - 1);
                  return t;
               }
            }
            offset = (offset + 1) & (LENGTH - 1);
            if (offset == 0 && Thread.interrupted()) {
               throw new InterruptedException();
            }
         }
      }

      @Override
      public void put(T t) throws InterruptedException {
         ThreadLocalRandom rand = ThreadLocalRandom.current();
         for (int cycle = 0;; ++cycle) {
            int offset = rand.nextInt(LENGTH >> SHIFT) << SHIFT;
            int end = offset + (1 << SHIFT);
            for ( ; offset < end; ++offset) {
               if (a.get(offset) == null) {
                  if (a.weakCompareAndSet(offset, null, t)) {
                     return;
                  }
               }
            }
            if (cycle > 100) {
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
               cycle = 0;
            }
         }
      }
   }

   public static class ARASCMPQueue<T> implements Queue<T> {
      private static final int LENGTH = 1024;
      private static final int SHIFT = 5;
      private final AtomicReferenceArray<T> a = new AtomicReferenceArray<>(LENGTH);
      private int lastPos;

      @Override
      public T take() throws InterruptedException {
         int offset = lastPos;
         for (;;) {
            T t = a.get(offset);
            if (t != null) {
               if (a.weakCompareAndSet(offset, t, null)) {
                  lastPos = (offset + 1) & (LENGTH - 1);
                  return t;
               }
            }
            offset = (offset + 1) & (LENGTH - 1);
            if (offset == 0 && Thread.interrupted()) {
               throw new InterruptedException();
            }
         }
      }

      @Override
      public void put(T t) throws InterruptedException {
         ThreadLocalRandom rand = ThreadLocalRandom.current();
         for (int cycle = 0;; ++cycle) {
            int offset = rand.nextInt(LENGTH >> SHIFT) << SHIFT;
            int end = offset + (1 << SHIFT);
            for ( ; offset < end; ++offset) {
               if (a.get(offset) == null) {
                  if (a.weakCompareAndSet(offset, null, t)) {
                     return;
                  }
               }
            }
            if (cycle > 10) {
               if (Thread.interrupted()) {
                  throw new InterruptedException();
               }
               Thread.yield();
               cycle = 0;
            }
         }
      }
   }
}
