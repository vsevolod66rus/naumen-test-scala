package controllers

import akka.actor.ActorSystem
import models.{Contact, UserFilter}
import javax.inject._
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.mvc._
import play.api.data.Forms._
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.Future
import services.PhonebookServiceImpl

class PhonebookController @Inject()(cc: ControllerComponents,
                                    service: PhonebookServiceImpl)
    extends AbstractController(cc) {

  val system = ActorSystem("naumen-test")

  implicit val ec = system.dispatcher

  def addContact = Action.async { implicit req =>
    validateContactForm(peopleForm.bindFromRequest)(
      postActionWithContact(service.addContact)(_, "was added successfully")
    )
  }

  def changeContact = Action.async { implicit req =>
    validateContactForm(peopleForm.bindFromRequest)(
      postActionWithContact(service.changeContactInfo)(
        _,
        "was changed successfully"
      )
    )
  }

  def deleteContact(id: Int) = Action.async { implicit req =>
    id match {
      case 0 => Future.successful(BadRequest("Id not found"))
      case _ =>
        service.deleteContact(id).flatMap {
          case Left(e) =>
            Future.successful(BadRequest(e.getMessage))
          case Right(_) =>
            Future.successful(Ok("Contact was deleted successfully"))
        }
    }
  }

  def getContacts = Action.async { implicit request: Request[AnyContent] =>
    getAction(service.getContacts)
  }

  def findByName = Action.async { implicit req =>
    validateUserFilterForm(userFilterForm.bindFromRequest)(
      getActionWithForm(service.findByName)(_)
    )
  }

  def findByPhone = Action.async { implicit req =>
    validateUserFilterForm(userFilterForm.bindFromRequest)(
      getActionWithForm(service.findByPhone)(_)
    )
  }

  def addContactJson = Action.async(parse.json) { req =>
    validateContactJson(req.body)(
      postActionWithContact(service.addContact)(_, "was added successfully")
    )
  }

  def changeContactJson = Action.async(parse.json) { implicit req =>
    validateContactJson(req.body)(
      postActionWithContact(service.addContact)(_, "was changed successfully")
    )
  }

  def options(path: String) = Action.async {
    Future.successful(
      Ok.withHeaders(
        ACCESS_CONTROL_ALLOW_HEADERS -> Seq(
          AUTHORIZATION,
          CONTENT_TYPE,
          "Target-URL"
        ).mkString(",")
      )
    )
  }

  def saveDbData = Action.async { implicit request: Request[AnyContent] =>
    service.saveDbData.flatMap {
      case Left(e) =>
        Future.successful(BadRequest(e.getMessage))
      case Right(_) =>
        Future.successful(Ok("db data was saved on disk"))
    }
  }

  def validateContactJson(contactJs: JsValue)(fa: Contact => Future[Result]) = {
    contactJs
      .validate[Contact]
      .fold(
        _ => {
          Future.successful(BadRequest("Invalid contact data format"))
        },
        Contact.validateContact(_) match {
          case Left(err) =>
            Future.successful(BadRequest(err.getMessage))
          case Right(contact) =>
            fa(contact)
        }
      )
  }

  def validateContactForm(
    contactForm: Form[Contact]
  )(fa: Contact => Future[Result]) = {
    contactForm.fold(
      _ => Future.successful(BadRequest("Cannot be empty")),
      Contact.validateContact(_) match {
        case Left(err) =>
          Future.successful(BadRequest(err.getMessage))
        case Right(contact) =>
          fa(contact)
      }
    )
  }

  def validateUserFilterForm(
    userFilterForm: Form[UserFilter]
  )(fa: UserFilter => Future[Result]) = {
    userFilterForm.fold(
      _ => Future.successful(BadRequest("Сannot be empty")),
      UserFilter.validateUserFilter(_) match {
        case Left(err) =>
          Future.successful(BadRequest(err.getMessage))
        case Right(filter) => fa(filter)
      }
    )
  }

  def postActionWithContact[A](
    f: Contact => Future[Either[Throwable, A]]
  )(contact: Contact, onSuccessMsg: String) = {
    f(contact).flatMap {
      case Left(e) =>
        Future.successful(BadRequest(e.getMessage))
      case Right(_) =>
        Future
          .successful(Ok(s"Contact ${contact.name} $onSuccessMsg"))
    }
  }

  def getActionWithForm(
    f: String => Future[Either[Throwable, List[Contact]]]
  )(userFilter: UserFilter) = {
    f(userFilter.filter.toLowerCase).flatMap {
      case Left(e) =>
        Future.successful(BadRequest(e.getMessage))
      case Right(value) =>
        Future.successful(Ok(Json.toJson(value)))
    }
  }

  def getAction(f: Future[Either[Throwable, List[Contact]]]) = {
    f.flatMap {
      case Left(e) =>
        Future.successful(BadRequest(e.getMessage))
      case Right(value) =>
        Future.successful(Ok(Json.toJson(value)))
    }
  }

  val peopleForm: Form[Contact] = Form {
    mapping(
      "id" -> optional(number),
      "name" -> nonEmptyText,
      "phoneNumber" -> nonEmptyText
    )(Contact.apply)(Contact.unapply)
  }

  val userFilterForm: Form[UserFilter] = Form {
    mapping("filter" -> nonEmptyText)(UserFilter.apply)(UserFilter.unapply)
  }
}
