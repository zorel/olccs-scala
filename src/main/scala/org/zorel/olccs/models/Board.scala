package org.zorel.olccs.models


import org.slf4j.LoggerFactory
import scala.xml.{Elem, XML}
import org.json4s.JsonDSL._
import org.zorel.olccs.elasticsearch._
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.search.SearchHit
import java.util.concurrent.atomic.AtomicBoolean
import org.json4s.jackson.JsonMethods._
import uk.co.bigbeeconsultants.http._
import uk.co.bigbeeconsultants.http.request.RequestBody
import uk.co.bigbeeconsultants.http.header._
import scala.collection.immutable.Map
import org.elasticsearch.index.query.QueryBuilders
import header.HeaderName._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Entities.EscapeMode
import com.github.theon.uri.Uri._
import scala.Some
import uk.co.bigbeeconsultants.http.header.Cookie
import javax.net.ssl._
import java.security.cert.X509Certificate
import java.security.SecureRandom


object Slip extends Enumeration {
  val Encoded, Raw = Value
}
class Board(val name: String,
             val get_url: String,
             val lastid_parameter: String,
             val slip_type: Slip.Value,
             val post_url: String,
             val post_parameter: String,
             val login_url: String,
             val cookie_name: String,
             val login_parameter: String,
             val password_parameter: String) {

  val l = LoggerFactory.getLogger(getClass)
  val lock: AtomicBoolean = new AtomicBoolean(false)

  var lastid = {
    try {
      backend(0,None, 1)(0).id
    } catch {
      case _:Throwable => 0
    }
  }

//  l.info("Lastid for %s: %s".format(name,lastid))

  def backend_orig: Elem = {
//    if(lock.get() == true) {
//      l.error("Fetch request already in progress for %s".format(name))
//    }
    val f = javax.xml.parsers.SAXParserFactory.newInstance()
    f.setValidating(false)
    f.setFeature("http://xml.org/sax/features/validation", false)
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false)
    f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    val p = f.newSAXParser()

    // Create a new trust manager that trusts all certificates
    val trustAllCerts = Array[TrustManager](new DumbTrustManager)

    // Activate the new trust manager
    val sc = SSLContext.getInstance("SSL")
    sc.init(Array[KeyManager](), trustAllCerts, new SecureRandom())

    val config = Config(followRedirects = false,
      keepAlive = false,
      userAgentString = Some("olccs-scala"),
      sslSocketFactory = Some(sc.getSocketFactory),
      hostnameVerifier = Some(new DumbHostnameVerifier))
    val httpClient = new HttpClient(config)

    val uri = lastid_parameter match {
      case "" => get_url
      case _ => get_url ? (lastid_parameter -> lastid)
    }
//    ?
    val response = httpClient.get(uri.toString())

    try {
      XML.withSAXParser(p).loadString(response.body.asString)
    } catch {
      case ex : Throwable => {
        l.error("Oops in backend reload for " + name)
        l.error(ex.getStackTrace.mkString("\n"))
        <board></board>
      }
    }
  }

