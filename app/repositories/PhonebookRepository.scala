package repositories

import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.free.connection
import cats.effect.{ContextShift, IO}
import cats.syntax.applicative._

import scala.concurrent.ExecutionContext
import scala.io.Source
import com.google.inject.Inject

import doobie.util.fragment.Fragment
import play.api.Configuration
import models.Contact
import exceptions.InvalidInputException

trait PhonebookRepository {

  def addContact(contact: Contact): IO[Int]

  def getContacts: IO[List[Contact]]

  def changeContactInfo(id: Int, contact: Contact): IO[Int]

  def deleteContact(id: Int, contact: Contact): IO[Int]

  def findByName(name: String): IO[List[Contact]]

  def findByPhone(phone: String): IO[List[Contact]]
}

class PhonebookRepositoryImpl @Inject()(configuration: Configuration)
    extends PhonebookRepository {

  def addContact(contact: Contact): IO[Int] =
    (for {
      _ <- isNameAlreadyExist(None, contact.name)
      _ <- isPhoneAlreadyExist(None, contact.phoneNumber)
      id <- sql"insert into phone_book (name, phone) values (${contact.name}, ${contact.phoneNumber}) returning id"
        .query[Int]
        .unique
    } yield id).transact(transactor)

  def getContacts: IO[List[Contact]] =
    (for {
      contacts <- sql"select id, name, phone from phone_book where not deleted order by id"
        .query[Contact]
        .to[List]
    } yield contacts).transact(transactor)

  def changeContactInfo(id: Int, contact: Contact): IO[Int] =
    (for {
      _ <- deleted(id, contact.name)
      _ <- isNameAlreadyExist(Option(id), contact.name)
      _ <- isPhoneAlreadyExist(Option(id), contact.phoneNumber)
      _ <- noChanges(id, contact)
      id <- sql"update phone_book set name = ${contact.name}, phone = ${contact.phoneNumber} where id = $id".update.run
    } yield id).transact(transactor)

  def deleteContact(id: Int, contact: Contact): IO[Int] =
    (for {
      _ <- deleted(id, contact.name)
      id <- sql"update phone_book set deleted = true where id = ${contact.id}".update.run
    } yield id).transact(transactor)

  def findByName(name: String): IO[List[Contact]] =
    (for {
      _ <- isPhoneBookEmpty
      contacts <- sql"select id, name, phone from phone_book where lower(name) like $name and not deleted"
        .query[Contact]
        .to[List]
      _ <- connection
        .raiseError(InvalidInputException("No such contacts found"))
        .whenA(contacts.isEmpty)
    } yield contacts).transact(transactor)

  def findByPhone(phone: String): IO[List[Contact]] =
    (for {
      _ <- isPhoneBookEmpty
      contacts <- sql"select id, name, phone from phone_book where lower(phone) like $phone and not deleted"
        .query[Contact]
        .to[List]
      _ <- connection
        .raiseError(InvalidInputException("No such contacts found"))
        .whenA(contacts.isEmpty)
    } yield contacts).transact(transactor)

  private def isPhoneBookEmpty: ConnectionIO[Unit] =
    for {
      bookIsEmpty <- sql"""select count (*) = 0 from phone_book where not deleted"""
        .query[Boolean]
        .unique
      _ <- connection
        .raiseError(InvalidInputException("Phone book is Empty."))
        .whenA(bookIsEmpty)
    } yield ()

  private def isNameAlreadyExist(id: Option[Int],
                                 name: String): ConnectionIO[Unit] =
    id match {
      case None =>
        for {
          alreadyExistName <- sql"""select count (*) > 0 from phone_book where name = $name and not deleted"""
            .query[Boolean]
            .unique
          _ <- connection
            .raiseError(
              InvalidInputException(s"Contact with name $name already exists.")
            )
            .whenA(alreadyExistName)
        } yield ()
      case Some(id) =>
        for {
          alreadyExistName <- sql"""select count (*) > 0 from phone_book where name = $name and id != $id and not deleted"""
            .query[Boolean]
            .unique
          _ <- connection
            .raiseError(
              InvalidInputException(s"Contact with name $name already exists.")
            )
            .whenA(alreadyExistName)
        } yield ()
    }

  private def isPhoneAlreadyExist(id: Option[Int],
                                  phoneNumber: String): ConnectionIO[Unit] =
    id match {
      case None =>
        for {
          alreadyExistPhone <- sql"""select count (*) > 0 from phone_book where phone = $phoneNumber and not deleted"""
            .query[Boolean]
            .unique
          _ <- connection
            .raiseError(
              InvalidInputException(
                s"Contact with phone $phoneNumber already exists."
              )
            )
            .whenA(alreadyExistPhone)
        } yield ()
      case Some(id) =>
        for {
          alreadyExistPhone <- sql"""select count (*) > 0 from phone_book where phone = $phoneNumber and id != $id and not deleted"""
            .query[Boolean]
            .unique
          _ <- connection
            .raiseError(
              InvalidInputException(
                s"Contact with phone $phoneNumber already exists."
              )
            )
            .whenA(alreadyExistPhone)
        } yield ()
    }

  private def deleted(id: Int, name: String): ConnectionIO[Unit] =
    for {
      deleted <- sql"select deleted from phone_book where id = $id"
        .query[Boolean]
        .unique
      _ <- connection
        .raiseError(InvalidInputException(s"$name was already deleted."))
        .whenA(deleted)
    } yield ()

  private def noChanges(id: Int, contact: Contact): ConnectionIO[Unit] =
    for {
      noChanges <- sql"select name = ${contact.name} and phone = ${contact.phoneNumber} from phone_book where id = $id"
        .query[Boolean]
        .unique
      _ <- connection
        .raiseError(InvalidInputException(s"No changes for ${contact.name}."))
        .whenA(noChanges)
    } yield ()

  private val dbExecutionContext = ExecutionContext.global

  implicit val contextShift: ContextShift[IO] =
    IO.contextShift(dbExecutionContext)

  private val driver = configuration.get[String]("db.default.driver")

  private val url = configuration.get[String]("db.default.url")

  private val username = configuration.get[String]("db.default.username")

  private val password = configuration.get[String]("db.default.password")

  val transactor: Transactor[IO] = Transactor
    .fromDriverManager(driver, url, username, password)

  private val createTable: Unit =
    Source
      .fromFile("app/resouses/db.sql")
      .mkString
      .pure[IO]
      .flatMap(
        Fragment(_, Nil).update.run
          .transact(transactor)
      )
      .unsafeRunAsyncAndForget()
}
