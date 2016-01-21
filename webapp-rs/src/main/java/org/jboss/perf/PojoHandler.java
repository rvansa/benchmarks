package org.jboss.perf;

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
@Path("/pojo")
public class PojoHandler {
   private volatile Object blackhole;

   @GET
   @Produces(MediaType.APPLICATION_JSON)
   public PojoPerson getPersonJson() {
      return PojoPerson.JOHNNY;
   }


   @GET
   @Path("/all")
   @Produces(MediaType.APPLICATION_JSON)
   public PojoPerson[] getAll() {
      return new PojoPerson[] { PojoPerson.DANNY, PojoPerson.JOHNNY, PojoPerson.PENNY };
   }

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   public void postPersonJson(PojoPerson person) {
      blackhole = person;
   }

   @PUT
   @Consumes(MediaType.APPLICATION_JSON)
   @Produces(MediaType.APPLICATION_JSON)
   public PojoPerson putPersonJson(PojoPerson person) {
      blackhole = person;
      return PojoPerson.JOHNNY;
   }
}
