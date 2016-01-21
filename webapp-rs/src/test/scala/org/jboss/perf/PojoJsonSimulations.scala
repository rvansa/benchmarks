package org.jboss.perf

import io.gatling.core.Predef._
import io.gatling.core.validation.Validation
import io.gatling.http.Predef._
import io.gatling.http.request.StringBody
import io.gatling.http.request.builder.Http
import org.jboss.perf.model.PojoPerson

/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
object PojoJsonSimulations {
  class Get extends BaseSimulation with AppJson {
    def run(http: Http) = {
      http.get("/pojo").check(status.is(200), bodyString.is(PojoPerson.JOHNNY_JSON))
    }
  }

  class GetAll extends BaseSimulation with AppJson {
    def run(http: Http) = {
      val expected = "[" + PojoPerson.DANNY_JSON + "," + PojoPerson.JOHNNY_JSON + "," + PojoPerson.PENNY_JSON + "]"
      http.get("/pojo/all").check(status.is(200), bodyString.is(expected))
    }
  }

  class Post extends BaseSimulation with AppJson {
    def run(http: Http) = {
      http.post("/pojo").body(new StringBody(PojoPerson.JOHNNY_JSON)).check(status.is(204));
    }
  }

  class Put extends BaseSimulation with AppJson {
    def run(http: Http) = {
      http.put("/pojo").body(new StringBody(PojoPerson.JOHNNY_JSON)).check(status.is(200), bodyString.is(PojoPerson.JOHNNY_JSON));
    }
  }
}

