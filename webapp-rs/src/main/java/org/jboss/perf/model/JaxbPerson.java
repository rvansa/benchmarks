package org.jboss.perf.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
@XmlRootElement(name = "person")
@XmlAccessorType(XmlAccessType.NONE)
public class JaxbPerson {
   public static final JaxbPerson JOHNNY = new JaxbPerson("John", "Black", 33,
         new Address("East Road", 666, "New York"));
   public static final String JOHNNY_JSON = "{\"firstName\":\"John\",\"lastName\":\"Black\",\"age\":33,\"address\":" +
         "{\"street\":\"East Road\",\"number\":666,\"city\":\"New York\"}}";
   public static final String JOHNNY_XML =
         "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
         "<person firstName=\"John\" lastName=\"Black\" age=\"33\">" +
            "<address street=\"East Road\" number=\"666\" city=\"New York\"/>" +
         "</person>";

   @XmlAttribute
   private String firstName;

   @XmlAttribute
   private String lastName;

   @XmlAttribute
   private int age;

   @XmlElement
   private Address address;

   public JaxbPerson() {
   }

   public JaxbPerson(String firstName, String lastName, int age, Address address) {
      this.firstName = firstName;
      this.lastName = lastName;
      this.age = age;
      this.address = address;
   }

   public String getFirstName() {
      return firstName;
   }

   public void setFirstName(String firstName) {
      this.firstName = firstName;
   }

   public String getLastName() {
      return lastName;
   }

   public void setLastName(String lastName) {
      this.lastName = lastName;
   }

   public int getAge() {
      return age;
   }

   public void setAge(int age) {
      this.age = age;
   }

   public Address getAddress() {
      return address;
   }

   public void setAddress(Address address) {
      this.address = address;
   }

   @XmlAccessorType(XmlAccessType.NONE)
   public static class Address {
      @XmlAttribute
      private String street;
      @XmlAttribute
      private int number;
      @XmlAttribute
      private String city;

      public Address() {
      }

      public Address(String street, int number, String city) {
         this.street = street;
         this.number = number;
         this.city = city;
      }

      public String getStreet() {
         return street;
      }

      public void setStreet(String street) {
         this.street = street;
      }

      public int getNumber() {
         return number;
      }

      public void setNumber(int number) {
         this.number = number;
      }

      public String getCity() {
         return city;
      }

      public void setCity(String city) {
         this.city = city;
      }
   }
}
