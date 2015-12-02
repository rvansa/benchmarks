package org.jgroups.protocols;

import org.jgroups.Address;
import org.jgroups.Message;

import java.util.concurrent.BlockingQueue;

/**
 */
public class SimplifiedTransferQueueBundler extends AbstractTransferQueueBundler {
   private final int MSG_BUF_SIZE = 512;
   private Message[] msgs = new Message[MSG_BUF_SIZE];
   private int curr;

   public SimplifiedTransferQueueBundler(BlockingQueue<Message> queue) {
      super(queue);
   }

   @Override
   protected void addMessage(Message msg, long size, MessageSink blackhole) {
      try {
         while (curr < MSG_BUF_SIZE && msgs[curr] != null) ++curr;
         if (curr < MSG_BUF_SIZE) {
            msgs[curr] = msg;
            ++curr;
         } else {
            sendBundledMessages(blackhole);
            curr = 0;
            msgs[0] = msg;
         }
      } finally {
         count += size;
      }
   }

   @Override
   protected void sendBundledMessages(MessageSink blackhole) {
      int start = 0;
      for (;;) {
         for ( ; start < MSG_BUF_SIZE && msgs[start] == null; ++start);
         if (start >= MSG_BUF_SIZE) {
            count = 0;
            return;
         }
         Address dest = msgs[start].getDest();
         int numMsgs = 1;
         for (int i = start + 1; i < MSG_BUF_SIZE; ++i) {
            Message msg = msgs[i];
            if (msg != null && (dest == msg.getDest() || (dest != null && dest.equals(msg.getDest())))) {
               msg.setDest(dest); // avoid further equals() calls
               numMsgs++;
            }
         }
         try {
            output.position(0);
            if (numMsgs == 1) {
               sendSingleMessage(msgs[start], false, output, blackhole);
               msgs[start] = null;
            } else {
               writeMessageListPrologue(dest, msgs[start].getSrc(), null, numMsgs, output, dest == null);
               for (int i = start; i < MSG_BUF_SIZE; ++i) {
                  Message msg = msgs[i];
                  if (msg != null && msg.getDest() == dest) {
                     msg.writeToNoAddrs(msg.getSrc(), output, (short) 0);
                     msgs[i] = null;
                  }
               }
               doSend(output.buffer(), 0, output.position(), dest, blackhole);
            }
            start++;
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
   }
}
