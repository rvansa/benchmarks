package org.jgroups.protocols;

import org.jgroups.Message;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractTransferQueueBundler extends BaseBundler {
   protected final BlockingQueue<Message> queue;

   protected AbstractTransferQueueBundler(BlockingQueue<Message> queue) {
      this.queue = queue;
   }

   public synchronized void stop() {
      queue.clear();
   }

   public boolean send(Message msg) throws Exception {
      return queue.offer(msg);
   }

   protected abstract void addMessage(Message msg, long size, MessageSink blackhole);

   public void run(MessageSink blackhole) {
      Message msg;
      try {
         if(count == 0) {
            // use timeout > AbstractQueuedSynchronizer.spinForTimeoutThreshold
            msg = queue.poll(5000, TimeUnit.NANOSECONDS);
            if(msg == null)
               return;
            long size=msg.size();
            if(count + size >= max_bundle_size)
               sendBundledMessages(blackhole);
            addMessage(msg, size, blackhole);
         }
         while(null != (msg=queue.poll())) {
            long size=msg.size();
            if(count + size >= max_bundle_size)
               sendBundledMessages(blackhole);
            addMessage(msg, size, blackhole);
         }
         if(count > 0) {
            sendBundledMessages(blackhole);
         }
      }
      catch(Throwable t) {
         t.printStackTrace();
      }
   }

   protected abstract void sendBundledMessages(MessageSink blackhole);
}
