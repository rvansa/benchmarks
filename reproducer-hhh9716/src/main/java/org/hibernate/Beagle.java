package org.hibernate;

import javax.persistence.Entity;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Entity
//@DiscriminatorValue("B")
public class Beagle extends Dog {
    String goo;

    public String getGoo() {
        return goo;
    }

    public void setGoo(String goo) {
        this.goo = goo;
    }
}
