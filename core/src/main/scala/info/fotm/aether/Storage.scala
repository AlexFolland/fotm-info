package info.fotm.aether

import akka.actor.{ActorRef, Actor}
import akka.event.{Logging, LoggingReceive}
import info.fotm.domain._

object Storage {
  // input
  final case class Updates(axis: Axis, teamUpdates: Seq[TeamUpdate])

  // output
  final case class GetTeamLadder(axis: Axis)
  final case class TeamLadderResponse(teamLadder: TeamLadder)

  // reactive, subscribes/unsubscribes sender to updates
  case object Subscribe
  case object Unsubscribe
}

class Storage extends Actor {
  import Storage._

  val log = Logging(context.system, this.getClass)
  // TODO: save/load state on init

  override def receive: Receive = process(Map.empty, Set.empty)

  def process(ladders: Map[Axis, Map[Team, TeamSnapshot]], subs: Set[ActorRef]): Receive = LoggingReceive {

    case Updates(axis, teamUpdates) =>
      log.debug("Updates received. Processing...")
      val axisLadder: Map[Team, TeamSnapshot] = ladders.getOrElse(axis, Map.empty)

      val updatedSnapshots = for {
        update <- teamUpdates
        team = Team(update.view.snapshots.map(_.id))
        snapshotOption = axisLadder.get(team)
      } yield {
          val snapshot = snapshotOption.fold { TeamSnapshot(update.view.snapshots) } {
            _.copy(view = update.view)
          }
          if (update.won) snapshot.copy(stats = snapshot.stats.win)
          else snapshot.copy(stats = snapshot.stats.loss)
        }

      val newAxisLadder = axisLadder ++ updatedSnapshots.map(ts => (ts.team, ts))
      context.become( process(ladders.updated(axis, newAxisLadder), subs) )

      for (sub <- subs)
        sub ! Updates(axis, teamUpdates)

    case GetTeamLadder(axis: Axis) =>
      ladders.get(axis).fold {
        sender ! TeamLadderResponse(TeamLadder(axis, Map.empty))
      } { ladder =>
        sender ! TeamLadderResponse(TeamLadder(axis, ladder))
      }

    case Subscribe =>
      context.become( process(ladders, subs + sender) )

    case Unsubscribe =>
      context.become( process(ladders, subs - sender) )
  }

}