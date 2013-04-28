package org.zorel.olccs.actors

import akka.actor.Actor
import org.slf4j.LoggerFactory

/**
 * Created with IntelliJ IDEA.
 * User: zorel
 * Date: 13/04/13
 * Time: 19:32
 * To change this template use File | Settings | File Templates.
 */
class MyActor extends Actor {
  val log = LoggerFactory.getLogger(getClass)

  def receive = {
    case "test" => log.info("receive test")
    case _ => log.info("unknow message")
  }
}
