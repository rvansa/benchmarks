package org.jboss.perf

import io.gatling.core.Predef._
import io.gatling.core.body.ByteArrayBody
import io.gatling.http.Predef.{status, bodyBytes}
import io.gatling.http.request.builder.Http

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
      http.post("/bytes").body(ByteArrayBody(BytesHandler.SOME_BYTES)).check(status.is(204));
    }
  }

  class Put extends BaseSimulation with AppBinary {
    def run(http: Http) = {
      http.put("/bytes").body(ByteArrayBody(BytesHandler.SOME_BYTES)).check(status.is(200), bodyBytes.exists);
    }
  }
}

