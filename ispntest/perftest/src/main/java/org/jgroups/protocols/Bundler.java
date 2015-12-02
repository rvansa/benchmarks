package org.jgroups.protocols;

import org.jgroups.Message;

/**
 */
public interface Bundler {
   void start();
   void stop();
   boolean send(Message msg) throws Exception;
   void run(MessageSink blackhole);
}
