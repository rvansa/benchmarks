package org.jboss.perf.hibernate;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

/**
 * Test entity mapped to single row.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Entity
public class Person {
    @Id
    @GeneratedValue
    private long id;

    private String firstName;
    private String middleName;
    private String lastName;

    public Person() {
    }

    public Person(String firstName, String middleName, String lastName) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public long getId() {
        return id;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Person{");
        sb.append("id=").append(id);
        sb.append(", firstName='").append(firstName).append('\'');
        sb.append(", middleName='").append(middleName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
