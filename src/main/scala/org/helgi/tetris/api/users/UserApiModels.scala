package org.helgi.tetris.api.users

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}


case class RegistrationData(username: String, displayName: String, email: String, password: String)

case class UserData(username: String, displayName: String, email: String)

case class LoginPassword(login: String, password: String)

case class LoginToken(login: String, token: String)

trait UserJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val registrationDataFormat: RootJsonFormat[RegistrationData] = jsonFormat4(RegistrationData.apply)
  implicit val userDataFormat: RootJsonFormat[UserData] = jsonFormat3(UserData.apply)
  implicit val loginPasswordFormat: RootJsonFormat[LoginPassword] = jsonFormat2(LoginPassword.apply)
  implicit val loginTokenFormat: RootJsonFormat[LoginToken] = jsonFormat2(LoginToken.apply)
}
