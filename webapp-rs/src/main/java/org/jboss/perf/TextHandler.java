package org.jboss.perf;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
@Path("/text")
public class TextHandler {
   private volatile Object blackhole;

   @GET
   @Produces(MediaType.TEXT_PLAIN)
   public String getText() {
      return "foo";
   }

   @POST
   @Consumes(MediaType.TEXT_PLAIN)
   public void postText(String text) {
      blackhole = text;
   }

   @PUT
   @Consumes(MediaType.TEXT_PLAIN)
   @Produces(MediaType.TEXT_PLAIN)
   public String putText(String text) {
      blackhole = text;
      return "goo";
   }
}
