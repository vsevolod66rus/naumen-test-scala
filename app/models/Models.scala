package models

import exceptions.InvalidInputException
import play.api.libs.json._

case class Contact(id: Option[Int], name: String, phoneNumber: String)

object Contact {
  implicit val contactFormat = Json.format[Contact]

  def validateContact(contact: Contact): Either[Throwable, Contact] = {
    val contactNameFormat = "^([a-zA-Z0-9]+(_|-| )?[a-zA-Z0-9]*)*[a-zA-Z0-9]+$"
    val contactNumberFormat =
      "^\\+?([0-9]+(|-| |.|(|)){0,2}[0-9]*)*[0-9]+$"
    val digitsPhoneNumber = contact.phoneNumber.foldLeft({
      if (contact.phoneNumber.charAt(0) == '+') "+" else ""
    })((acc, el) => {
      el match {
        case '-' | '+' | ' ' | '(' | ')' | '.' => acc
        case _                                 => acc + el
      }
    })
    if (contact.name.length < 5 | contact.name.length > 25)
      Left(InvalidInputException("Name contains from 5 to 25 characters"))
    else if (!contact.name.matches(contactNameFormat))
      Left(
        InvalidInputException(
          "Only english letters, numbers and single \"-\",\" \", \"_\" allowed"
        )
      )
    else if (!contact.phoneNumber.matches(contactNumberFormat))
      Left(InvalidInputException("Invalid number format"))
    else if (digitsPhoneNumber.length < 5 | digitsPhoneNumber.length > 15)
      Left(InvalidInputException("Invalid number length"))
    else Right(Contact(contact.id, contact.name, digitsPhoneNumber))
  }
}

case class UserFilter(filter: String)

object UserFilter {
  implicit val userFilerFormat = Json.format[UserFilter]
}

case class DbContactData(id: Int,
                         name: String,
                         phoneNumber: String,
                         creationDate: String,
                         deleted: Boolean)

object DbContactData {
  implicit val dbContactDataFormat = Json.format[DbContactData]
}
