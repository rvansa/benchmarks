package org.jboss.perf

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.core.controller.inject.InjectionStep
import io.gatling.http.Predef._
import io.gatling.http.request.builder.{Http, HttpRequestBuilder}

import scala.concurrent.duration._;

/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
abstract class BaseSimulation extends Simulation {
  val rampUp = Integer.getInteger("test.rampUp", 2).intValue();
  val duration = Integer.getInteger("test.duration", 2)
  val host = System.getProperty("test.host", "localhost");
  val port = Integer.getInteger("test.port", 8080);
  val usersPerSec = Integer.getInteger("test.usersPerSec", 100).doubleValue();
  val reqsPerUser = Integer.getInteger("test.reqsPerUser", 10).doubleValue();

  def root() = Activator.ROOT_PATH;

  def configuration() = {
    val cfg = io.gatling.core.Predef.configuration
    cfg.copy(http = cfg.http.copy(
      ahc = cfg.http.ahc.copy(
        soReuseAddress = System.getProperty("test.soReuseAddress", cfg.http.ahc.soReuseAddress.toString).toBoolean,
        keepAlive = System.getProperty("test.keepAlive", cfg.http.ahc.keepAlive.toString).toBoolean)
    ))
  }

  def protocolConf() = {
      http.baseURL("http://" + host + ":" + port + root()).doNotTrackHeader("1").shareConnections
  }

  def pre(scenarioBuilder: ScenarioBuilder) = scenarioBuilder
  def run(http: Http): HttpRequestBuilder;
  def run(scenarioBuilder: ScenarioBuilder): ScenarioBuilder = pre(scenarioBuilder).repeat(reqsPerUser.toInt) {
    exec(run(http(name)))
  }

  var name = getClass().getName();
  name = name.substring(name.lastIndexOf('.') + 1).replaceAllLiterally("$", ".");
  var injectionSteps = new Array[InjectionStep](0);
  if (rampUp > 0) injectionSteps :+ (rampUsersPerSec(1) to (usersPerSec.toInt) during (rampUp seconds))
  if (duration > 0) injectionSteps :+ (constantUsersPerSec(usersPerSec) during (duration seconds))
  setUp(run(scenario(name)).inject(injectionSteps).protocols(protocolConf()))
}
