package com.bdesigns.akka.json

import org.json4s.jackson.Serialization
import org.json4s.{DefaultFormats, Formats}

trait Json4sFormat {
  implicit val serialization: Serialization.type = Serialization
  implicit val json4sFormats: Formats = DefaultFormats
}

object Json4sFormat extends Json4sFormat
