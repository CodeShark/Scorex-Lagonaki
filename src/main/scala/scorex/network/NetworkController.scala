package scorex.network

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{Actor, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import scorex.Controller
import scorex.block.BlockchainController.GetMaxChainScore
import scorex.block.{BlockchainController, NewBlock}
import scorex.network.message.{Message, _}
import scorex.settings.Settings
import scorex.utils.ScorexLogging

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Random, Try}

//must be singleton
class NetworkController extends Actor with ScorexLogging {

  import NetworkController._

  private implicit val system = context.system

  private val connectedPeers = mutable.Map[InetSocketAddress, PeerData]()
  private val connectingPeers = mutable.Buffer[InetSocketAddress]()

  private def maxPeerScore() = Try(connectedPeers.maxBy(_._2.blockchainScore)._2.blockchainScore).toOption.flatten

  private def maxScoreHandler() = Try(connectedPeers.maxBy(_._2.blockchainScore)._2.handler).toOption

  //todo: a bit stupid workaround, consider more elegant solution for circular linking
  private var blockchainControllerOpt: Option[ActorRef] = None

  IO(Tcp) ! Bind(self, new InetSocketAddress(InetAddress.getByName(Settings.bindAddress), Settings.Port))

  private def updateScore(remote: InetSocketAddress, height: Int, score: BigInt) = {
    val prevBestScore = maxPeerScore().getOrElse(0: BigInt)

    connectedPeers.get(remote).foreach { peerData =>
      connectedPeers.put(remote, peerData.copy(blockchainScore = Some(score)))
      log.info(s"Score updated for $remote: h. $height -- $score")
    }

    if (score > prevBestScore) {
      connectedPeers.foreach { case (_, PeerData(handler, _)) =>
        handler ! PeerConnectionHandler.BestPeer(remote, score > Controller.blockchainStorage.score)
      }
    }
  }

  override def receive = {
    case b@Bound(localAddress) =>
      log.info("Successfully bound to the port " + Settings.Port)
      context.system.scheduler.schedule(200.millis, 3.seconds)(self ! CheckPeers)
      context.system.scheduler.schedule(500.millis, 5.seconds)(self ! AskForPeers)

    case CommandFailed(_: Bind) =>
      log.error("Network port " + Settings.Port + " already in use!")
      context stop self
      Controller.stopAll()

    case CheckPeers =>
      if (connectedPeers.size < Settings.maxConnections) {
        val peer = PeerManager.randomPeer()
        if (!connectedPeers.contains(peer) && !connectingPeers.contains(peer)) {
          connectingPeers += peer
          IO(Tcp) ! Connect(peer) //todo: connection timeout
        }
      }

    case c@Connected(remote, local) =>
      log.info(s"Connected to $remote")
      connectingPeers -= remote
      val connection = sender()
      val handler = context.actorOf(Props(classOf[PeerConnectionHandler], self, connection, remote))
      connection ! Register(handler)
      connectedPeers += remote -> PeerData(handler, None)
      PeerManager.peerConnected(remote)

    case CommandFailed(c: Connect) =>
      log.info("Failed to connect to : " + c.remoteAddress)
      connectedPeers -= c.remoteAddress
      PeerManager.peerDisconnected(c.remoteAddress)

    case CommandFailed(cmd: Tcp.Command) =>
      log.info("Failed to execute command : " + cmd)

    case ShutdownNetwork =>
      log.info("Going to shutdown all connections & unbind port")
      connectedPeers.values.foreach(_.handler ! PeerConnectionHandler.CloseConnection)
      self ! Unbind
      context stop self

    case PeerDisconnected(remote) =>
      connectedPeers -= remote
      PeerManager.peerDisconnected(remote)

    case AskForPeers =>
      val handlers = connectedPeers.values.toList
      if (handlers.nonEmpty) {
        val randomHandler = handlers(Random.nextInt(handlers.size)).handler
        randomHandler ! GetPeersMessage
      }

    case BroadcastMessage(message, exceptOf) =>
      log.info(s"Broadcasting message $message")
      connectedPeers.foreach { case (remote, PeerData(handler, _)) =>
        if (!exceptOf.contains(remote)) handler ! message
      }
      log.info("Broadcasting end")

    case SendMessageToBestPeer(message) =>
      maxScoreHandler().foreach { handler =>
        log.info(s"Sending $message to a best peer ${handler.path}")
        handler ! message
      }

    case GetPeers => sender() ! connectedPeers.toMap

    case GetMaxChainScore =>
      if (blockchainControllerOpt.isEmpty) blockchainControllerOpt = Some(sender())
      sender() ! BlockchainController.MaxChainScore(maxPeerScore())

    case NewBlock(block, Some(sndr)) =>
      blockchainControllerOpt.foreach { blockchainController =>
        blockchainController ! NewBlock(block, Some(sndr))
        val height = Controller.blockchainStorage.height()
        self ! BroadcastMessage(BlockMessage(height, block), List(sndr))
      }

    case UpdateBlockchainScore(remote, height, score) => updateScore(remote, height, score)

    case nonsense: Any => log.warn(s"NetworkController: got something strange $nonsense")
  }
}

object NetworkController {

  private case object CheckPeers

  private case object AskForPeers

  case object ShutdownNetwork

  case object GetPeers

  case object GetMaxBlockchainScore

  case class PeerData(handler: ActorRef, blockchainScore: Option[BigInt])

  case class PeerDisconnected(address: InetSocketAddress)

  case class UpdateBlockchainScore(remote: InetSocketAddress, height: Int, score: BigInt)

  case class SendMessageToBestPeer(message: Message)

  case class BroadcastMessage(message: Message, exceptOf: List[InetSocketAddress] = List())
}
