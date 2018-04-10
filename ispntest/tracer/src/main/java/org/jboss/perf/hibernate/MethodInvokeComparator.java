package org.jboss.perf.hibernate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class MethodInvokeComparator {
   public static void main(String[] args) {
      Map<String, double[]> normalized = new HashMap<>();
      String normalizeWith = args[0];
      for (int i = 1; i < args.length; ++i) {
         try {
            Map<String, Integer> invocations = new HashMap<>();
            try (FileReader fileReader = new FileReader(args[i]); BufferedReader reader = new BufferedReader(fileReader)) {
               String line;
               while ((line = reader.readLine()) != null) {
                  int splitIndex = line.indexOf('x');
                  int count = Integer.parseInt(line.substring(0, splitIndex).trim());
                  String name = line.substring(splitIndex + 1).trim();
                  invocations.put(name, count);
               }
            }
            Integer normFactor = invocations.get(normalizeWith);
            if (normFactor == null) {
               continue;
            }

            for (Map.Entry<String, Integer> entry : invocations.entrySet()) {
               double[] values = normalized.get(entry.getKey());
               if (values == null) {
                  values = new double[args.length - 1];
                  normalized.put(entry.getKey(), values);
               }
               double nValue = (double) (int) entry.getValue() / (double) (int) normFactor;
               values[i - 1] = nValue;
            }
         } catch (IOException e) {
            e.printStackTrace();  // TODO: Customise this generated block
         }
      }
      TreeMap<Double, String> ratios = new TreeMap<>();
      for (Map.Entry<String, double[]> entry : normalized.entrySet()) {
         double min = Double.MAX_VALUE, max = 0;
         for (double d : entry.getValue()) {
            if (d > max) max = d;
            if (d != 0 && d < min) min = d;
            if (min < max) {
               ratios.put(min / max, entry.getKey());
            }
         }
      }
      for (Map.Entry<Double, String> entry : ratios.entrySet()) {
         double[] relativeValues = normalized.get(entry.getValue());
         for (double d : relativeValues) {
            System.out.printf("%3.3f\t", d);
         }
         System.out.println(entry.getValue());
      }
   }
}
