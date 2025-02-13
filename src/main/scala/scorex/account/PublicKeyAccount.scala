package scorex.account

import scorex.Controller
import scorex.crypto.Crypto

class PublicKeyAccount(val publicKey: Array[Byte]) extends Account(Crypto.getAddress(publicKey)){
  def generatingBalance: BigDecimal = Controller.blockchainStorage.generationBalance(address)
}