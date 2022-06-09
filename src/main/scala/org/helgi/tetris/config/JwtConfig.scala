package org.helgi.tetris.config

import scala.concurrent.duration.FiniteDuration

case class JwtConfig(secret: String, expirationTime: FiniteDuration)
