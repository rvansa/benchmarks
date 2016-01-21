package org.jboss.perf


import javax.ws.rs.core.MediaType

import io.gatling.core.Predef._

/**
  * @author Radim Vansa &ltrvansa@redhat.com&gt;
  */
trait AppXml extends BaseSimulation {
  override def protocolConf() = {
    super.protocolConf().acceptHeader(MediaType.APPLICATION_XML).contentTypeHeader(MediaType.APPLICATION_XML)
  }
}
