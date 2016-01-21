package org.jboss.perf;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * @author Radim Vansa &ltrvansa@redhat.com&gt;
 */
@ApplicationPath("/test")
public class Activator extends Application {
   public static final String ROOT_PATH = "/webapp-rs/test";

   @Provider
   public static class QuietNotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
      @Override
      public Response toResponse(NotFoundException exception) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
   }
}
