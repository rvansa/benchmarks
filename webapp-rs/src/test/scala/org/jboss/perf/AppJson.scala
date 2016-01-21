package org.jboss.perf

import javax.ws.rs.core.MediaType
import io.gatling.core.Predef._

/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
trait AppJson extends BaseSimulation {
  override def protocolConf() = {
    super.protocolConf().acceptHeader(MediaType.APPLICATION_JSON).contentTypeHeader(MediaType.APPLICATION_JSON)
  }
}
