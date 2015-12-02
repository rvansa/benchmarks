package org.jgroups.protocols;

import org.jgroups.Address;
import org.jgroups.Message;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 */
public class TransferQueueBundler extends AbstractTransferQueueBundler {
   /** Keys are destinations, values are lists of Messages */
   final Map<Address,List<Message>> msgs=new HashMap<>(24);

   public TransferQueueBundler(BlockingQueue<Message> queue) {
      super(queue);
   }

   @Override
   protected void addMessage(Message msg, long size, MessageSink blackhole) {
      List<Message> tmp=msgs.get(msg.getDest());
      if(tmp == null) {
         tmp=new LinkedList<>();
         msgs.put(msg.getDest(), tmp);
      }
      tmp.add(msg);
      count+=size;
   }

   /**
    * Sends all messages in the map. Messages for the same destination are bundled into a message list. The map will
    * be cleared when done
    * @param blackhole
    */
   @Override
   protected void sendBundledMessages(MessageSink blackhole) {
      for(Map.Entry<Address,List<Message>> entry: msgs.entrySet()) {
         List<Message> list=entry.getValue();
         if(list.isEmpty())
            continue;

         output.position(0);
         if(list.size() == 1)
            sendSingleMessage(list.get(0), false, output, blackhole);
         else {
            Address dst=entry.getKey();
            sendMessageList(dst, list.get(0).getSrc(), list, false, output, blackhole);
         }
      }
      msgs.clear();
      count=0;
   }


}
