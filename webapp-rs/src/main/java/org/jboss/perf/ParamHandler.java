package org.jboss.perf;

import org.jboss.perf.model.TwoStrings;

import javax.ws.rs.CookieParam;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;

/**
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
@Path("/param")
public class ParamHandler {
   private volatile Object blackhole;

   private void consume(String foo, int bar, TwoStrings goo) {
      blackhole = foo;
      blackhole = bar;
      blackhole = goo;
   }

   @GET
   @Path("/path/{foo}/{bar}/{goo}")
   public Response param(@PathParam("foo") String foo, @PathParam("bar") int bar, @PathParam("goo") TwoStrings goo) {
      consume(foo, bar, goo);
      return Response.accepted().build();
   }

   @GET
   @Path("/query")
   public Response query(@QueryParam("foo") String foo, @QueryParam("bar") int bar, @QueryParam("goo") TwoStrings goo) {
      consume(foo, bar, goo);
      return Response.accepted().build();
   }

   @GET
   @Path("/header")
   public Response header(@HeaderParam("foo") String foo, @HeaderParam("bar") int bar, @HeaderParam("goo") TwoStrings goo) {
      consume(foo, bar, goo);
      return Response.accepted().build();
   }

   @GET
   @Path("/matrix")
   public Response matrix(@MatrixParam("foo") String foo, @MatrixParam("bar") int bar, @MatrixParam("goo") TwoStrings goo) {
      consume(foo, bar, goo);
      return Response.accepted().build();
   }

   @GET
   @Path("/cookie")
   public Response cookie(@CookieParam("foo") String foo, @CookieParam("bar") int bar, @CookieParam("goo") TwoStrings goo) {
      consume(foo, bar, goo);
      return Response.accepted().build();
   }

   @POST
   @Path("/form")
   public Response form(@FormParam("foo") String foo, @FormParam("bar") int bar, @FormParam("goo") TwoStrings goo) {
      consume(foo, bar, goo);
      return Response.accepted().build();
   }

   @GET
   @Path("/segment/{id}")
   public Response segment(@PathParam("id") PathSegment pathSegment) {
      int bar = Integer.parseInt(pathSegment.getMatrixParameters().getFirst("bar"));
      TwoStrings goo = new TwoStrings(pathSegment.getMatrixParameters().getFirst("goo"));
      consume(pathSegment.getPath(), bar, goo);
      return Response.accepted().build();
   }
}
