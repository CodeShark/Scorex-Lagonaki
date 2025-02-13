package scorex.block

import java.net.InetSocketAddress
import java.util.logging.Logger

import akka.actor.{Actor, ActorRef}
import scorex.Controller
import scorex.network.NetworkController
import scorex.network.message.{BlockMessage, GetSignaturesMessage}
import scorex.settings.Constants

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


case class Synchronize(peer: InetSocketAddress)

case class NewBlock(block: Block, sender: Option[InetSocketAddress])

case class BlocksDownload(signatures: List[Array[Byte]], peer: InetSocketAddress)

case class BlockchainController(networkController: ActorRef) extends Actor {

  import BlockchainController._

  private var status = Status.Offline

  override def preStart() = {
    context.system.scheduler.schedule(1.second, 2.seconds)(self ! CheckState)
    context.system.scheduler.schedule(500.millis, 1.second)(networkController ! GetMaxChainScore)
  }

  override def receive = {
    case CheckState =>
      status match {
        case Status.Offline =>

        case Status.Syncing =>
          val msg = GetSignaturesMessage(Controller.blockchainStorage.lastSignatures())
          networkController ! NetworkController.SendMessageToBestPeer(msg)

        case Status.Generating =>
          println("Trying to generate new block")
          Constants.ConsensusAlgo.consensusFunctions.generateBlock().foreach { block =>
            self ! NewBlock(block, None)
          }
      }

    case MaxChainScore(scoreOpt) => scoreOpt match {
      case Some(maxScore) =>
        if (maxScore > Controller.blockchainStorage.score) status = Status.Syncing
        else status = Status.Generating

      case None => status = Status.Offline
    }

    case NewBlock(block, remoteOpt) =>
      if (Block.isNewBlockValid(block)) {
        Logger.getGlobal.info(s"New block: $block")
        block.process()
        Controller.blockchainStorage.appendBlock(block)
        val height = Controller.blockchainStorage.height()
        val exceptOf = remoteOpt.toList
        networkController ! NetworkController.BroadcastMessage(BlockMessage(height, block), exceptOf)
      } else {
        Logger.getGlobal.warning(s"Non-valid block: $block from $remoteOpt")
      }

    case GetStatus => sender() ! status

    case a: Any => Logger.getGlobal.warning(s"BlockchainController: got something strange $a")
  }
}

object BlockchainController {

  object Status extends Enumeration {
    val Offline = Value(0)
    val Syncing = Value(1)
    val Generating = Value(2)
  }

  case object CheckState

  case object GetMaxChainScore

  case class MaxChainScore(scoreOpt: Option[BigInt])

  case object GetStatus
}