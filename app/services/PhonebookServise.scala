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

  def deleteContact(id: Int): Future[Either[Throwable, Int]]

  def findByName(name: String): Future[Either[Throwable, List[Contact]]]

  def findByPhone(phone: String): Future[Either[Throwable, List[Contact]]]

  def saveDbData: Future[Either[Throwable, Unit]]
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

  def deleteContact(id: Int): Future[Either[Throwable, Int]] =
    repository.deleteContact(id).attempt.unsafeToFuture()

  def findByName(name: String): Future[Either[Throwable, List[Contact]]] =
    repository.findByName(filter(name)).attempt.unsafeToFuture()

  def findByPhone(phone: String): Future[Either[Throwable, List[Contact]]] =
    repository.findByPhone(filter(phone)).attempt.unsafeToFuture()

  def saveDbData: Future[Either[Throwable, Unit]] =
    repository.saveDbData.attempt.unsafeToFuture()

  private def filter(filter: String): String = "%" + filter + "%"
}
