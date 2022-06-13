package org.helgi.tetris.api.game

import akka.NotUsed
import akka.actor.*
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes.*
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.*
import akka.http.scaladsl.server.Directives.*
import akka.stream.*
import akka.stream.scaladsl.*
import org.helgi.tetris.api.*
import org.helgi.tetris.config.JwtConfig
import org.helgi.tetris.game.*
import org.helgi.tetris.game.Pos.Pos
import org.helgi.tetris.model.User
import org.helgi.tetris.repository.{GameRepoActor, GameResultRepository, UserRepository}
import org.helgi.tetris.util.JwtDirectives.*
import spray.json.{RootJsonFormat, *}

import java.time.{Duration, Instant}
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

enum GameProtocol:
  case GameStateMsg(gd: GameData)
  case GameOver
  case GameFailure(t: Throwable)

trait UserJsonSupport extends SprayJsonSupport with DefaultJsonProtocol :
  implicit val tetrominoFormat: RootJsonFormat[TetrominoType] = new RootJsonFormat[TetrominoType] :
    def write(obj: TetrominoType): JsValue = JsString(obj.toString)

    def read(json: JsValue): TetrominoType = {
      json match {
        case JsString(txt) => TetrominoType.valueOf(txt)
        case somethingElse => throw DeserializationException(s"Expected a value from enum instead of $somethingElse")
      }
    }
  implicit val gameStatusFormat: RootJsonFormat[GameStatus] = new RootJsonFormat[GameStatus] :
    def write(obj: GameStatus): JsValue = JsString(obj.toString)

    def read(json: JsValue): GameStatus = {
      json match {
        case JsString(txt) => GameStatus.valueOf(txt)
        case somethingElse => throw DeserializationException(s"Expected a value from enum instead of $somethingElse")
      }
    }

  implicit val tetrisPieceFormat: RootJsonFormat[Piece] = new RootJsonFormat[Piece] :
    private val readFormat = jsonFormat(Piece.apply, "pos", "kind", "theta")

    override def read(json: JsValue): Piece = readFormat.read(json)


    override def write(obj: Piece): JsValue = JsObject(("pos", obj.pos.toJson), ("kind", obj.kind.toJson),
      ("theta", obj.theta.toJson), ("blocks", obj.blocks.toJson))
  implicit val gameStateFormat: RootJsonFormat[GameState] = jsonFormat8(GameState.apply)
  implicit val gameDataFormat: RootJsonFormat[GameData] = jsonFormat3(GameData.apply)


trait GameApi extends UserJsonSupport :

  val system: ActorSystem

  implicit val executionContext: ExecutionContextExecutor

  val jwtConfig: JwtConfig

  val gameRepo: GameResultRepository

  implicit private val mat: Materializer = Materializer(system)

  private val gameRepoActor = system.actorOf(GameRepoActor.props(gameRepo))
  private val gameSessions = scala.collection.concurrent.TrieMap[String, GameSessionRef]()

  system.scheduler.scheduleAtFixedRate(1.days, 1.days) { () => clearOldGameSessions() }

  def gameRoutes: Route = pathPrefix("game") {
    path("create") {
      pathEndOrSingleSlash {
        authenticateOpt(jwtConfig.secret) { optUserId =>
          post {
            val gameSessionActor = system.actorOf(GameSessionActor.props(optUserId,
              GameOptions(), gameRepoActor))
            val sessionId = UUID.randomUUID().toString
            gameSessions += (sessionId -> GameSessionRef(Instant.now(), gameSessionActor))
            complete(Created, sessionId)
          }
        }
      }
    } ~
      path("connect" / Segment) { sessionId =>
        get {
          val gameSession = gameSessions(sessionId)
          handleWebSocketMessages(gameMessageFlow(gameSession.actor))
        }
      }
  }

  private def gameMessageFlow(sessionRef: ActorRef): Flow[Message, TextMessage, NotUsed] =
    val (broker, source) = Source.actorRefWithBackpressure[GameProtocol]("",
      { case GameProtocol.GameOver => CompletionStrategy.immediately },
      { case GameProtocol.GameFailure(t) => t })
      .map {
        case GameProtocol.GameStateMsg(gd) => TextMessage(gd.toJson.prettyPrint)
        case _ => TextMessage("Invalid Error")
      }
      .preMaterialize()

    sessionRef ! GameSessionMessage.Start(broker)

    val sink = Flow[Message].collect {
      case TextMessage.Strict(text) =>
        sessionRef.tell(GameCommand.valueOf(text), broker)
    }.to(Sink.ignore)

    Flow.fromSinkAndSource(sink, source)

  private def clearOldGameSessions(): Unit =
    val sessionIds = gameSessions
      .filter(_._2.createdAt.isBefore(Instant.now().minus(Duration.ofDays(1))))
      .map { (id, session) =>
        system.stop(session.actor)
        id
      }
    gameSessions --= sessionIds

  private case class GameSessionRef(createdAt: Instant, actor: ActorRef)
