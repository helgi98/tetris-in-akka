package org.helgi.tetris.model

import java.time.Instant


case class GameResult(id: Long, userId: Long, score: Int, linesCleared: Int, lvl: Int,
                      startedAt: Instant, finishedAt: Instant)