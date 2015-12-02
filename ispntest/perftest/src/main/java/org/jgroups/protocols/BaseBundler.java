package org.jgroups.protocols;

import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.Version;
import org.jgroups.util.ByteArrayDataOutputStream;
import org.jgroups.util.Util;

import java.io.DataOutput;
import java.util.List;

/**
 */
public abstract class BaseBundler implements Bundler {
   final ByteArrayDataOutputStream output=new ByteArrayDataOutputStream(1024);
   long count;    // current number of bytes accumulated

   protected int max_bundle_size = 65000;

   protected static final byte    LIST=1; // we have a list of messages rather than a single message when set
   protected static final byte    MULTICAST=2; // message is a multicast (versus a unicast) message when set

   protected static void writeMessage(Message msg, DataOutput dos, boolean multicast) throws Exception {
      byte flags=0;
      dos.writeShort(Version.version); // write the version
      if(multicast)
         flags += MULTICAST;
      dos.writeByte(flags);
      msg.writeTo(dos);
   }

   public static void doSend(byte[] buffer, int offset, int length, Address dest, MessageSink blackhole) {
      blackhole.consume(buffer, offset, length, dest);
   }

   public static void writeMessageList(Address dest, Address src, byte[] cluster_name,
                                       List<Message> msgs, DataOutput dos, boolean multicast, short transport_id) throws Exception {
      writeMessageListPrologue(dest, src, cluster_name, msgs != null ? msgs.size() : 0, dos, multicast);

      if(msgs != null)
         for(Message msg: msgs)
            msg.writeToNoAddrs(src, dos, transport_id); // exclude the transport header
   }

   public static void writeMessageListPrologue(Address dest, Address src, byte[] cluster_name, int msgCount, DataOutput dos, boolean multicast) throws Exception {
      dos.writeShort(Version.version);

      byte flags=LIST;
      if(multicast)
         flags+=MULTICAST;

      dos.writeByte(flags);

      Util.writeAddress(dest, dos);

      Util.writeAddress(src, dos);

      dos.writeShort(cluster_name != null? cluster_name.length : -1);
      if(cluster_name != null)
         dos.write(cluster_name);

      // Number of messages (0 == no messages)
      dos.writeInt(msgCount);
   }

   public void start() {}
   public void stop()  {}
   public abstract boolean send(Message msg) throws Exception;

   protected void sendSingleMessage(final Message msg, boolean reset, final ByteArrayDataOutputStream out, MessageSink blackhole) {
      Address dest=msg.getDest();

      try {
         if(reset)
            out.position(0);
         writeMessage(msg, out, dest == null);
         doSend(out.buffer(), 0, out.position(), dest, blackhole);
      }
      catch(Throwable e) {
      }
   }

   protected void sendMessageList(final Address dest, final Address src,
                                  final List<Message> list, boolean reset, final ByteArrayDataOutputStream out, MessageSink blackhole) {
      try {
         if(reset)
            out.position(0);
         writeMessageList(dest, src, null, list, out, dest == null, (short) 0); // flushes output stream when done
         doSend(out.buffer(), 0, out.position(), dest, blackhole);
      }
      catch(Throwable e) {
      }
   }
}
