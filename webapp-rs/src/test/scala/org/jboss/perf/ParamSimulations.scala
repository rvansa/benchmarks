package org.jboss.perf


import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.Http

/**
  *
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
object ParamSimulations {
  class Path extends BaseSimulation {
    def run(http: Http) = {
      http.get("/param/path/xxx/42/yyy_zzz").check(status.is(202))
    }
  }

  class PathFailed extends BaseSimulation {
    def run(http: Http) = {
      http.get("/param/path/xxx/not-a-number/yyy_zzz").check(status.is(404))
    }
  }

  class Query extends BaseSimulation {
    def run(http: Http) = {
      http.get("/param/query")
        .queryParam("foo", "xxx")
        .queryParam("bar", "42")
        .queryParam("goo", "yyy_zzz").check(status.is(202))
    }
  }

  class Header extends BaseSimulation {
    def run(http: Http) = {
      http.get("/param/header")
        .header("foo", "xxx")
        .header("bar", "42")
        .header("goo", "yyy_zzz").check(status.is(202))
    }
  }

  class Matrix extends BaseSimulation {
    def run(http: Http) = {
      http.get("/param/matrix;foo=xxx;bar=42;goo=yyy_zzz")
        .check(status.is(202))
    }
  }

  class Cookie extends BaseSimulation {
    override def pre(scenarioBuilder: ScenarioBuilder): ScenarioBuilder = {
      scenarioBuilder
        .exec(addCookie(Cookie("foo", "xxx")))
        .exec(addCookie(Cookie("bar", "42")))
        .exec(addCookie(Cookie("goo", "yyy_zzz")))
    }

    def run(http: Http) = {
      http.get("/param/cookie").check(status.is(202))
    }
  }

  class Form extends BaseSimulation {
    def run(http: Http) = {
      http.post("/param/form")
        .formParam("foo", "xxx")
        .formParam("bar", "42")
        .formParam("goo", "yyy_zzz")
        .check(status.is(202))
    }
  }

  class Segment extends BaseSimulation {
    def run(http: Http) = {
      http.get("/param/segment/foo_xxx;bar=42;goo=yyy_zzz")
        .check(status.is(202))
    }
  }
}
