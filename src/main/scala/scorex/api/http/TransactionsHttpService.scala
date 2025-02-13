package scorex.api.http

import play.api.libs.json.Json
import scorex.Controller
import scorex.crypto.Base58
import scorex.database.UnconfirmedTransactionsDatabaseImpl
import spray.routing.HttpService

import scala.util.Try


trait TransactionsHttpService extends HttpService with CommonApiFunctions {

  lazy val transactionsRouting =
    pathPrefix("transactions") {
      path("signature" / Segment) { case signature =>
        get {
          complete {
            Try(Base58.decode(signature)).map { signatureBytes =>
              ??? //todo: implement?
            }.getOrElse(ApiError.json(ApiError.InvalidSignature)).toString()
          }
        }
      } ~ path("unconfirmed") {
        get {
          complete {
            Json.arr(UnconfirmedTransactionsDatabaseImpl.all().map(_.json())).toString()
          }
        }
      } ~ path("address" / Segment) { case address =>
        get {
          complete {
            val txs = Controller.blockchainStorage.accountTransactions(address)
            Json.arr(txs.map(_.json())).toString()
          }
        }
      } ~ path("address" / Segment / "limit" / IntNumber) { case (address, limit) =>
        get {
          complete {
            val txs = Controller.blockchainStorage.accountTransactions(address).takeRight(limit)
            Json.arr(txs.map(_.json())).toString()
          }
        }
      }
    }
}
