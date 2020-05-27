package cache

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._
import scala.collection._
import models.Contact
import cats.effect.IO
import cats.syntax.applicative._
import play.api.Logger

class ContactCache {

  val logger = Logger.apply("Cache logger")

  val contactsCache: concurrent.Map[Int, Contact] =
    new ConcurrentHashMap[Int, Contact]().asScala

  def findContact(key: Int): IO[Option[Contact]] = {
    contactsCache.get(key) match {
      case Some(contact) => {
        Some(contact).pure[IO]
      }
      case _ => None.pure[IO]
    }
  }

  def addContact(id: Int, contact: Contact): IO[Int] = {
    contactsCache.putIfAbsent(id, contact)
    logger.info(
      s"cache updated: added a contact ${contact.name} ${contact.phoneNumber}"
    )
    println(contactsCache)
    id.pure[IO]
  }

  def updateContact(id: Int, contact: Contact): IO[Int] = {
    contactsCache
      .get(id)
      .foreach { record =>
        contactsCache.put(
          id,
          record.copy(
            id = contact.id,
            name = contact.name,
            phoneNumber = contact.phoneNumber
          )
        )
      }
    logger.info(
      s"cache updated: changed a contact ${contact.name} ${contact.phoneNumber}"
    )
    println(contactsCache)
    id.pure[IO]
  }

  def removeContact(id: Int): IO[Int] = {
    contactsCache.remove(id)
    println(contactsCache)
    logger.info(s"cache updated: removed a contact with id $id")
    id.pure[IO]
  }

  def getAllContacts: IO[List[Contact]] = {
    logger.info(s"all contacts were taken from the cache")
    contactsCache.values.toList.pure[IO]
  }

  def findByName(filter: String): IO[List[Contact]] = {
    val res: List[Contact] = Nil
    val contacts = contactsCache.values.toList
    logger.info(s"all contacts were found from the cache with filter $filter")
    contacts
      .foldLeft(res)((acc, el) => {
        if (el.name.toLowerCase.contains(filter.toLowerCase)) acc :+ el
        else acc
      })
      .pure[IO]
  }

  def findByPhone(filter: String): IO[List[Contact]] = {
    val res: List[Contact] = Nil
    val contacts = contactsCache.values.toList
    logger.info(s"all contacts were found from the cache with filter $filter")
    contacts
      .foldLeft(res)((acc, el) => {
        if (el.phoneNumber.toLowerCase.contains(filter.toLowerCase)) acc :+ el
        else acc
      })
      .pure[IO]
  }

  def contactCacheSize: Int = contactsCache.size
}
