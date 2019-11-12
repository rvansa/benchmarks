package org.jboss;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jctools.queues.MpmcArrayQueue;

public class BusyMpmcArrayQueue<T> extends MpmcArrayQueue<T> implements BlockingQueue<T> {

  public BusyMpmcArrayQueue(int capacity) {
    super(capacity);
  }

  @Override
  public void put(T t) throws InterruptedException {
    while (!offer(t)) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      Thread.yield();
    }
  }

  @Override
  public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    while (!offer(t)) {
      long now = System.nanoTime();
      if (now >= deadline) {
        return false;
      }
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      Thread.yield();
    }
    return true;
  }

  @Override
  public T take() throws InterruptedException {
    T element;
    while ((element = poll()) == null) {
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      Thread.yield();
    }
    return element;
  }

  @Override
  public T poll(long timeout, TimeUnit unit) throws InterruptedException {
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    T element;
    while ((element = poll()) == null) {
      long now = System.nanoTime();
      if (now >= deadline) {
        return null;
      }
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      Thread.yield();
    }
    return element;
  }

  @Override
  public int remainingCapacity() {
    return capacity() - size();
  }

  @Override
  public int drainTo(Collection<? super T> c) {
    T element;
    int transferred = 0;
    while ((element = poll()) != null) {
      c.add(element);
      ++transferred;
    }
    return transferred;
  }

  @Override
  public int drainTo(Collection<? super T> c, int maxElements) {
    T element;
    int transferred = 0;
    while (transferred < maxElements && (element = poll()) != null) {
      c.add(element);
      ++transferred;
    }
    return transferred;
  }
}
