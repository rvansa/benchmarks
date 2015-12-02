package org.jgroups.protocols;

import org.jgroups.Address;
import org.openjdk.jmh.infra.Blackhole;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface MessageSink {
   void consume(byte[] buffer, int offset, int length, Address dest);

   class Impl implements MessageSink {
      final Blackhole blackhole;

      public Impl(Blackhole blackhole) {
         this.blackhole = blackhole;
      }

      @Override
      public void consume(byte[] buffer, int offset, int length, Address dest) {
         blackhole.consume(buffer);
         blackhole.consume(offset);
         blackhole.consume(length);
         blackhole.consume(dest);
      }
   }
}
