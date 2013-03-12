package de.debilski.pelita.pelitaci

import com.typesafe.config._

class Settings(config: Config) {
  config.checkValid(ConfigFactory.defaultReference(), "pelita-CI")

  // non-lazy fields, we want all exceptions at construct time
  val pelitaPath = config.getString("pelita-CI.pelitaPath")
  val pelitagame = config.getString("pelita-CI.pelitagame")
}

object DefaultSettings extends Settings(ConfigFactory.load())
