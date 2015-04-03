package org.jboss.perf.hibernate;

import java.util.Collection;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;

/**
 * Interface for invoking query with entity manager and ongoing transaction.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface QueryRunner {
    Collection<?> runQuery(EntityManager entityManager, CriteriaBuilder cb);
}
