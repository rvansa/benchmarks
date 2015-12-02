package org.jboss.perf.hibernate;

import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.protocols.Bundler;
import org.jgroups.protocols.MessageSink;
import org.jgroups.protocols.TransferQueueBundler;
import org.jgroups.protocols.SimplifiedTransferQueueBundler;
import org.jgroups.stack.IpAddress;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BundlerBenchmark {
   @org.openjdk.jmh.annotations.State(Scope.Benchmark)
   public static class State {
      @Param({"original", "my"})
      String bundlerType;

      @Param({"linked", "array"})
      String queueType;

      @Param("4")
      int numDests;

      @Param("100")
      int numPayloads;

      @Param("512")
      public int payloadLength;

      Bundler bundler;
      Address[] addresses;
      byte[][] payloads;

      @Setup
      public void setup() throws UnknownHostException {
         BlockingQueue<Message> queue = queueType.equals("array") ? new ArrayBlockingQueue<>(20000) : new LinkedBlockingDeque<>(20000);
         bundler = bundlerType.equals("my") ? new SimplifiedTransferQueueBundler(queue) : new TransferQueueBundler(queue);
         bundler.start();

         ThreadLocalRandom random = ThreadLocalRandom.current();
         addresses = new Address[numDests];
         for (int i = 0; i < numDests; ++i) {
            byte[] addr = new byte[4];
            random.nextBytes(addr);
            addresses[i] = new IpAddress(InetAddress.getByAddress(null, addr), random.nextInt());
         }

         payloads = new byte[numPayloads][payloadLength];
         for (int i = 0; i < numPayloads; ++i) {
            random.nextBytes(payloads[i]);
         }
      }
   }

   @Group("pc")
   @GroupThreads(5)
   @Benchmark
   public void producer(State state) throws Exception {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      state.bundler.send(new Message(
            state.addresses[random.nextInt(state.addresses.length)],
            state.payloads[random.nextInt(state.payloads.length)]));
   }

   @Group("pc")
   @GroupThreads(1)
   @Benchmark
   public void consumer(State state, Blackhole blackhole) {
      state.bundler.run(new MessageSink.Impl(blackhole));
   }
}
