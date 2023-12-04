package eu.datatools4heart.dis

import io.onfhir.path.{FhirPathAggFunctionsFactory, FhirPathIdentityServiceFunctionsFactory, FhirPathNavFunctionsFactory, FhirPathTerminologyServiceFunctionsFactory, FhirPathUtilFunctionsFactory, IFhirPathFunctionLibraryFactory}
import io.tofhir.common.util.CustomMappingFunctionsFactory
import io.tofhir.engine.config.ErrorHandlingType.ErrorHandlingType
import io.tofhir.engine.config.{ErrorHandlingType, ToFhirConfig}
import io.tofhir.engine.execution.RunningJobRegistry
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.scalatest.{Inside, Inspectors, OptionValues}
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should

class MappingTestSpec extends AsyncFlatSpec with should.Matchers with
  OptionValues with Inside with Inspectors {

  val mappingErrorHandling: ErrorHandlingType = ErrorHandlingType.HALT
  val fhirWriteErrorHandling: ErrorHandlingType = ErrorHandlingType.HALT

  val sparkConf: SparkConf = new SparkConf()
    .setAppName(ToFhirConfig.sparkAppName)
    .setMaster(ToFhirConfig.sparkMaster)
    .set("spark.driver.allowMultipleContexts", "false")
    .set("spark.ui.enabled", "false")
  val sparkSession: SparkSession = SparkSession.builder().config(sparkConf).getOrCreate()

  val runningJobRegistry: RunningJobRegistry = new RunningJobRegistry(sparkSession)

  val functionLibraries: Map[String, IFhirPathFunctionLibraryFactory] = {
    Map(
      FhirPathUtilFunctionsFactory.defaultPrefix -> FhirPathUtilFunctionsFactory,
      FhirPathNavFunctionsFactory.defaultPrefix -> FhirPathNavFunctionsFactory,
      FhirPathAggFunctionsFactory.defaultPrefix -> FhirPathAggFunctionsFactory,
      FhirPathIdentityServiceFunctionsFactory.defaultPrefix -> FhirPathIdentityServiceFunctionsFactory,
      FhirPathTerminologyServiceFunctionsFactory.defaultPrefix -> FhirPathTerminologyServiceFunctionsFactory,
      "cst" -> new CustomMappingFunctionsFactory()
    )
  }
}
