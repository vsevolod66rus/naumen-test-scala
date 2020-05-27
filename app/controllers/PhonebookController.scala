package controllers

import models.{Contact, UserFilter}
import javax.inject._
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.mvc._
import play.api.data.Forms._
import play.api.libs.json.Json
import exceptions.InvalidInputException

import scala.concurrent.{ExecutionContext, Future}
import services.PhonebookServiceImpl

class PhonebookController @Inject()(cc: ControllerComponents,
                                    service: PhonebookServiceImpl)
    extends AbstractController(cc) {

  def addContact = Action.async { implicit req =>
    println(req.body)
    validateContactForm(peopleForm.bindFromRequest)(
      postActionWithContact(service.addContact)(_, "was added successfully")
    )
  }

  def changeContact = Action.async { implicit req =>
    println(req.body)
    validateContactForm(peopleForm.bindFromRequest)(
      postActionWithContact(service.changeContactInfo)(
        _,
        "was changed successfully"
      )
    )
  }

  def deleteContact = Action.async { implicit req =>
    println(req.body)
    validateContactForm(peopleForm.bindFromRequest)(
      postActionWithContact(service.deleteContact)(
        _,
        "was deleted successfully"
      )
    )
  }

  def getContacts = Action.async { implicit request: Request[AnyContent] =>
    getAction(service.getContacts)
  }

  def findByName = Action.async { implicit req =>
    println(req.body)
    validateUserFilterForm(userFilterForm.bindFromRequest)(
      getActionWithForm(service.findByName)(_)
    )
  }

  def findByNameParam(filter: String) = Action.async {
    Future.successful(Ok(s"aaa $filter"))
  }

  def findByPhone = Action.async { implicit req =>
    println(req.body)
    validateUserFilterForm(userFilterForm.bindFromRequest)(
      getActionWithForm(service.findByPhone)(_)
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
      validateUserFilter(_) match {
        case Left(err) =>
          Future.successful(BadRequest(err.getMessage))
        case Right(filter) => fa(filter)
      }
    )
  }

  implicit val cs = ExecutionContext.global

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

  def validateUserFilter(
    userFilter: UserFilter
  ): Either[Throwable, UserFilter] = {
    if (userFilter.filter.length < 1)
      Left(InvalidInputException("Сannot be empty"))
    else Right(userFilter)
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
}
