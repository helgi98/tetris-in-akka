package org.helgi.tetris.util

import scala.concurrent.duration.{DurationLong, FiniteDuration}

object Conversions:

  given javaToScalaDuration: Conversion[java.time.Duration, FiniteDuration] = _.toNanos.nanos
