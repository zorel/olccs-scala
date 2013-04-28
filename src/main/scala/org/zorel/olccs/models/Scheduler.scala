package org.zorel.olccs.models

import _root_.akka.actor.{Props, ActorSystem}
import org.zorel.olccs.actors.ReloadActor
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created with IntelliJ IDEA.
 * User: zorel
 * Date: 13/04/13
 * Time: 22:36
 * To change this template use File | Settings | File Templates.
 */
object Scheduler {
  val system = ActorSystem("OlccsSystem")
  val reload_actor = system.actorOf(Props[ReloadActor], name = "reload_actor")
  val init: AtomicBoolean = new AtomicBoolean(false)
  val l = LoggerFactory.getLogger(getClass)

  def start = {
    if(init.get() == false) {
      try {
        system.scheduler.schedule(0 seconds, 15 seconds) {
          reload_actor ! "refresh"
        }
        l.info("Scheduler initialized")
      } finally {
        init.set(true)
      }
    } else {
      l.error("Scheduler already initialized")
    }
  }

}
