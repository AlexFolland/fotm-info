package controllers

import javax.inject._

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.github.nscala_time.time.Imports
import info.fotm.aether.{AetherConfig, Storage}
import info.fotm.domain.Axis
import com.github.nscala_time.time.Imports._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, SECONDS}

@Singleton
class Application @Inject()(system: ActorSystem) extends Controller {

  Logger.info(">>> Storage path: " + AetherConfig.storagePath)
  Logger.info(">>> Proxy path: " + AetherConfig.storageProxyPath)

  implicit val timeout: Timeout = new Timeout(Duration(30, SECONDS))

  val period = 30.minutes

  // init proxy and subscribe to storage updates
  lazy val storage: ActorSelection = system.actorSelection(AetherConfig.storagePath)
  lazy val storageProxy = system.actorOf(Storage.props, AetherConfig.storageProxyActorName)
  storage.tell(Storage.Identify, storageProxy)

  def healthCheck = Action {
    Ok("OK")
  }

  def index(region: String, bracket: String): Action[AnyContent] = Action.async {
    Axis.parse(region, bracket).fold(Future.successful(NotFound: Result)) { axis =>
      val interval = new Interval(DateTime.now - period, DateTime.now)
      val request = storageProxy ? Storage.QueryState(axis, interval)

      request.mapTo[Storage.QueryStateResponse].map { (response: Storage.QueryStateResponse) =>
        Ok(views.html.index(s"Playing Now ${response.axis}", response.axis, response.teams, response.chars))
      }
    }
  }

}
