package org.zorel.olccs.actors

import _root_.akka.actor.{Props, Actor}
import org.slf4j.LoggerFactory
import org.zorel.olccs.models.Board
import _root_.akka.routing.RoundRobinRouter

/**
 * Created with IntelliJ IDEA.
 * User: zorel
 * Date: 13/04/13
 * Time: 22:28
 * To change this template use File | Settings | File Templates.
 */
class ReloadActor extends Actor {
  val l = LoggerFactory.getLogger(getClass)
  val worker = context.actorOf(Props[ReloadWorker].withRouter(RoundRobinRouter(10)), name = "Reloadworker")

  def receive = {
    case "refresh" => {
      l.debug("Receive refresh order")
      for (b@(s, board) <- Board.boards) {
        l.debug("Refresh for %s" format s)
        worker ! board
      }

    }
  }

}
