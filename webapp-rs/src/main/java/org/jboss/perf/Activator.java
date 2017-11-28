package org.jboss.perf;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
@ApplicationPath("/test")
public class Activator extends Application {
   public static final String ROOT_PATH = "/webapp-rs/test";

   private final Set<Object> singletons = new HashSet<>(Arrays.asList(
      new BytesHandler(), new JaxbHandler(), new ParamHandler(), new PojoHandler(), new TextHandler()
   ));

   @Override
   public Set<Object> getSingletons() {
      return singletons;
   }

   @Provider
   public static class QuietNotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
      @Override
      public Response toResponse(NotFoundException exception) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
   }
}