// TODO
//  val raw = <post><message>test &amp; <b><u><i>meuh</i></u></b></message></post>
//  (raw \ "message").toString
//  (raw \ "message").text
//
//  val cdata = <post><message><![CDATA[test &amp; <b><u><i>meuh</i></u></b>]]></message></post>
//  (cdata \ "message").toString
//  (cdata \ "message").text
//
//  val encoded = <post><message>test &amp;#38; &lt;b&gt;&lt;i&gt;&lt;u&gt;meuh&lt;/u&gt;&lt;/i&gt;&lt;/b&gt;</message></post>
//  (encoded \ "message").toString
//  (encoded \ "message").text


  def index {
    l.debug("Start: index tribune %s" format name)
    val b = backend_orig
    (b \ "post" filter( x => (x \ "@id").text.toInt > lastid)).reverse.foreach { p =>
      l.debug("last: " + lastid + "=> " + (p \ "@id").text)
      val m = slip_type match {
        case Slip.Encoded => "<message>" + (p \ "message").text + "</message>"
        case Slip.Raw => (p \ "message").toString
      }
      var post: Post = null
//      try {

      // Parse str into a Document
      val doc : Document = Jsoup.parseBodyFragment(m)

      // Clean the document.
//      val doc2 = new Cleaner(Whitelist.simpleText()).clean(doc)

      // Adjust escape mode
      doc.outputSettings().escapeMode(EscapeMode.xhtml)
      doc.outputSettings().prettyPrint(false)

      // Get back the string of the body.
//      l.info(doc.body().html())
        lastid = (p \ "@id").text.toInt
        post = Post(name,
          (p \ "@id").text,
          (p \ "@time").text,
          (p \ "info").text,
          (p \ "login").text,
          XML.loadString(doc.body().html())
        )
//      } catch {
//        case ex: Throwable => {
//          l.info(m)
//          l.info("to string:" + (p \ "message").toString())
//          l.info("text: " + (p \ "message").text)
//        }
//      }

      ElasticSearch.index(name, post)
    }
    l.debug("End: index tribune %s" format name)
  }

  // From: initial (eq. to last)
  // To:
  // Size: maximum size of backend, in number of posts
  def backend(from:Int=0, to:Option[Int]=None, size:Int=50): List[Post] = {
    l.debug("Entering backend for %s" format name)
//    l.info("==> %s %s %s".format(from, to, size))
    val q = to match {
      case Some(n) => QueryBuilders.rangeQuery("id").from(from).to(n)
      case None => QueryBuilders.rangeQuery("id").from(from)
    }

    val response: SearchResponse = ElasticSearch.query(name, q, size)
    val t = for (h: SearchHit <- response.hits().hits()) yield {
      val id = h.field("id").getValue[Int]
      val time = h.field("time").getValue[String]
      val info = h.field("info").getValue[String]
      val login = h.field("login").getValue[String]
      val message = h.field("message").getValue[String]
      Post(name, id, time, info, login, message)
    }
    t.toList
  }



  def backend_json(from:Int=0, to:Option[Int]=None, size:Int=50): String = {

    compact(render((("board" ->
      ("site" -> name)) ~
      ("posts" -> backend(from,to,size).map { p =>
        (("id" -> p.id))}))))
  }

  def backend_xml(from:Int=0, to:Option[Int]=None, size:Int=50): Elem = {
    <board site={name}>
      {
      backend(from,to,size).map { p =>
        p.to_xml
      }
    }
    </board>
  }

  def backend_tsv(from:Int=0, to:Option[Int]=None, size:Int=50): String = {
    "id\ttime\tinfo\tlogin\tmessage\n" + backend(from,to,size).reverse.map(_.to_tsv).mkString("")
  }

  def search(query:String, from:Int=0, to:Option[Int]=None, size:Int=50): List[Post] = {
    l.debug("Entering search for %s".format(name))
    val q = QueryBuilders.queryString(query)

    val response: SearchResponse = ElasticSearch.query(name, q, size)
    val t = for (h: SearchHit <- response.hits().hits()) yield {
      val id = h.field("id").getValue[Int]
      val time = h.field("time").getValue[String]
      val info = h.field("info").getValue[String]
      val login = h.field("login").getValue[String]
      val message = h.field("message").getValue[String]
      Post(name, id, time, info, login, message)
    }
    t.toList
  }

  def search_json(q:String, from:Int=0, to:Option[Int]=None, size:Int=50): String = {

    compact(render((("board" ->
      ("site" -> name)) ~
      ("posts" -> search(q,from,to,size).map { p =>
        (("id" -> p.id))}))))
  }

  def search_xml(q:String, from:Int=0, to:Option[Int]=None, size:Int=50): Elem = {
    <board site={name}>
      {
      search(q,from,to,size).map { p =>
        p.to_xml
      }
      }
    </board>
  }

  def search_tsv(q:String, from:Int=0, to:Option[Int]=None, size:Int=50): String = {
    "id\ttime\tinfo\tlogin\tmessage\n" + search(q,from,to,size).reverse.map(_.to_tsv).mkString("")
  }

  def post(cookies: Map[String, String], ua: String, content: String): String = {
    val c = content.replace("#{plus}#","+").
      replace("#{amp}#","&").
      replace("#{dcomma}#",";").
      replace("#{percent}#","%")

    val headers = Headers(
      USER_AGENT -> ua,
      REFERER -> post_url
    )

    // Create a new trust manager that trusts all certificates
    val trustAllCerts = Array[TrustManager](new DumbTrustManager)

    // Activate the new trust manager
    val sc = SSLContext.getInstance("SSL")
    sc.init(Array[KeyManager](), trustAllCerts, new SecureRandom())

    val config = Config(followRedirects = false,
      keepAlive = false,
      userAgentString = Some("olccs-scala"),
      sslSocketFactory = Some(sc.getSocketFactory),
      hostnameVerifier = Some(new DumbHostnameVerifier))

    val domain = new java.net.URL(post_url).getHost
    val cj: CookieJar = CookieJar(cookies.map(x => Cookie(x._1,x._2, domain)).toList)

    val http_client = new HttpClient(config)

    l.debug("post_parameter => %s".format(post_parameter))
    val requestBody = RequestBody(Map(post_parameter -> c))
    val response = http_client.post(post_url, Some(requestBody), headers, cj)
    index
    response.headers.get(HeaderName("X-Post-Id")) match {
      case Some(h) => h.toString()
      case None => ""
    }

//    println(r.status)

    //l.info(cookies.toString())
  }

  def login(login: String, password:String): Map[String,String] = {
    val config = Config(followRedirects = false, keepAlive = false)
    val http_client = new HttpClient(config)
    val cj = CookieJar()
    l.info("" + login + " " + password)
    val requestBody = RequestBody(Map(login_parameter -> login,password_parameter -> password, "form_id" -> "user_login_block"))
    val cookies = http_client.post(login_url, Some(requestBody), Headers(), cj).cookies
    l.info(cookies.toString)
    cookies match {
      case Some(cj) => cj.cookies.map(c => (c.name, c.value)).toMap
      case None => Map()
    }
  }
}

