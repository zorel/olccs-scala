package org.zorel.olccs.elasticsearch


import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.{Node, NodeBuilder}
import org.elasticsearch.client.Client
import org.elasticsearch.action.index.IndexRequest.OpType
import org.elasticsearch.index.engine.DocumentAlreadyExistsException
import org.elasticsearch.common.xcontent.{XContentBuilder, ToXContent, XContentFactory}

import org.slf4j.LoggerFactory
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.JsonAST.JValue

import org.zorel.olccs.models.Post
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.search.sort.SortOrder


object ElasticSearch {

  // ClientPool initialise (NodeBuilder.nodeBuilder().clusterName("twitter").client(true).node)
  val l = LoggerFactory.getLogger(getClass)

  //  def init(hosts: String) {
  val settings = ImmutableSettings.settingsBuilder()
  settings.put("client.transport.sniff", true)
  settings.put("discovery.zen.ping.multicast.enabled", false)
  settings.put("discovery.zen.ping.unicast.hosts", "127.0.0.1")

  val node: Node = NodeBuilder.nodeBuilder().settings(settings.build).clusterName("olccs").client(true).node
  val client: Client = node.client
  //  }

  implicit val formats = DefaultFormats

  def index(index: String, post: Post) {
    val json = post.to_json
    val id = post.id
    try {
      client.
        prepareIndex(index, "post", id.toString).
        setSource(compact(render(json))).
        setOpType(OpType.CREATE).
        setRefresh(true).
        execute.
        actionGet()
    } catch {
      case ex: DocumentAlreadyExistsException => l.debug("Document dupliqué id " + post.id + " pour tribune " + post.board)
    }
  }

  def query(index: String, q: QueryBuilder, size:Int=50): SearchResponse = {
    val r = client.
      prepareSearch(index).
      addFields("id","board","time","info","login","message").
      setTypes("post").
      setQuery(q).
      addSort("id", SortOrder.DESC).
      setSize(size)

//    l.info(r.toString)
    r.execute().
      actionGet()
  }


  //  def query(index: String, q: JValue): SearchResponse = {
//    query(index, compact(render(q)))
//  }

//  "query_string" -> (
//    ("default_field" -> "message") ~
//      ("default_operator" -> "AND") ~
//      ("query" ->
//        )
  def optimize() {
    client.admin().indices().
      prepareOptimize("_all").
      setMaxNumSegments(1).
      execute().
      actionGet()
  }

  def close() {
    client.close()
    node.close()
  }
}