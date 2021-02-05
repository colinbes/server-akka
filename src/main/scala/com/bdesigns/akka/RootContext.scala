package com.bdesigns.akka

trait RootContext {
  lazy val getRootContext: String = {
    import com.typesafe.config._
    val conf = ConfigFactory.load()
    val contextPath = conf.getString("akka.http.server.root-path")
    contextPath
  }
}
