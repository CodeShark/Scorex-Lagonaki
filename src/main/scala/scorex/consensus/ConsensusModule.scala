package scorex.consensus

import scorex.block.{GenesisBlock, NxtGenesisBlock, QoraGenesisBlock}
import scorex.consensus.nxt.{NxtBlockGenerationData, NxtBlockGenerationDataParser, NxtBlockGenerationFunctions}
import scorex.consensus.qora.{QoraBlockGenerationData, QoraBlockGenerationDataParser, QoraBlockGenerationFunctions}

//data and functions related to a consensus algo
//todo: stricter typing solution?
sealed trait ConsensusModule {
  type kernelData <: BlockGenerationData

  val KERNEL_SIGNATURE_LENGTH: Int

  val consensusFunctions: BlockGenerationFunctions
  val kernelDataParser: BlockGenerationDataParser[kernelData]

  val genesisBlock: GenesisBlock
}

object ConsensusModuleQora extends ConsensusModule {
  override type kernelData = QoraBlockGenerationData

  override val KERNEL_SIGNATURE_LENGTH = QoraBlockGenerationDataParser.GeneratorSignatureLength

  override val consensusFunctions = QoraBlockGenerationFunctions
  override val kernelDataParser = QoraBlockGenerationDataParser
  override val genesisBlock = QoraGenesisBlock

}

object ConsensusModuleNxt extends ConsensusModule {
  override type kernelData = NxtBlockGenerationData

  override val KERNEL_SIGNATURE_LENGTH = NxtBlockGenerationDataParser.GeneratorSignatureLength

  override val consensusFunctions = NxtBlockGenerationFunctions
  override val kernelDataParser = NxtBlockGenerationDataParser
  override val genesisBlock = NxtGenesisBlock
}
