package org.jboss.perf

import javax.ws.rs.core.MediaType

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.StringBody
import io.gatling.http.request.builder.Http
import org.jboss.perf.model.JaxbPerson;

/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
object JaxbXmlSimulations {
  class Get extends BaseSimulation with AppXml {
    def run(http: Http) = {
      http.get("/jaxb").check(status.is(200), bodyString.is(JaxbPerson.JOHNNY_XML))
    }
  }

  class Post extends BaseSimulation with AppXml {
    def run(http: Http) = {
      http.post("/jaxb").body(new StringBody(JaxbPerson.JOHNNY_XML)).check(status.is(204));
    }
  }

  class Put extends BaseSimulation with AppXml {
    def run(http: Http) = {
      http.put("/jaxb").body(new StringBody(JaxbPerson.JOHNNY_XML)).check(status.is(200), bodyString.is(JaxbPerson.JOHNNY_XML));
    }
  }
}


