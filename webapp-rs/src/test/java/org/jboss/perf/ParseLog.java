package org.jboss.perf;

import io.gatling.charts.stats.ErrorRecord;
import io.gatling.charts.stats.ErrorRecordParser$;
import io.gatling.charts.stats.RequestRecord;
import io.gatling.charts.stats.RequestRecordParser;
import io.gatling.charts.stats.UserRecord;
import io.gatling.charts.stats.UserRecordParser;
import io.gatling.commons.stats.KO$;
import io.gatling.core.Predef$;
import io.gatling.core.config.GatlingConfiguration;
import io.gatling.core.stats.message.End$;
import io.gatling.core.stats.writer.LogFileDataWriter$;
import io.gatling.core.stats.writer.RunMessage;
import scala.Option;
import scala.collection.mutable.Map$;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;


public class ParseLog {
   static {
      Predef$.MODULE$.configuration_$eq(GatlingConfiguration.load(Map$.MODULE$.empty()));
   }

   private static final String SEPARATOR = "" + LogFileDataWriter$.MODULE$.Separator();
   private static final UserRecordParser USER_RECORD_PARSER = new UserRecordParser(BaseSimulation$.MODULE$.longToInt());
   private static final RequestRecordParser REQUEST_RECORD_PARSER = new RequestRecordParser(BaseSimulation$.MODULE$.longToInt());;
   private static final ErrorRecordParser$ ERROR_RECORD_PARSER = ErrorRecordParser$.MODULE$;

   // point where we consider the server overloaded
   private static int overload = Integer.getInteger("test.overload", BaseSimulation$.MODULE$.usersPerSec() * 3);

   public static void main(String[] args) {
      GatlingConfiguration cfg = BaseSimulation$.MODULE$.cfg();
      String resultsDir = cfg.core().directory().results();

      Map<String, RunData> data = new HashMap<>();
      for (File subdir : new File(resultsDir).listFiles()) {
         if (!subdir.isDirectory()) {
            System.err.printf("Skipping %s/%s which is not a directory%n", resultsDir, subdir);
            continue;
         }
         for (File simulationLog : subdir.listFiles((dir, name) -> name.endsWith(".log"))) {
            System.err.printf("Parsing %s%n", simulationLog.getAbsolutePath());
            RunData runData = new RunData();
            parseLog(simulationLog, runMessage -> {
                  runData.name = runMessage.simulationClassName();
                  runData.startTime = runMessage.start();
               },
               requestRecord -> {
                  if (runData.startTime == Long.MAX_VALUE) {
                     System.err.println("Missing RUN record at the beginning");
                  } else if (requestRecord.start() - runData.startTime < TimeUnit.SECONDS.toMillis(BaseSimulation$.MODULE$.rampUp())) {
                     //ignore
                  } else {
                     runData.histogram.recordValue(requestRecord.responseTime());
                     if (requestRecord.status() == KO$.MODULE$) {
                        runData.failedRequests++;
                     }
                  }
               },
               errorRecord -> {
                  if (runData.startTime == Long.MAX_VALUE) {
                     System.err.println("Missing RUN record at the beginning");
                  } else if (errorRecord.timestamp() - runData.startTime < BaseSimulation$.MODULE$.rampUp()) {
                     //ignore
                  } else {
                     runData.errors++;
                  }
               },
               userRecord -> {
                  if (userRecord.event() == End$.MODULE$) {
                     runData.addUser(Integer.parseInt(userRecord.userId()), userRecord.start(), userRecord.end());
                  }
               });
            data.compute(runData.name, (name, pre) -> pre == null ? runData : pre.merge(runData));
         }
      }
      if (data.isEmpty()) {
         System.err.println("No data.");
         return;
      }
      int maxLenght = data.keySet().stream().mapToInt(String::length).max().getAsInt();
      String indent = indent(maxLenght - 4);
      System.out.println();
      System.out.printf("Test%s Requests Errors   Mean     Std.dev. Min      Max      50th pct 75th pct 95th pct 99th pct over (num)%n", indent);
      System.out.printf("    %s -------- -------- -------- -------- -------- -------- -------- -------- -------- -------- ---- -----%n", indent);
      for (RunData s : data.values()) {
         int overTime = IntStream.range(0, s.users.length).filter(time -> s.liveUsersAt(time * 1000 + 500) > overload).findFirst().orElse(-1);
         System.out.printf("%s%s %8d %8d %8.3f %8.3f %8d %8d %8d %8d %8d %8d %4d %5d%n", s.name, indent(maxLenght - s.name.length()),
            s.histogram.getTotalCount(), s.failedRequests, s.histogram.getMean(), s.histogram.getStdDeviation(),
            s.histogram.getMinValue(), s.histogram.getMaxValue(), s.histogram.getValueAtPercentile(50),
            s.histogram.getValueAtPercentile(75), s.histogram.getValueAtPercentile(95), s.histogram.getValueAtPercentile(99),
            overTime, s.liveUsersAt(overTime * 1000 + 500));
      }
   }

   private static String indent(int maxLenght) {
      return IntStream.range(0, maxLenght).mapToObj(x -> " ")
         .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
   }

   private static void parseLog(File simulationLog, Consumer<RunMessage> runRecords, Consumer<RequestRecord> requestRecords, Consumer<ErrorRecord> errorRecords, Consumer<UserRecord> userRecords) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(simulationLog)))) {
         for (; ; ) {
            String line = reader.readLine();
            if (line == null)
               break;
            String[] strings = line.split(SEPARATOR);
            if (strings.length == 0)
               continue;
            switch (strings[0]) {
               case "RUN":
                  runRecords.accept(new RunMessage(strings[1], Option.apply(strings[2]), strings[3], Long.parseLong(strings[4]), strings[5].trim()));
                  break;
               case "REQUEST":
                  requestRecords.accept(REQUEST_RECORD_PARSER.unapply(strings).get());
                  break;
               case "ERROR":
                  errorRecords.accept(ERROR_RECORD_PARSER.unapply(strings).get());
                  break;
               case "USER":
                  userRecords.accept(USER_RECORD_PARSER.unapply(strings).get());
                  break;
            }
         }
      } catch (IOException e) {
         e.printStackTrace();
      }
   }
}
