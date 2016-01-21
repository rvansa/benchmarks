package org.jboss.perf

import io.gatling.core.Predef._;
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.http.Predef._
import io.gatling.http.request.builder.{HttpRequestBuilder, Http}

import scala.concurrent.duration._;

/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
abstract class BaseSimulation extends Simulation {
  val rampUp = Integer.getInteger("test.rampUp", 2).intValue();
  val rps = Integer.getInteger("test.rps", 100).doubleValue();
  val duration = Integer.getInteger("test.duration", 2)
  val host = System.getProperty("test.host", "localhost");
  val port = Integer.getInteger("test.port", 8080);

  def protocolConf() = {
      http.baseURL("http://" + host + ":" + port + Activator.ROOT_PATH).doNotTrackHeader("1").shareConnections
  }

  def pre(scenarioBuilder: ScenarioBuilder) = scenarioBuilder
  def run(http: Http): HttpRequestBuilder;

  var name = getClass().getName();
  name = name.substring(name.lastIndexOf('.') + 1).replaceAllLiterally("$", ".");
  setUp(pre(scenario(name)).exec(run(http(name)))
    .inject(rampUsers(rps.toInt) over (rampUp seconds), constantUsersPerSec(rps) during (duration seconds)).protocols(protocolConf()))
}
