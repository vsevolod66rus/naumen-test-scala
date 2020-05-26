package models

import play.api.libs.json._

case class Contact(id: Option[Int], name: String, phoneNumber: String)

object Contact {
  implicit val contactFormat = Json.format[Contact]
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
