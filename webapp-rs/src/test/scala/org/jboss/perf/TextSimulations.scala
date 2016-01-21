package org.jboss.perf

import javax.ws.rs.core.MediaType

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.Http

/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
object TextSimulations {
  abstract class TextSimualtion extends BaseSimulation {
    override def protocolConf() = {
      super.protocolConf().acceptHeader(MediaType.TEXT_PLAIN).contentTypeHeader(MediaType.TEXT_PLAIN)
    }
  }

  class Get extends TextSimualtion {
    def run(http: Http) = {
      http.get("/text").check(status.is(200), bodyString.is("foo"))
    }
  }

  class Post extends TextSimualtion {
    def run(http: Http) = {
      http.post("/text").body(StringBody("bar")).check(status.is(204))
    }
  }

  class Put extends TextSimualtion {
    def run(http: Http) = {
      http.put("/text").body(StringBody("bar")).check(status.is(200), bodyString.is("goo"))
    }
  }
}
