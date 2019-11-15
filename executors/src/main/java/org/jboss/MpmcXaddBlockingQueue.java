package org.jboss;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;

import org.jctools.queues.MpmcUnboundedXaddArrayQueue;

public class MpmcXaddBlockingQueue<T> extends AbstractQueue<T> implements BlockingQueue<T> {

   private final MpmcUnboundedXaddArrayQueue<T> queue;
   private final AtomicReferenceArray<Thread> waitingConsumers;

   public MpmcXaddBlockingQueue(int chunkSize, int consumers) {
      queue = new MpmcUnboundedXaddArrayQueue<>(chunkSize);
      waitingConsumers = new AtomicReferenceArray<>(consumers);
   }

   public MpmcXaddBlockingQueue(int chunkSize, int maxPooledChunks, int consumers) {
      queue = new MpmcUnboundedXaddArrayQueue<>(chunkSize, maxPooledChunks);
      waitingConsumers = new AtomicReferenceArray<>(consumers);
   }

   private void addWaitingConsumer() throws InterruptedException {
      Thread currentThread = Thread.currentThread();
      for (;;) {
         for (int i = 0; i < waitingConsumers.length(); ++i) {
            if (waitingConsumers.get(i) == currentThread) {
               return;
            }
         }
         for (int i = 0; i < waitingConsumers.length(); ++i) {
            if (waitingConsumers.get(i) == null && waitingConsumers.compareAndSet(i, null, currentThread)) {
               return;
            }
         }
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
         Thread.yield();
      }
   }

   private void wakeupConsumer() {
      for (int i = 0; i < waitingConsumers.length(); ++i) {
         Thread thread = waitingConsumers.get(i);
         if (thread != null && (thread = waitingConsumers.getAndSet(i, null)) != null) {
            LockSupport.unpark(thread);
            return;
         }
      }
   }

   @Override
   public Iterator<T> iterator() {
      throw new UnsupportedOperationException();
   }

   @Override
   public int size() {
      return queue.size();
   }

   @Override
   public void put(T t) throws InterruptedException {
      if (queue.offer(t)) {
         wakeupConsumer();
      } else {
         throw new IllegalStateException("Queue is unbounded");
      }
   }

   @Override
   public boolean offer(T t) {
      if (queue.offer(t)) {
         wakeupConsumer();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
      if (queue.offer(t)) {
         wakeupConsumer();
         return true;
      }
      return false;
   }

   @Override
   public T take() throws InterruptedException {
      // Reading from empty LinkedBlockingQueue is cheap (volatile read)
      T element;
      while ((element = queue.poll()) == null) {
         addWaitingConsumer();
         // Producer might put element to the queue and try to wake up consumers before
         // the consumer gets registered; that's why have to poll once more.
         element = queue.poll();
         if (element != null) {
            return element;
         }
         LockSupport.park();
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
      }
      return element;
   }

   @Override
   public T poll() {
      return queue.poll();
   }

   @Override
   public T peek() {
      return queue.peek();
   }

   @Override
   public T poll(long timeout, TimeUnit unit) throws InterruptedException {
      T element;
      long deadline = System.nanoTime() + unit.toNanos(timeout);
      while ((element = queue.poll()) == null) {
         long now = System.nanoTime();
         if (now >= deadline) {
            return null;
         }
         addWaitingConsumer();
         // Producer might put element to the queue and try to wake up consumers before
         // the consumer gets registered; that's why have to poll once more.
         element = queue.poll();
         if (element != null) {
            return element;
         }
         LockSupport.parkNanos(deadline - now);
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
      }
      return element;
   }

   @Override
   public int remainingCapacity() {
      return Integer.MAX_VALUE;
   }

   @Override
   public int drainTo(Collection<? super T> c) {
      T element;
      int transferred = 0;
      while ((element = queue.poll()) != null) {
         c.add(element);
         ++transferred;
      }
      return transferred;
   }

   @Override
   public int drainTo(Collection<? super T> c, int maxElements) {
      T element;
      int transferred = 0;
      while (transferred < maxElements && (element = queue.poll()) != null) {
         c.add(element);
         ++transferred;
      }
      return transferred;
   }
}
