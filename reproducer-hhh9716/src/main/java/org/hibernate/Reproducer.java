package org.hibernate;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Reproducer {
   public static void main(String[] args) {
      test();
      test();
   }

   public static void test() {
      System.out.println("Test...");
      EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("hikari.mem");

      Object id;

      EntityManager em = entityManagerFactory.createEntityManager();
      em.getTransaction().begin();
      Beagle entity = new Beagle();
      entity.setBar("BAR");
      em.persist(entity);
      em.getTransaction().commit();
      id = entity.id;
      em.close();

      em = entityManagerFactory.createEntityManager();
      em.getTransaction().begin();
      entity = em.find(Beagle.class, id);
      System.out.printf("Entity is %s\n", entity);
      em.getTransaction().commit();
      em.close();

      entityManagerFactory.close();
   }
}
