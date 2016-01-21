package org.jboss.perf.model;

/**
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
public class TwoStrings {
   private final String first;
   private String second;

   public TwoStrings(String string) {
      int sep = string.indexOf('_');
      first = string.substring(0, sep);
      second = string.substring(sep + 1);
   }
}
