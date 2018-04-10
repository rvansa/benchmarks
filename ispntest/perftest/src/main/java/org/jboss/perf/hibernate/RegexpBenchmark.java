package org.jboss.perf.hibernate;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class RegexpBenchmark {
   @State(value = Scope.Benchmark)
   public static class RState {

      @Param(value = {"1", "10", "100", "1000"})
      public int length;

      public String matched;
      public String nonMatched;
      public String testString;
      public Pattern matchingPattern;
      public Pattern nonMatchingPattern;

      @Setup
      public void setup() {
         StringBuilder sb = new StringBuilder(length);
         ThreadLocalRandom rand = ThreadLocalRandom.current();
         for (int i = 0; i < length; ++i) {
            sb.append((char) rand.nextInt('A', 'Z' + 1));
         }
         testString = sb.toString();
         matched = new String(testString.toCharArray());
         sb.setCharAt(length / 2, (char) (testString.charAt(length / 2) + 1));

         matchingPattern = Pattern.compile(matched);
         nonMatched = sb.toString();
         nonMatchingPattern = Pattern.compile(nonMatched);
      }
   }

   @Benchmark
   public boolean matchingRegexp(RState state) {
      return state.matchingPattern.matcher(state.testString).matches();
   }

   @Benchmark
   public boolean matchingEquals(RState state) {
      return state.testString.equals(state.matched);
   }

   @Benchmark
   public boolean nonMatchingRegexp(RState state) {
      return state.nonMatchingPattern.matcher(state.testString).matches();
   }

   @Benchmark
   public boolean nonMatchingEquals(RState state) {
      return state.testString.equals(state.nonMatched);
   }
}
