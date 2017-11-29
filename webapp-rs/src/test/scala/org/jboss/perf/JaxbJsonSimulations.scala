package org.jboss.perf

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.core.body.StringBody
import io.gatling.http.request.builder.Http
import org.jboss.perf.model.JaxbPerson;

/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
object JaxbJsonSimulations {
  val cxf = java.lang.Boolean.getBoolean("cxf")
  val uploadJson = if (cxf) "{person:" + JaxbPerson.JOHNNY_JSON + "}" else JaxbPerson.JOHNNY_JSON;
  val downloadJson = if (cxf) JaxbPerson.JOHNNY_JSON else JaxbPerson.JOHNNY_JSON.replaceAll("@", "");

  class Get extends BaseSimulation with AppJson {
    def run(http: Http) = {
      http.get("/jaxb").check(status.is(200), bodyString.is(downloadJson) )
    }
  }

  class Post extends BaseSimulation with AppJson {
    def run(http: Http) = {
      http.post("/jaxb").body(StringBody(uploadJson)).check(status.is(204));
    }
  }

  class Put extends BaseSimulation with AppJson {
    def run(http: Http) = {
      http.put("/jaxb").body(StringBody(uploadJson)).check(status.is(200), bodyString.is(downloadJson) );
    }
  }
}


