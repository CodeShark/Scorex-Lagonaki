package scorex.transaction

import java.util

import com.google.common.primitives.{Bytes, Ints, Longs}
import play.api.libs.json.Json
import scorex.account.Account
import scorex.crypto.{Base58, Crypto}
import scorex.transaction.Transaction.TransactionType


case class GenesisTransaction(override val recipient: Account,
                              override val amount: Long,
                              override val timestamp: Long)
  extends Transaction(TransactionType.GenesisTransaction, recipient, amount, 0, timestamp,
    GenesisTransaction.generateSignature(recipient, amount, timestamp)) {

  import scorex.transaction.GenesisTransaction._
  import scorex.transaction.Transaction._

  override def json() =
    jsonBase() ++ Json.obj("recipient" -> recipient.address, "amount" -> amount.toString)

  override def bytes() = {
    val typeBytes = Array(TransactionType.GenesisTransaction.id.toByte)

    val timestampBytes = Bytes.ensureCapacity(Longs.toByteArray(timestamp), TimestampLength, 0)

    val amountBytes = Bytes.ensureCapacity(Longs.toByteArray(amount), AmountLength, 0)

    val rcpBytes = Base58.decode(recipient.address).get
    require(rcpBytes.length == Account.AddressLength)

    val res = Bytes.concat(typeBytes, timestampBytes, rcpBytes, amountBytes)
    require(res.length == dataLength)
    res
  }

  override lazy val dataLength = TypeLength + BASE_LENGTH

  def isSignatureValid() = {
    val typeBytes = Bytes.ensureCapacity(Ints.toByteArray(TransactionType.GenesisTransaction.id), TypeLength, 0)
    val timestampBytes = Bytes.ensureCapacity(Longs.toByteArray(timestamp), TimestampLength, 0)
    val amountBytes = Bytes.ensureCapacity(Longs.toByteArray(amount), AmountLength, 0)
    val data = Bytes.concat(typeBytes, timestampBytes,
      Base58.decode(recipient.address).get, amountBytes)
    val digest = Crypto.sha256(data)

    Bytes.concat(digest, digest).sameElements(signature)
  }

  override def validate() =
    if (amount < BigDecimal(0)) {
      ValidationResult.NegativeAmount
    } else if (!Crypto.isValidAddress(recipient.address)) {
      ValidationResult.InvalidAddress
    } else ValidationResult.ValidateOke

  override def getCreator(): Option[Account] = None

  override def involvedAmount(account: Account): BigDecimal =
    if (recipient.address.equals(account.address)) amount else 0

  override def balanceChanges(): Map[Option[Account], BigDecimal] = Map(Some(recipient) -> amount)
}


object GenesisTransaction {

  import scorex.transaction.Transaction._

  private val RECIPIENT_LENGTH = Account.AddressLength
  private val BASE_LENGTH = TimestampLength + RECIPIENT_LENGTH + AmountLength

  def generateSignature(recipient: Account, amount: BigDecimal, timestamp: Long) = {
    val typeBytes = Bytes.ensureCapacity(Ints.toByteArray(TransactionType.GenesisTransaction.id), TypeLength, 0)
    val timestampBytes = Bytes.ensureCapacity(Longs.toByteArray(timestamp), TimestampLength, 0)
    val amountBytes = amount.bigDecimal.unscaledValue().toByteArray
    val amountFill = new Array[Byte](AmountLength - amountBytes.length)

    val data = Bytes.concat(typeBytes, timestampBytes,
      Base58.decode(recipient.address).get, Bytes.concat(amountFill, amountBytes))

    val digest = Crypto.sha256(data)
    Bytes.concat(digest, digest)
  }

  def parse(data: Array[Byte]): Transaction = {
    require(data.length >= BASE_LENGTH, "Data does not match block length") //CHECK IF WE MATCH BLOCK LENGTH

    var position = 0

    //READ TIMESTAMP
    val timestampBytes = util.Arrays.copyOfRange(data, position, position + TimestampLength)
    val timestamp = Longs.fromByteArray(timestampBytes)
    position += TimestampLength

    //READ RECIPIENT
    val recipientBytes = util.Arrays.copyOfRange(data, position, position + RECIPIENT_LENGTH)
    val recipient = new Account(Base58.encode(recipientBytes))
    position += RECIPIENT_LENGTH

    //READ AMOUNT
    val amountBytes = util.Arrays.copyOfRange(data, position, position + AmountLength)
    val amount = Longs.fromByteArray(amountBytes)

    GenesisTransaction(recipient, amount, timestamp)
  }
}