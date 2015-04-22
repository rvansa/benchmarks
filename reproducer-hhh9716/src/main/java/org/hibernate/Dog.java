package org.hibernate;

import javax.persistence.Entity;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Entity
//@DiscriminatorValue("D")
public class Dog extends Mammal {
    String bar;

    public String getBar() {
        return bar;
    }

    public void setBar(String bar) {
        this.bar = bar;
    }
}
