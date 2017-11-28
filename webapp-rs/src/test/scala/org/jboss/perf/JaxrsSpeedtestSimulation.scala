package org.jboss.perf

import javax.ws.rs.core.MediaType

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.{Http, HttpRequestBuilder}


class JaxrsSpeedtestSimulation extends BaseSimulation {
  override def protocolConf() = {
    super.protocolConf().acceptHeader(MediaType.APPLICATION_JSON)
  }

  override def root() = ""

  override def run(http: Http): HttpRequestBuilder = {
    http.get("/jaxrs-eap/hello/advanced").queryParam("firstName", "AA").queryParam("lastName", "BB")
      .check(status.is(200), bodyString.transform(str => str.substring(0, 1)).is("{"))
  }
}
