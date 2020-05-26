package services

import models.Contact
import cats.effect.IO
import com.google.inject.Inject
import repositories.PhonebookRepositoryImpl
import scala.concurrent.Future
import exceptions.InvalidInputException

trait PhonebookServise {
  def addContact(contact: Contact): Future[Either[Throwable, Int]]

  def getContacts: Future[Either[Throwable, List[Contact]]]

  def changeContactInfo(contact: Contact): Future[Either[Throwable, Int]]

  def deleteContact(contact: Contact): Future[Either[Throwable, Int]]

  def findByName(name: String): Future[Either[Throwable, List[Contact]]]

  def findByPhone(phone: String): Future[Either[Throwable, List[Contact]]]
}

class PhonebookServiceImpl @Inject()(repository: PhonebookRepositoryImpl)
    extends PhonebookServise {
  def addContact(contact: Contact): Future[Either[Throwable, Int]] =
    repository.addContact(contact).attempt.unsafeToFuture()

  def getContacts: Future[Either[Throwable, List[Contact]]] =
    repository.getContacts.attempt.unsafeToFuture()

  def changeContactInfo(contact: Contact): Future[Either[Throwable, Int]] =
    (contact.id match {
      case None     => IO.raiseError(InvalidInputException("Id not found"))
      case Some(id) => repository.changeContactInfo(id, contact)
    }).attempt.unsafeToFuture()

  def deleteContact(contact: Contact): Future[Either[Throwable, Int]] =
    (contact.id match {
      case None     => IO.raiseError(InvalidInputException("Id not found"))
      case Some(id) => repository.deleteContact(id, contact)
    }).attempt.unsafeToFuture()

  def findByName(name: String): Future[Either[Throwable, List[Contact]]] =
    repository.findByName(filter(name)).attempt.unsafeToFuture()

  def findByPhone(phone: String): Future[Either[Throwable, List[Contact]]] =
    repository.findByPhone(filter(phone)).attempt.unsafeToFuture()

  private def filter(filter: String): String = "%" + filter + "%"
}
