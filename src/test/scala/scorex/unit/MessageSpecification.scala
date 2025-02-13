package scorex.unit

import java.nio.ByteBuffer

import org.scalatest.FunSuite
import scorex.network.message._

class MessageSpecification extends FunSuite {
  test("PingMessage roundtrip") {
    val msg = PingMessage
    val parsedTry = Message.parse(ByteBuffer.wrap(msg.bytes))

    assert(parsedTry.isSuccess)
  }

  test("ScoreMessage roundtrip 1") {
    val h1 = 1
    val s1 = BigInt(2)

    val msg = ScoreMessage(h1, s1)
    val parsed = Message.parse(ByteBuffer.wrap(msg.bytes)).get

    assert(parsed.isInstanceOf[ScoreMessage])
    assert(parsed.asInstanceOf[ScoreMessage].height == h1)
    assert(parsed.asInstanceOf[ScoreMessage].score == s1)
  }

  test("ScoreMessage roundtrip 2") {
    val h1 = Int.MaxValue - 1
    val s1 = BigInt(Long.MaxValue) + 100

    val msg = ScoreMessage(h1, s1)
    val parsed = Message.parse(ByteBuffer.wrap(msg.bytes)).get

    assert(parsed.isInstanceOf[ScoreMessage])
    assert(parsed.asInstanceOf[ScoreMessage].height == h1)
    assert(parsed.asInstanceOf[ScoreMessage].score == s1)
  }

  test("GetSignaturesMessage roundtrip 1") {
    val e1 = 33: Byte
    val e2 = 34: Byte
    val s1 = e2 +: Array.fill(scorex.crypto.Crypto.SignatureLength - 1)(e1)

    val msg = GetSignaturesMessage(Seq(s1))
    val parsed = Message.parse(ByteBuffer.wrap(msg.bytes)).get

    assert(parsed.isInstanceOf[GetSignaturesMessage])
    assert(parsed.asInstanceOf[GetSignaturesMessage].signatures.head.sameElements(s1))
  }

  test("SignaturesMessage roundtrip 1") {
    val e1 = 33: Byte
    val e2 = 34: Byte
    val s1 = e2 +: Array.fill(scorex.crypto.Crypto.SignatureLength - 1)(e1)
    val s2 = e1 +: Array.fill(scorex.crypto.Crypto.SignatureLength - 1)(e2)

    val msg = SignaturesMessage(Seq(s1, s2))
    val parsed = Message.parse(ByteBuffer.wrap(msg.bytes)).get

    assert(parsed.isInstanceOf[SignaturesMessage])
    assert(parsed.asInstanceOf[SignaturesMessage].signatures.head.sameElements(s1))
    assert(parsed.asInstanceOf[SignaturesMessage].signatures.tail.head.sameElements(s2))
  }
}