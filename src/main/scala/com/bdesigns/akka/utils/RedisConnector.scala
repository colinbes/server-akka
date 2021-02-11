package com.bdesigns.akka.utils

import com.typesafe.config.ConfigFactory

import java.net.URI
import javax.net.ssl.SSLContext

case class RedisConfig(host: String, port: Int, database: Int = 0, timeout: Int = 0, secret: Option[Any] = None, sslContext: Option[SSLContext])

object RedisConnector {
  def getConnectionUri = {
    val uriStr = ConfigFactory.load().getConfig("redis-server").getString("uri")
    URI.create(uriStr)
  }
}
