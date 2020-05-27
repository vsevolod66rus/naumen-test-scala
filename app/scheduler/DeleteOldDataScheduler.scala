package scheduler

import java.time.LocalTime
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.actor.Cancellable
import javax.inject.Inject
import repositories.PhonebookRepository
import play.api.Logger

class DeleteOldDataScheduler @Inject()(repository: PhonebookRepository) {

  val system = ActorSystem("naumen-test")

  val logger = Logger.apply("Scheduler logger")

  implicit val ec = system.dispatcher

  def start(): Cancellable =
    system.scheduler
      .scheduleWithFixedDelay(getDelay(LocalTime.of(0, 0)), 24.hours) { () =>
        repository.deleteOldData.attempt.unsafeRunAsync({
          case Left(err) =>
            logger.info(s"Scheduler error $err")
          case Right(_) =>
            logger.info("Old db data was deleted")
        })
      }

  def getDelay(time: LocalTime): FiniteDuration = {
    val DaySeconds = 86400
    val timeSeconds = time.toSecondOfDay
    val now = LocalTime.now().toSecondOfDay
    val difference = timeSeconds - now
    if (difference < 0) {
      (DaySeconds + difference).seconds
    } else {
      (timeSeconds - now).seconds
    }
  }
}
