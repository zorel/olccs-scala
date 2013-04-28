package org.zorel.olccs

import org.zorel.olccs.models.Board
import javax.net.ssl._
import java.security.cert.X509Certificate
import java.security.SecureRandom
import uk.co.bigbeeconsultants.http._

class OlccsServlet extends OlccsStack {

  get("/") {
    <html>plop</html>
  }

  get ("/totoz") {
    params.get("url") match {
      case Some(u) => {
        contentType = "application/xml"
        val search_url = u.replace("{question}","?").replace(" ", "+")
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
        httpClient.get(search_url).body.asString
      }
      case None => ""
    }
  }

  get("/boards.xml") {
    contentType = "application/xml"
    <sites>{
      Board.boards.map( b =>
        <site name={b._2.name}>
          <module name="board" title="tribune" type="application/board+xml">
            <backend path={b._2.get_url} public="true" refresh="15" tags_encoded="false"/>
            <post anonymous="true" max_length="512" method="post" path={b._2.post_url}>
              <field name={b._2.post_parameter}/>
            </post>
            <login method="post" path={b._2.login_url}>
              <username name={b._2.login_parameter}/>
              <password name="pass"/>
              <remember name=""/>
              <cookie name=""/>
            </login>
          </module>
        </site>
      )}
    </sites>
  }
}


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