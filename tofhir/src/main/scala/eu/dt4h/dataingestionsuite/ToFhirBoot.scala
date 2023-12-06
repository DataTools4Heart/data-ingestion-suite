package eu.dt4h.dataingestionsuite

import eu.dt4h.dataingestionsuite.mappingfunctions.ICRCMappingFunctionsFactory
import io.onfhir.path.IFhirPathFunctionLibraryFactory
import io.tofhir.engine.Boot
import io.tofhir.rxnorm.RxNormApiFunctionLibraryFactory

/**
 * A wrapper bootstrap on the core ToFhir Engine
 */
object ToFhirBoot extends App {
  // Inject DT4H-specific function libraries
  val dt4hMappingFunctions: Map[String, IFhirPathFunctionLibraryFactory] = Map(
    "icrc" -> new ICRCMappingFunctionsFactory(),
    "rxn" -> new RxNormApiFunctionLibraryFactory("https://rxnav.nlm.nih.gov", 2)
  )
  Boot.init(args, dt4hMappingFunctions)
}
