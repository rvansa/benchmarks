package org.jboss.perf;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * This is here also as an example how to retrieve data in order to persist them, e.g. in PerfRepo
 *
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
public class PrintStats {
   public static void main(String[] args) {
      if (args.length != 1) {
         throw new IllegalArgumentException("Expected single argument, got: " + Arrays.toString(args));
      }
      File dir = new File(args[0]);
      if (!dir.isDirectory()) {
         throw new IllegalArgumentException(dir + " is not a directory");
      }
      Map<String, Stats> statsMap = new TreeMap<>();
      for (File subDir : dir.listFiles()) {
         File statsFile = Paths.get(subDir.toString(), "js", "stats.js").toFile();
         if (!statsFile.exists()) {
            System.err.printf("Cannot find file %s%n", statsFile.toString());
         }
         try {
            String allFile = new String(Files.readAllBytes(statsFile.toPath()));
            int start = allFile.indexOf('{');
            int end = start + 1, brackets = 1;
            for ( ;end < allFile.length() && brackets > 0; ++end) {
               switch (allFile.charAt(end)) {
                  case '{':
                     ++brackets;
                     break;
                  case '}':
                     --brackets;
                     break;
               }
            }
            JSONObject object = new JSONObject(allFile.substring(start, end));
            JSONObject contents = object.getJSONObject("contents");
            for (String req : contents.keySet()) {
               JSONObject stats = contents.getJSONObject(req).getJSONObject("stats");
               String name = stats.getString("name");
               Stats data = new Stats(stats);
               statsMap.put(name, data);
            }
         } catch (IOException e) {
            System.err.printf("Cannot read file %s%n", statsFile.toString());
         }

      }
      int maxLenght = statsMap.keySet().stream().map(s -> s.length()).max(Integer::compare).get();
      String indent = indent(maxLenght - 4);
      System.out.println();
      System.out.printf("Test%s Requests Errors   Mean     Std.dev. Min      Max      50th pct 75th pct 95th pct 99th pct%n", indent);
      System.out.printf("    %s -------- -------- -------- -------- -------- -------- -------- -------- -------- --------%n", indent);
      for (Map.Entry<String, Stats> entry : statsMap.entrySet()) {
         Stats s = entry.getValue();
         System.out.printf("%s%s %8d %8d %8d %8d %8d %8d %8d %8d %8d %8d%n", entry.getKey(), indent(maxLenght - entry.getKey().length()),
               s.requests.total, s.requests.ko, s.meanRT.ok, s.stdDev.ok, s.minRT.ok, s.maxRT.ok, s.pct50th.ok, s.pct75th.ok, s.pct95th.ok, s.pct99th.ok);
      }
      System.out.println();
   }

   private static String indent(int chars) {
      StringBuilder sb = new StringBuilder(chars);
      for (int i = 0; i < chars; ++i) sb.append(' ');
      return sb.toString();
   }

   private static class Metrics {
      final int total;
      final int ok;
      final int ko;

      public Metrics(JSONObject object) {
         total = parseOrZero(object.getString("total"));
         ok = parseOrZero(object.getString("ok"));
         ko = parseOrZero(object.getString("ko"));
      }

      private int parseOrZero(String str) {
         try {
            return Integer.parseInt(str);
         } catch (NumberFormatException e) {
            return 0;
         }
      }
   }

   private static class Stats {
      final Metrics requests;
      final Metrics minRT;
      final Metrics maxRT;
      final Metrics meanRT;
      final Metrics stdDev;
      final Metrics pct50th;
      final Metrics pct75th;
      final Metrics pct95th;
      final Metrics pct99th;

      public Stats(JSONObject object) {
         requests = new Metrics(object.getJSONObject("numberOfRequests"));
         minRT = new Metrics(object.getJSONObject("minResponseTime"));
         maxRT = new Metrics(object.getJSONObject("maxResponseTime"));
         meanRT = new Metrics(object.getJSONObject("meanResponseTime"));
         stdDev = new Metrics(object.getJSONObject("standardDeviation"));
         pct50th = new Metrics(object.getJSONObject("percentiles1"));
         pct75th = new Metrics(object.getJSONObject("percentiles2"));
         pct95th = new Metrics(object.getJSONObject("percentiles3"));
         pct99th = new Metrics(object.getJSONObject("percentiles4"));
      }
   }
}
