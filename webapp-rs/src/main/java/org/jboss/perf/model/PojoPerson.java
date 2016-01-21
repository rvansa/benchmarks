package org.jboss.perf.model;

/**
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
public class PojoPerson {
   public static final PojoPerson JOHNNY = new PojoPerson("John", "Black", 33,
         new Address("East Road", 666, "New York"));
   public static final String JOHNNY_JSON = "{\"firstName\":\"John\",\"lastName\":\"Black\",\"age\":33,\"address\":" +
         "{\"street\":\"East Road\",\"number\":666,\"city\":\"New York\"}}";
   public static final PojoPerson DANNY = new PojoPerson("Dan", "White", 66,
         new Address("West Road", 999, "Old York"));
   public static final String DANNY_JSON = "{\"firstName\":\"Dan\",\"lastName\":\"White\",\"age\":66,\"address\":" +
         "{\"street\":\"West Road\",\"number\":999,\"city\":\"Old York\"}}";
   public static final PojoPerson PENNY = new PojoPerson("Penny", "Red", 18,
         new Address("Blueberry st.", 111, "New Jersey"));
   public static final String PENNY_JSON = "{\"firstName\":\"Penny\",\"lastName\":\"Red\",\"age\":18,\"address\":" +
         "{\"street\":\"Blueberry st.\",\"number\":111,\"city\":\"New Jersey\"}}";


   private String firstName;
   private String lastName;
   private int age;
   private Address address;

   public PojoPerson() {
   }

   public PojoPerson(String firstName, String lastName, int age, Address address) {
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

   public static class Address {
      private String street;
      private int number;
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
