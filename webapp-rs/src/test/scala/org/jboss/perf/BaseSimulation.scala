package org.jboss.perf

import java.io.FileInputStream
import java.util.Properties

import io.gatling.core.Predef._
import io.gatling.core.structure.ScenarioBuilder
import io.gatling.core.controller.inject.InjectionStep
import io.gatling.http.Predef._
import io.gatling.http.request.builder.{Http, HttpRequestBuilder}
import org.jboss.perf.BaseSimulation._

import scala.concurrent.duration._;


/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
abstract class BaseSimulation extends Simulation {

  def root() = Activator.ROOT_PATH;

  // TODO: use this as implicit configuration
  def config() = {
    BaseSimulation.cfg
  }

  def protocolConf() = {
      http(config).baseURL("http://" + host + ":" + port + root()).doNotTrackHeader("1").shareConnections
  }

  def pre(scenarioBuilder: ScenarioBuilder) = scenarioBuilder
  def run(http: Http): HttpRequestBuilder;
  def run(scenarioBuilder: ScenarioBuilder): ScenarioBuilder = pre(scenarioBuilder).repeat(reqsPerUser) {
    exec(run(http(name))).exitHereIfFailed
  }

  var name = getClass().getName();
  name = name.substring(name.lastIndexOf('.') + 1).replaceAllLiterally("$", ".");
  var injectionSteps = new Array[InjectionStep](0);
  val usersPerSec = if (usersPerSecFile == null) {
    BaseSimulation.usersPerSec
  } else {
    var file = new FileInputStream(usersPerSecFile)
    try {
      val properties = new Properties()
      properties.load(file)
      properties.getProperty(getClass.getName).toInt
    } finally {
      file.close()
    }
  }
  System.out.println("Running with " + usersPerSec)
  if (rampUp > 0) injectionSteps = injectionSteps :+ (rampUsersPerSec(rampUsersFrom) to (usersPerSec.toInt) during (rampUp seconds))
  if (duration > 0) injectionSteps = injectionSteps :+ (constantUsersPerSec(usersPerSec) during (duration seconds))
  setUp(run(scenario(name)).inject(injectionSteps).protocols(protocolConf())).maxDuration(rampUp + duration)
}

object BaseSimulation {
  val rampUsersFrom = Integer.getInteger("test.rampUsersFrom", 1).intValue()
  val rampUp = Integer.getInteger("test.rampUp", 2).intValue()
  val duration = Integer.getInteger("test.duration", 2)
  val host = System.getProperty("test.host", "localhost")
  val port = Integer.getInteger("test.port", 8080)
  val usersPerSec = Integer.getInteger("test.usersPerSec", 100).intValue()
  val reqsPerUser = Integer.getInteger("test.reqsPerUser", 10).intValue()
  val usersPerSecFile: String = {
    val value = System.getProperty("test.usersPerSec")
    if (value != null) {
      try {
        Integer.decode(value)
        null
      } catch {
        case e: NumberFormatException => value;
      }
    } else null
  }

  val defaultCfg = io.gatling.core.Predef.configuration
  val soReuseAddress = System.getProperty("test.soReuseAddress", defaultCfg.http.ahc.soReuseAddress.toString).toBoolean
  val keepAlive = System.getProperty("test.keepAlive", defaultCfg.http.ahc.keepAlive.toString).toBoolean
  val requestTimeout = System.getProperty("test.requestTimeout", defaultCfg.http.ahc.requestTimeOut.toString).toInt

  val cfg = defaultCfg.copy(
    http = defaultCfg.http.copy(
      ahc = defaultCfg.http.ahc.copy(
        soReuseAddress = soReuseAddress,
        keepAlive = keepAlive,
        requestTimeOut = requestTimeout
      )
    ),
    core = defaultCfg.core.copy(
      directory = defaultCfg.core.directory.copy(
        results = System.getProperty("gatling.resultsFolder", defaultCfg.core.directory.results)
      )
    )
  )

  val longToInt: (Object => Object) = x => java.lang.Integer.valueOf(x.asInstanceOf[java.lang.Long].intValue())
}