package org.jboss.perf

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.{ByteArrayBody, StringBody}
import io.gatling.http.request.builder.Http
import org.jboss.perf.model.PojoPerson

/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
object BytesSimulations {
  class Get extends BaseSimulation with AppBinary {
    def run(http: Http) = {
      http.get("/bytes").check(status.is(200), bodyBytes.exists)
    }
  }

  class Post extends BaseSimulation with AppBinary {
    def run(http: Http) = {
      http.post("/bytes").body(new ByteArrayBody(BytesHandler.SOME_BYTES)).check(status.is(204));
    }
  }

  class Put extends BaseSimulation with AppBinary {
    def run(http: Http) = {
      http.put("/bytes").body(new ByteArrayBody(BytesHandler.SOME_BYTES)).check(status.is(200), bodyBytes.exists);
    }
  }
}

