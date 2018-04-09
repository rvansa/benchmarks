package org.jboss.perf.hibernate.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
//@DiscriminatorColumn(discriminatorType = DiscriminatorType.CHAR)
//@DiscriminatorValue("M")
public class Mammal {
    @Id
    @GeneratedValue
    long id;

    String foo;

    public long getId() {
        return id;
    }

    public String getFoo() {
        return foo;
    }

    public void setFoo(String foo) {
        this.foo = foo;
    }
}
