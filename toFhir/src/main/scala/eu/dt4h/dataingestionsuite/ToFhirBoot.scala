package eu.dt4h.dataingestionsuite

import eu.dt4h.dataingestionsuite.mappingfunctions.ICRCMappingFunctionsFactory
import io.onfhir.path.IFhirPathFunctionLibraryFactory
import io.tofhir.engine.Boot

/**
 * A wrapper bootstrap on the core ToFhir Engine
 */
object ToFhirBoot extends App {
  // Inject DT4H-specific function libraries
  val dt4hMappingFunctions: Map[String, IFhirPathFunctionLibraryFactory] = Map("icrc" -> new ICRCMappingFunctionsFactory())
  Boot.init(args, dt4hMappingFunctions)
}
