package org.jboss.perf;

import org.HdrHistogram.Histogram;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class RunData {
   String name;
   long startTime = Long.MAX_VALUE;
   long endTime = Long.MIN_VALUE;
   final Histogram histogram = new Histogram(TimeUnit.SECONDS.toNanos(60), 3);
   long failedRequests;
   long errors;
   long[] userStart = new long[1024];
   long[] userEnd = new long[1024];
   int[] users = new int[1000];

   public RunData merge(RunData runData) {
      if (!name.equals(runData.name)) {
         throw new IllegalArgumentException("Cannot merge " + runData.name + " into " + name);
      }
      histogram.add(runData.histogram);
      failedRequests += runData.failedRequests;
      errors += runData.errors;
      return this;
   }

   public void addUser(int userId, long start, long end) {
      if (userId > userStart.length) {
         userStart = Arrays.copyOf(userStart, Math.max(userStart.length * 2, userId));
         userEnd = Arrays.copyOf(userEnd, Math.max(userEnd.length * 2, userId));
      }
      userStart[userId - 1] = start;
      userEnd[userId - 1] = end;

      endTime = Math.max(endTime, end);
      int relativeStart = (int) TimeUnit.MILLISECONDS.toSeconds(end - startTime);
      int relativeEnd = (int) TimeUnit.MILLISECONDS.toSeconds(end - startTime);
      if (relativeEnd >= users.length) {
         users = Arrays.copyOf(users, Math.max(users.length * 2, relativeEnd + 1));
      }
      for (int time = relativeStart; time <= relativeEnd; ++time) {
         users[time]++;
      }
   }
}
