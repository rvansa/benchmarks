package org.jgroups.protocols;

import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.util.ByteArrayDataInputStream;
import org.jgroups.util.UUID;
import org.jgroups.util.Util;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BundlerTest {
   protected static final int NUM_MSGS = 10000;
   ExecutorService executorService = Executors.newFixedThreadPool(4);
   Address[] addresses = new Address[] { null, new UUID(0xA, 0), new UUID(0xB, 0)};

   @Test
   public void testTQB() {
      test(new TransferQueueBundler(new LinkedBlockingQueue<>(1000)));
   }

   @Test
   public void testSTQB() {
      test(new SimplifiedTransferQueueBundler(new LinkedBlockingQueue<>(1000)));
   }

   private void test(final Bundler bundler) {
      Future<?> bundlerFuture = executorService.submit(() -> {
         CountingSink sink = new CountingSink();
         while (sink.numMsgs < NUM_MSGS) {
            bundler.run(sink);
            System.out.printf("Processed %d msgs\n", sink.numMsgs);
         }
         assertEquals(NUM_MSGS, sink.numMsgs);
      });
      for (int i = 0; i < 10000; ++i) {
         final int ii = i;
         executorService.submit(() -> {
            Message msg = new Message(addresses[ii % 3], String.valueOf(ii).getBytes() );
            for(;;) {
               if (bundler.send(msg)) {
                  break;
               } else {
                  Thread.yield();
               }
            }
            return null;
         });
      }
      executorService.shutdown();
      try {
         if (!executorService.awaitTermination(1000, TimeUnit.SECONDS)) fail();
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
      try {
         bundlerFuture.get();
      } catch (InterruptedException e) {
         e.printStackTrace();  // TODO: Customise this generated block
      } catch (ExecutionException e) {
         e.printStackTrace();  // TODO: Customise this generated block
      }
      CountingSink sink = new CountingSink();
      bundler.run(sink);
      assertEquals(0, sink.numMsgs);
   }

   static class CountingSink implements MessageSink {
      Set<String> msgs = new HashSet<String>();
      int numMsgs = 0;

      @Override
      public void consume(byte[] data, int offset, int length, Address dest) {
         ByteArrayDataInputStream in = new ByteArrayDataInputStream(data, offset, length);
         try {
            in.readShort();
            byte flags = in.readByte();
            boolean is_message_list=(flags & BaseBundler.LIST) == BaseBundler.LIST;

            if (is_message_list) {// used if message bundling is enabled
               Util.readAddress(in);
               Util.readAddress(in);
               // AsciiString cluster_name=Bits.readAsciiString(in);
               short clusterNameLength = in.readShort();
               byte[] cluster_name = clusterNameLength >= 0 ? new byte[clusterNameLength] : null;
               if (cluster_name != null)
                  in.readFully(cluster_name, 0, cluster_name.length);
               int msgCount = in.readInt();
               numMsgs += msgCount;
               for (int i = 0; i < msgCount; ++i) {
                  Message msg = new Message(false);
                  msg.readFrom(in);
                  checkMsg(msg);
               }
            } else {
               ++numMsgs;
               Message msg = new Message(false);
               msg.readFrom(in);
               checkMsg(msg);
            }
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      public void checkMsg(Message msg) {
         String msgId = new String(msg.getBuffer());
         try {
            int id = Integer.parseInt(msgId);
            if (id < 0 || id >= NUM_MSGS) {
               System.out.println("Unepxected message " + msgId);
            }
         } catch (NumberFormatException e) {
            e.printStackTrace();
         }
         if (!msgs.add(msgId)) {
            System.out.printf("Message %s already received\n", msgId);
         }
      }
   }
}
