package eu.dt4h.dataingestionsuite.mappingfunctions

import io.onfhir.path.annotation.FhirPathFunction
import io.onfhir.path.grammar.FhirPathExprParser.ExpressionContext
import io.onfhir.path.{AbstractFhirPathFunctionLibrary, FhirPathEnvironment, FhirPathException, FhirPathExpressionEvaluator, FhirPathResult, FhirPathString, IFhirPathFunctionLibraryFactory}

import java.nio.ByteBuffer
import java.util
import java.util.Base64

/**
 * Function library containing extraction functions specific to the ICRC pilot environment
 *
 * @param context Context parameters that available when one of the functions
 * @param current
 */
class ICRCMappingFunctions(context: FhirPathEnvironment, current: Seq[FhirPathResult]) extends AbstractFhirPathFunctionLibrary with Serializable {

  /**
   * Decodes the given data and converts it to an array of space separated numbers e.g '123 456 123'.
   *
   * @param dataExpr String data such that byte representation of each 2 consecutive characters represents a number.
   * @return Space separated numbers concatenated in a string
   */
  @FhirPathFunction(documentation = " Decodes the given data and converts it to an array of space separated numbers. The output would be like '123 456 123'. Ex: icrc:createTimeSeriesData(%data)",
    insertText = "icrc:createTimeSeriesData(<dataExpr>)", detail = "icrc", label = "icrc:createTimeSeriesData", kind = "Method", returnType = Seq("string"), inputType = Seq("string"))
  def createTimeSeriesData(dataExpr: ExpressionContext): Seq[FhirPathResult] = {
    val dataResult = new FhirPathExpressionEvaluator(context, current).visit(dataExpr)
    if (dataResult.length > 1 || !dataResult.head.isInstanceOf[FhirPathString]) {
      throw new FhirPathException(s"Invalid function call 'createTimeSeriesData', given expression for origin parameter: ${dataExpr.getText} should return a single, string value!")
    }

    val data: String = dataResult.head.asInstanceOf[FhirPathString].s
    val encoded: Array[Byte] = Base64.getEncoder().encode(data.getBytes());
    val decodedNumbers: Seq[String] = Range(0, encoded.length, 2).map(i => {
      val bf: ByteBuffer = ByteBuffer.wrap(util.Arrays.copyOfRange(encoded, i, i + 2)).order(java.nio.ByteOrder.LITTLE_ENDIAN)
      val decoded: Double = bf.getShort
      decoded + ""
    })
    Seq(FhirPathString(decodedNumbers.mkString(" ")))
  }
}

class ICRCMappingFunctionsFactory() extends IFhirPathFunctionLibraryFactory with Serializable {

  override def getLibrary(context: FhirPathEnvironment, current: Seq[FhirPathResult]): AbstractFhirPathFunctionLibrary =
    new ICRCMappingFunctions(context, current)

}
