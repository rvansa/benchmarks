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

  def root() = Activator.ROOT_PATH;

  def protocolConf() = {
      http.baseURL("http://" + host + ":" + port + root()).doNotTrackHeader("1").shareConnections
  }

  def pre(scenarioBuilder: ScenarioBuilder) = scenarioBuilder
  def run(http: Http): HttpRequestBuilder;
  def run(scenarioBuilder: ScenarioBuilder): ScenarioBuilder = pre(scenarioBuilder).exec(run(http(name)))

  var name = getClass().getName();
  name = name.substring(name.lastIndexOf('.') + 1).replaceAllLiterally("$", ".");
  setUp(run(scenario(name)).inject(rampUsersPerSec(1) to (rps.toInt) during (rampUp seconds), constantUsersPerSec(rps) during (duration seconds)).protocols(protocolConf()))
}
