package org.helgi.tetris.repository

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import doobie.implicits.*
import doobie.util.fragments.*
import doobie.util.transactor.Transactor

import scala.concurrent.Future

case class User(id: Long, username: String, displayName: String, email: String, password: String)

trait UserRepository:

  def createUser(user: User): Future[User]

  def findUserById(id: Long): Future[Option[User]]

  def findUserByEmail(email: String): Future[Option[User]]

  def findUserByUsername(username: String): Future[Option[User]]

object UserRepository:
  def apply(ta: Transactor[IO]): UserRepository =
    new UserRepository :
      override def createUser(user: User): Future[User] =
        (for
          _ <- userInsert(user).run
          id <- sql"select lastval()".query[Long].unique
          u <- userSelectById(id).unique
        yield u).transact(ta).unsafeToFuture()

      override def findUserById(id: Long): Future[Option[User]] =
        userSelectById(id).option.transact(ta).unsafeToFuture()

      override def findUserByEmail(email: String): Future[Option[User]] =
        (userSelect ++ whereAnd(byEmail(email))).query[User].option.transact(ta).unsafeToFuture()

      override def findUserByUsername(username: String): Future[Option[User]] =
        (userSelect ++ whereAnd(byUsername(username))).query[User].option.transact(ta).unsafeToFuture()

  def userSelectById(id: Long): doobie.Query0[User] = (userSelect ++ whereAnd(byId(id))).query[User]

  def userSelect: doobie.Fragment = fr"SELECT * FROM user u"

  def byId(id: Long): doobie.Fragment = fr"u.id = $id"

  def byEmail(email: String): doobie.Fragment = fr"u.email = $email"

  def byUsername(username: String): doobie.Fragment = fr"u.username = $username"

  def userInsert(user: User): doobie.Update0 =
    sql"""
         |INSERT INTO user(username, display_name, email, password)
         |VALUE (${user.username}, ${user.displayName}, ${user.email}, ${user.password})
         |""".stripMargin.update