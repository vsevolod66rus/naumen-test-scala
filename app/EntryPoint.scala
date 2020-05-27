import cache.ContactCache
import filters.{CorsFilter, LoggingFilter}
import play.api._
import play.api.mvc._
import play.filters.gzip._
import play.api.routing.Router
import repositories.PhonebookRepositoryImpl
import services.PhonebookServiceImpl
import scheduler.DeleteOldDataScheduler

class EntryPoint extends ApplicationLoader {

  private var components: MyComponents = _

  def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    components = new MyComponents(context)

    val scheduler =
      new DeleteOldDataScheduler(components.repositoryImpl).start()

    components.application
  }
}

class MyComponents(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with play.filters.HttpFiltersComponents
    with GzipFilterComponents
    with _root_.controllers.AssetsComponents {

  lazy val loggingFilter: LoggingFilter = new LoggingFilter()

  lazy val corsFilter: CorsFilter = new CorsFilter()

  override lazy val httpFilters = Seq(gzipFilter, loggingFilter, corsFilter)

  lazy val bodyParser = PlayBodyParsers()

  lazy val defaultParser = new BodyParsers.Default(parse)

  lazy val contactCacheImpl = new ContactCache

  lazy val repositoryImpl =
    new PhonebookRepositoryImpl(configuration, contactCacheImpl)

  lazy val phonebookController = new _root_.controllers.PhonebookController(
    controllerComponents,
    new PhonebookServiceImpl(repositoryImpl)
  )

  lazy val router: Router =
    new _root_.router.Routes(httpErrorHandler, phonebookController)
}
