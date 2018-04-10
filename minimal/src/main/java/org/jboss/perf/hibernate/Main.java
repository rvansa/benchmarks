package org.jboss.perf.hibernate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Main {
    private static JacamarHelper jacamarHelper = new JacamarHelper();
    private static TransactionManager tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
    private static EntityManagerFactory entityManagerFactory;

    private static final Map<String, String> l2Properties;

    static {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put("javax.persistence.sharedCache.mode", "ALL");
        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.infinispan.InfinispanRegionFactory");
        properties.put("hibernate.cache.use_second_level_cache", "true");
        properties.put("hibernate.cache.use_query_cache", "true");
        properties.put("hibernate.cache.default_cache_concurrency_strategy", "transactional");
        //properties.put("hibernate.transaction.factory_class", "org.hibernate.transaction.JTATransactionFactory");
        properties.put("hibernate.transaction.manager_lookup_class", "org.hibernate.transaction.JBossTransactionManagerLookup");
        l2Properties = Collections.unmodifiableMap(properties);
    }

    public static void main(String[] args) throws Throwable {
        setup();
        persist();
        load();
        tearDown();
    }

    private static void setup() throws Throwable {
        tm.setTransactionTimeout(1200);
        jacamarHelper.start();
        entityManagerFactory = Persistence.createEntityManagerFactory("ironjacamar.xa", l2Properties);
        //entityManagerFactory = Persistence.createEntityManagerFactory("ironjacamar.xa");

        EntityManager entityManager = entityManagerFactory.createEntityManager();
        tm.begin();
        entityManager.joinTransaction();
        CriteriaDelete<Person> deleteAll = entityManager.getCriteriaBuilder().createCriteriaDelete(Person.class);
        deleteAll.from(Person.class);
        int deleted = entityManager.createQuery(deleteAll).executeUpdate();
        System.out.printf("Deleted %d Persons%n", deleted);
        tm.commit();
        entityManager.close();
    }

    private static void persist() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        tm.begin();
        entityManager.joinTransaction();
        entityManager.persist(new Person("John", "Jack", "Doe"));
        tm.commit();
        entityManager.close();
    }

    private static void load() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        tm.begin();
        entityManager.joinTransaction();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Person> query = cb.createQuery(Person.class);
        List<Person> johns = entityManager.createQuery(query.where(cb.like(query.from(Person.class).<String>get("firstName"), "John"))).getResultList();
        System.out.printf("Found %d Johns %n", johns.size());
        tm.commit();
        entityManager.close();
    }

    private static void tearDown() throws Throwable {
        entityManagerFactory.close();
        jacamarHelper.stop();
    }
}