object Board {
//  var boards: ArrayBuffer[Board] = new ArrayBuffer[Board]()
  var boards = new scala.collection.mutable.HashMap[String, Board]()
  def apply(name: String,
    get_url: String,
    lastid_parameter: String,
    slip_type: Slip.Value,
    post_url: String,
    post_parameter: String,
    login_url: String,
    cookie_name: String,
    login_parameter: String,
    password_parameter: String) = {
      val b = new Board(name, get_url, lastid_parameter, slip_type, post_url, post_parameter, login_url, cookie_name, login_parameter, password_parameter)
      boards += (name -> b)
      b
    }
}

//class DumbTrustManager extends X509TrustManager {
//  def getAcceptedIssuers: Array[X509Certificate] = null
//  def checkClientTrusted(p1: Array[cert.X509Certificate], p2: String) {}
//  def checkServerTrusted(p1: Array[cert.X509Certificate], p2: String) {}
//}
//
//class DumbHostnameVerifier extends HostnameVerifier {
//  def verify(p1: String, p2: SSLSession) = true
//}

/** Don't use this in production code!!! */
class DumbTrustManager extends X509TrustManager {
  def getAcceptedIssuers: Array[java.security.cert.X509Certificate] = null
  def checkClientTrusted(certs: Array[X509Certificate], authType: String) {}
  def checkServerTrusted(certs: Array[X509Certificate], authType: String) {}
}

/** Don't use this in production code!!! */
class DumbHostnameVerifier extends HostnameVerifier {
  def verify(p1: String, p2: SSLSession) = true
}