package org.jboss.perf;

import org.jboss.perf.model.JaxbPerson;
import org.jboss.perf.model.PojoPerson;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
@Path("/jaxb")
public class JaxbHandler {
   private volatile Object blackhole;

   @GET
   @Produces(MediaType.APPLICATION_JSON)
   public JaxbPerson getPersonJson() {
      return JaxbPerson.JOHNNY;
   }

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   public void postPersonJson(JaxbPerson person) {
      blackhole = person;
   }

   @PUT
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   public JaxbPerson putPersonJson(JaxbPerson person) {
      blackhole = person;
      return JaxbPerson.JOHNNY;
   }

   @GET
   @Produces(MediaType.APPLICATION_XML)
   public JaxbPerson getPersonXml() {
      return JaxbPerson.JOHNNY;
   }

   @POST
   @Consumes(MediaType.APPLICATION_XML)
   public void postPersonXml(JaxbPerson person) {
      blackhole = person;
   }

   @PUT
   @Consumes(MediaType.APPLICATION_XML)
   @Produces(MediaType.APPLICATION_XML)
   public JaxbPerson putPersonXml(JaxbPerson person) {
      blackhole = person;
      return JaxbPerson.JOHNNY;
   }
}
