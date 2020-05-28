package repositories

import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.free.connection
import cats.effect.{ContextShift, IO}
import cats.syntax.applicative._
import cats.syntax.flatMap._

import scala.io.Source
import com.google.inject.Inject
import doobie.util.fragment.Fragment
import play.api.Configuration
import play.api.libs.json.Json
import models.{Contact, DbContactData}
import exceptions.InvalidInputException
import java.io.PrintWriter

import akka.actor.ActorSystem
import cache.ContactCache

trait PhonebookRepository {

  def addContact(contact: Contact): IO[Int]

  def getContacts: IO[List[Contact]]

  def changeContactInfo(id: Int, contact: Contact): IO[Int]

  def deleteContact(id: Int): IO[Int]

  def findByName(name: String): IO[List[Contact]]

  def findByPhone(phone: String): IO[List[Contact]]

  def saveDbData: IO[Unit]

  def deleteOldData: IO[Int]
}

class PhonebookRepositoryImpl @Inject()(configuration: Configuration,
                                        contactCache: ContactCache)
    extends PhonebookRepository {

  def addContact(contact: Contact): IO[Int] =
    (for {
      _ <- isNameAlreadyExist(None, contact.name)
      _ <- isPhoneAlreadyExist(None, contact.phoneNumber)
      id <- sql"insert into phone_book (name, phone) values (${contact.name}, ${contact.phoneNumber}) returning id"
        .query[Int]
        .unique
    } yield id)
      .transact(transactor)
      .flatMap(
        id =>
          contactCache
            .addContact(
              id,
              Contact(Option(id), contact.name, contact.phoneNumber)
          )
      )

  def getContacts: IO[List[Contact]] =
    for {
      contacts <- isCacheValid.ifM(
        contactCache.getAllContacts,
        for {
          res <- sql"select id, name, phone from phone_book where not deleted order by name"
            .query[Contact]
            .to[List]
            .transact(transactor)
          _ <- res
            .foreach(
              contact => contactCache.addContact(contact.id.get, contact)
            )
            .pure[IO]
        } yield res
      )
    } yield contacts

  def changeContactInfo(id: Int, contact: Contact): IO[Int] =
    (for {
      _ <- deleted(id)
      _ <- isNameAlreadyExist(Option(id), contact.name)
      _ <- isPhoneAlreadyExist(Option(id), contact.phoneNumber)
      _ <- noChanges(id, contact)
      id <- sql"update phone_book set name = ${contact.name}, phone = ${contact.phoneNumber} where id = $id".update.run
    } yield id)
      .transact(transactor)
      .flatMap(_ => contactCache.updateContact(id, contact))

  def deleteContact(id: Int): IO[Int] =
    (for {
      _ <- deleted(id)
      id <- sql"update phone_book set deleted = true where id = $id".update.run
    } yield
      id).transact(transactor).flatMap(_ => contactCache.removeContact(id))

  def findByName(name: String): IO[List[Contact]] =
    for {
      contacts <- isCacheValid.ifM(
        contactCache.findByName(name.dropRight(1).drop(1)),
        (for {
          _ <- isPhoneBookEmpty
          res <- sql"select id, name, phone from phone_book where lower(name) like $name and not deleted"
            .query[Contact]
            .to[List]
        } yield res).transact(transactor)
      )
      _ <- IO
        .raiseError(InvalidInputException("No such contacts found"))
        .whenA(contacts.isEmpty)
    } yield contacts

  def findByPhone(phone: String): IO[List[Contact]] =
    for {
      contacts <- isCacheValid.ifM(
        contactCache.findByPhone(phone.dropRight(1).drop(1)),
        (for {
          _ <- isPhoneBookEmpty
          res <- sql"select id, name, phone from phone_book where lower(phone) like $phone and not deleted"
            .query[Contact]
            .to[List]
        } yield res).transact(transactor)
      )
      _ <- IO
        .raiseError(InvalidInputException("No such contacts found"))
        .whenA(contacts.isEmpty)
    } yield contacts

  def saveDbData: IO[Unit] =
    for {
      data <- sql"select * from phone_book"
        .query[DbContactData]
        .to[List]
        .transact(transactor)
      writer <- new PrintWriter("dbContactsData.json").pure[IO]
      _ <- writer
        .write(Json.toJson(data).toString)
        .pure[IO]
      _ <- writer.close.pure[IO]
    } yield ()

  def deleteOldData: IO[Int] =
    sql"update phone_book set deleted = true where date < (now() - interval '365 days');".update.run
      .transact(transactor)

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

  private def deleted(id: Int): ConnectionIO[Unit] =
    for {
      deleted <- sql"select deleted from phone_book where id = $id"
        .query[Boolean]
        .unique
      _ <- connection
        .raiseError(InvalidInputException(s"Contact was deleted earlier."))
        .whenA(deleted)
    } yield ()

  private def noChanges(id: Int, contact: Contact): ConnectionIO[Unit] =
    for {
      noChanges <- sql"select name = ${contact.name} and phone = ${contact.phoneNumber} from phone_book where id = $id and not deleted"
        .query[Boolean]
        .unique
      _ <- connection
        .raiseError(InvalidInputException(s"No changes for ${contact.name}."))
        .whenA(noChanges)
    } yield ()

  private def isCacheValid: IO[Boolean] =
    sql"select count (*) from phone_book where not deleted"
      .query[Int]
      .unique
      .transact(transactor)
      .flatMap(count => (count == contactCache.contactCacheSize).pure[IO])

  val system = ActorSystem("naumen-test")

  private val dbExecutionContext = system.dispatcher

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
      .fromFile("app/resources/db.sql")
      .mkString
      .pure[IO]
      .flatMap(
        Fragment(_, Nil).update.run
          .transact(transactor)
      )
      .unsafeRunAsyncAndForget()
}
