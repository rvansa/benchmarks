package org.jboss.perf.hibernate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class MethodInvokeTracer {
   static final int MAX_TRACED_METHODS = 128 * 1024;
   static final ThreadLocal<ThreadRecord> threadInvocations = new ThreadLocal<ThreadRecord>() {
      @Override
      protected ThreadRecord initialValue() {
         ThreadRecord record = new ThreadRecord();
         synchronized (registeredInvocations) {
            registeredInvocations.add(record);
         }
         return record;
      }
   };
   static final ArrayList<ThreadRecord> registeredInvocations = new ArrayList<>();

   public static void enable(boolean enable) {
      ThreadRecord record = threadInvocations.get();
      record.enabled = enable;
   }

   public static void invoked(int index) {
      ThreadRecord record = threadInvocations.get();
      record.invocations[index]++;
   }

   /* Read the class names and methods from the rule file, instead of storing it */
   public static void printInvocations() {
      final String ruleFile = System.getProperty("method.invoke.rules");
      ArrayList<Invocation> parsedInvocations = new ArrayList<>();
      try (FileReader fileReader = new FileReader(ruleFile); BufferedReader reader = new BufferedReader(fileReader)) {
         String line;
         while ((line = reader.readLine()) != null) {
            if (line.startsWith("RULE")) {
               int ruleNumber = Integer.parseInt(line.substring(6));
               long methodInvocations = 0;
               for (ThreadRecord record : registeredInvocations) {
                  if (record.enabled) {
                     methodInvocations += record.invocations[ruleNumber];
                  }
               }
               if (methodInvocations > 0) {
                  String className = reader.readLine().substring(6); // CLASS xxx
                  String methodName = reader.readLine().substring(7); // METHOD mmm
                  parsedInvocations.add(new Invocation(className + "." + methodName, methodInvocations));
               }
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
      Collections.sort(parsedInvocations);
      System.err.println("---------------------------------------");
      for (Invocation invocation : parsedInvocations) {
         System.err.printf("%9dx\t%s\n", invocation.count, invocation.name);
      }
      System.err.println("---------------------------------------");
   }

   public static void reset() {
      System.err.println("Resetting stats");
//      for (int i = 0; i < MAX_TRACED_METHODS; ++i) {
//         invocations.set(i, 0);
//      }
      synchronized (registeredInvocations) {
         for (ThreadRecord record : registeredInvocations) {
            Arrays.fill(record.invocations, 0);
         }
      }
   }

   private static class ThreadRecord {
      long[] invocations = new long[MAX_TRACED_METHODS];
      boolean enabled = false;
   }

   private static class Invocation implements Comparable<Invocation> {
      public final String name;
      public final long count;

      public Invocation(String name, long count) {
         this.name = name;
         this.count = count;
      }

      @Override
      public int compareTo(Invocation o) {
         return Long.compare(o.count, this.count);
      }
   }
}
