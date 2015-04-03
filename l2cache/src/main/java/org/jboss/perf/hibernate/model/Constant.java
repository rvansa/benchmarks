package org.jboss.perf.hibernate.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

/**
 * Read-only cached entity
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
@Immutable
public class Constant {
    @Id
    @GeneratedValue
    long id;

    String value;

    public Constant() {
    }

    public Constant(String value) {
        this.value = value;
    }

    public long getId() {
        return id;
    }

    public String getValue() {
        return value;
    }
}
