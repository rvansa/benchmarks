package org.jboss.perf;

//import org.jboss.perf.hibernate.AraQueue;


public class QueueTest {
   /*@Test
   public void testST() {
      testST(new AraQueue(), 512);
   }

   @Test
   public void testMT() {
      testMT(new AraQueue());
   }

   private void testST(Queue q, int maxSize) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      int size = 0;
      int counter = 0;
      Set<Object> set = new HashSet<>();
      for (int i = 0; i < 10000; ++i) {
         int maxProduce = Math.min(maxSize - size, 32);
         for (int j = maxProduce == 1 ? 1 : random.nextInt(1, maxProduce); j > 0; --j) {
            if (q.offer(counter)) {
               ++counter;
               ++size;
            } else break;
         }
         int maxConsume = Math.min(size, 32);
         for (int j = maxConsume == 1 ? 1 : random.nextInt(1, maxConsume); j > 0; --j) {
            Object v;
            if ((v = q.poll()) != null) {
               --size;
               set.add(v);
            } else {
               break;
            }
         }
      }
      while (size > 0) {
         Object v;
         if ((v = q.poll()) != null) {
            --size;
            set.add(v);
         }
      }
      for (int i = 0; i < counter; ++i) {
         assert set.contains(i) : "missing " + i;
      }
   }

   private void testMT(Queue q) {
      final int NUM_PRODUCERS = 4;
      final int NUM_ELEMENTS = 10000000;
      ExecutorService executorService = Executors.newFixedThreadPool(NUM_PRODUCERS);
      for (int i = 0; i < NUM_PRODUCERS; ++i) {
         int myId = i;
         executorService.execute(() -> {
            for (int j = myId; j < NUM_ELEMENTS; j += NUM_PRODUCERS) {
               while (!q.offer(j)) Thread.yield();
            }
         });
      }
      Set<Object> set = new HashSet<>();
      for (int i = 0; i < NUM_ELEMENTS; ++i) {
         Object v;
         while ((v = q.poll()) == null) {
            Thread.yield();
         }
         set.add(v);
      }
      for (int i = 0; i < NUM_ELEMENTS; ++i) {
         assert set.contains(i) : "missing " + i;
      }
   } */
}
