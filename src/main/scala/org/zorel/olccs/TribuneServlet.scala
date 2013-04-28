
package org.zorel.olccs

import org.zorel.olccs.models.Board
import org.scalatra._
import org.slf4j.LoggerFactory
import scala.Some

class TribuneServlet extends OlccsStack with ApiFormats {
  val l = LoggerFactory.getLogger(getClass)
  override protected implicit def string2RouteMatcher(path: String): RouteMatcher = RailsPathPatternParser(path)
  addMimeMapping("text/tsv", "tsv")

  get("/:tribune/remote(.:ext)") {
    val b = Board.boards(params("tribune"))
    val from = params.getOrElse("last", "0").toInt
    val to:Option[Int] = params.get("to") match {
      case Some(t) => Some(t.toInt)
      case None => None
    }

    val size:Int = params.getOrElse("size","50").toInt
    params.getOrElse("ext","xml") match {
      case "json" => {
        contentType = "application/json"
        Ok(b.backend_json(from,to,size))
      }
      case "xml" => {
        contentType = "application/xml"
        Ok(b.backend_xml(from,to,size))
      }
      case "tsv" => {
        contentType = "text/tsv"
        Ok(b.backend_tsv(from,to,size))
      }
    }
  }

  get("/:tribune/search(.:ext)") {
    val b = Board.boards(params("tribune"))
    val from = params.getOrElse("last", "0").toInt
    val query = params.getOrElse("query","")
    val to:Option[Int] = params.get("to") match {
      case Some(t) => Some(t.toInt)
      case None => None
    }

    val size:Int = params.getOrElse("size","50").toInt
    params.getOrElse("ext","xml") match {
      case "json" => {
        contentType = "application/json"
        Ok(b.search_json(query,from,to,size))
      }
      case "xml" => {
        contentType = "application/xml"
        Ok(b.search_xml(query,from,to,size))
      }
      case "tsv" => {
        contentType = "text/tsv"
        Ok(b.search_tsv(query,from,to,size))
      }
    }
  }

  post("/:tribune/post(.:ext)") {
    // TODO: gestion de l'ua, pour l'instant ça mets l'ua présente dans le user-agent du post
    val b = Board.boards(params("tribune"))

//    l.info("postdata" + params.get("postdata").getOrElse("postdata vide"))
//    l.info("message" + params.get("message").getOrElse("message vide"))
//    l.info("cookie param" + params.get("cookie").getOrElse("cookie param vide"))
//    l.info("Cookies request:")
//        for((k,v) <- request.cookies) {
//          l.info("%s / %s".format(k,v))
//        }
//    l.info("================")
    val message = params.get("postdata") match {
      case Some(p) => p
      case None => params.get("message") match {
        case Some(m) => m
        case None => ""
      }
      case _ => ""
    }

    val cookies = params.get("cookie") match {
      case Some(c) => {
        c match {
          case "" => Map[String, String]()
          case _ => {
            val a: Array[String] = c.split("=")
            Map((a(0), a(1)))
          }
        }
      }
      case None => request.cookies
      case _ => Map[String, String]()
    }

    val user_agent = params.get("ua") match {
      case Some(u) => u
      case None => request.header("User-Agent").getOrElse("").toString

    }
    val xpostid: String = b.post(cookies.toMap, user_agent, message)
//    xpostid match {
//      case Some(x) =>
    response.setHeader("X-Post-Id", xpostid)
//      case None => _
//    }
    params.get("last") match {
      case Some(l) => params.getOrElse("ext","xml") match {
        case "json" => {
          contentType = "application/json"
          Ok(b.backend_json(l.toInt))
        }
        case "xml" => {
          contentType = "application/xml"
          Ok(b.backend_xml(l.toInt))
        }
        case "tsv" => {
          contentType = "text/tsv"
          Ok(b.backend_tsv(l.toInt))
        }
      }
      case None => Ok("ok")
    }


  }

  post("/:tribune/login") {
    val b = Board.boards(params("tribune"))

    val login = params("login")
    val password = params("password")

    val cookies = b.login(login,password)
    l.info(cookies.toString)

    cookies.map(c => response.addCookie(Cookie(c._1, c._1)))

    Ok("ok")

  }

}
