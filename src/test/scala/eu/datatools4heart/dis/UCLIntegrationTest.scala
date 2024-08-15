package eu.datatools4heart.dis

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import io.onfhir.api.Resource
import io.onfhir.client.OnFhirNetworkClient
import io.onfhir.util.JsonFormatter._
import io.tofhir.engine.mapping._
import io.tofhir.engine.mapping.context.{IMappingContextLoader, MappingContextLoader}
import io.tofhir.engine.mapping.job.FhirMappingJobManager
import io.tofhir.engine.mapping.schema.{IFhirSchemaLoader, SchemaFolderLoader}
import io.tofhir.engine.model._
import io.tofhir.engine.repository.mapping.{FhirMappingFolderRepository, IFhirMappingRepository}
import io.tofhir.engine.util.FhirMappingUtility
import org.json4s.JArray
import org.json4s.jackson.Serialization.writePretty

import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class UCLIntegrationTest extends MappingTestSpec {

  val mappingRepository: IFhirMappingRepository =
    new FhirMappingFolderRepository(Paths.get("mappings/ucl").toAbsolutePath.toUri)

  val contextLoader: IMappingContextLoader = new MappingContextLoader

  val schemaLoader: IFhirSchemaLoader = new SchemaFolderLoader(Paths.get("schemas/ucl").toAbsolutePath.toUri)

  val dataSourceSettings = Map("source" -> FileSystemSourceSettings("test-source-ucl", "https://ucl.ac.uk", Paths.get("test-data/ucl").toAbsolutePath.toString))

  val fhirMappingJobManager = new FhirMappingJobManager(mappingRepository, contextLoader, schemaLoader, functionLibraries, sparkSession)

  val fhirSinkSetting: FhirRepositorySinkSettings = FhirRepositorySinkSettings(fhirRepoUrl = sys.env.getOrElse("FHIR_REPO_URL", "http://localhost:8080/fhir"))
  implicit val actorSystem = ActorSystem("UCLIntegrationTest")

  val onFhirClient = OnFhirNetworkClient.apply(fhirSinkSetting.fhirRepoUrl)

  val fhirServerIsAvailable =
    Try(Await.result(onFhirClient.search("Patient").execute(), FiniteDuration(5, TimeUnit.SECONDS)).httpStatus == StatusCodes.OK)
      .getOrElse(false)

  val terminologyServiceSettings: LocalFhirTerminologyServiceSettings = LocalFhirTerminologyServiceSettings(
    folderPath = "./terminology-systems/DT4HTerminologyService",
    conceptMapFiles = Seq(
      ConceptMapFile(
        id = "snomedToIcd10.csv",
        name = "snomedToIcd10.csv",
        conceptMapUrl = "https://datatools4heart.eu/fhir/mappings/ConceptMap/snomed-to-icd10",
        sourceValueSetUrl = "http://snomed.info/sct",
        targetValueSetUrl = "http://hl7.org/fhir/sid/icd-10"
      ),
      ConceptMapFile(
        id = "omopToIcd10.csv",
        name = "omopToIcd10.csv",
        conceptMapUrl = "https://datatools4heart.eu/fhir/mappings/ConceptMap/omop-to-icd10",
        sourceValueSetUrl = "https://www.ohdsi.org/omop",
        targetValueSetUrl = "http://hl7.org/fhir/sid/icd-10"
      ),
      ConceptMapFile(
        id = "omopToRxNorm.csv",
        name = "omopToRxNorm.csv",
        conceptMapUrl = "https://datatools4heart.eu/fhir/mappings/ConceptMap/omop-to-rxnorm",
        sourceValueSetUrl = "https://www.ohdsi.org/omop",
        targetValueSetUrl = "http://www.nlm.nih.gov/research/umls/rxnorm"
      ),
      ConceptMapFile(
        id = "omopToIcd10Pcs.csv",
        name = "omopToIcd10Pcs.csv",
        conceptMapUrl = "https://datatools4heart.eu/fhir/mappings/ConceptMap/omop-to-icd10pcs",
        sourceValueSetUrl = "https://www.ohdsi.org/omop",
        targetValueSetUrl = "http://hl7.org/fhir/sid/icd-10-pcs"
      ),
      ConceptMapFile(
        id = "omopToLoinc.csv",
        name = "omopToLoinc.csv",
        conceptMapUrl = "https://datatools4heart.eu/fhir/mappings/ConceptMap/omop-to-loinc",
        sourceValueSetUrl = "https://www.ohdsi.org/omop",
        targetValueSetUrl = "http://loinc.org"
      ),
      ConceptMapFile(
        id = "omopToSnomed.csv",
        name = "omopToSnomed.csv",
        conceptMapUrl = "https://datatools4heart.eu/fhir/mappings/ConceptMap/omop-to-snomed",
        sourceValueSetUrl = "https://www.ohdsi.org/omop",
        targetValueSetUrl = "http://snomed.info/sct"
      ),
      ConceptMapFile(
        id = "snomedToIcd10Pcs.csv",
        name = "snomedToIcd10Pcs.csv",
        conceptMapUrl = "https://datatools4heart.eu/fhir/mappings/ConceptMap/snomed-to-icd10pcs",
        sourceValueSetUrl = "http://snomed.info/sct",
        targetValueSetUrl = "http://hl7.org/fhir/sid/icd-10-pcs"
      ),
      ConceptMapFile(
        id = "noteNlpSnomedToIcd10.csv",
        name = "noteNlpSnomedToIcd10.csv",
        conceptMapUrl = "https://datatools4heart.eu/fhir/mappings/ConceptMap/note-nlp-snomed-to-icd10",
        sourceValueSetUrl = "https://www.ohdsi.org/omop",
        targetValueSetUrl = "http://hl7.org/fhir/sid/icd-10"
      ),
    )
  )

  val mappingJob: FhirMappingJob = FhirMappingJob(
    name = Some("mappingJob"),
    sourceSettings = dataSourceSettings,
    sinkSettings = fhirSinkSetting,
    mappings = Seq.empty,
    dataProcessingSettings = DataProcessingSettings(saveErroneousRecords = false, archiveMode = ArchiveModes.OFF))

  val causeOfDeathMappingTask = FhirMappingTask(
    mappingRef = "https://datatools4heart.eu/fhir/mappings/ucl/cause-of-death-mapping",
    sourceBinding = Map("omopDeath" -> FileSystemSource(path = "DEATH.csv")))

  val conditionMappingTask = FhirMappingTask(
    mappingRef = "https://datatools4heart.eu/fhir/mappings/ucl/condition-mapping",
    sourceBinding = Map("omopCondition" -> FileSystemSource(path = "CONDITION_OCCURRENCE.csv")))

  val encounterMappingTask = FhirMappingTask(
    mappingRef = "https://datatools4heart.eu/fhir/mappings/ucl/encounter-mapping",
    sourceBinding = Map("omopVisitOccurrence" -> FileSystemSource(path = "VISIT_OCCURRENCE.csv"),
                        "omopConditionOccurrence" -> FileSystemSource(path = "CONDITION_OCCURRENCE.csv")))

  val labResultMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/ucl/lab-result-mapping",
      sourceBinding = Map("omopMeasurement" -> FileSystemSource(path = "MEASUREMENT.csv")))

  val medAdmMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/ucl/medication-mapping",
      sourceBinding = Map("omopDrugExposure" -> FileSystemSource(path = "DRUG_EXPOSURE.csv")))

  val patientMappingTask = FhirMappingTask(
    mappingRef = "https://datatools4heart.eu/fhir/mappings/ucl/patient-mapping",
    sourceBinding = Map("omopPerson" -> FileSystemSource(path = "PERSON.csv"),
                        "omopDeath" -> FileSystemSource(path = "DEATH.csv")))

  val procedureMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/ucl/procedure-mapping",
      sourceBinding = Map("omopProcedure" -> FileSystemSource(path = "PROCEDURE_OCCURRENCE.csv"))
    )

  val vitalSignMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/ucl/vital-sign-mapping",
      sourceBinding = Map("omopMeasurement" -> FileSystemSource(path = "MEASUREMENT.csv"))
    )


  "conditions mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(conditionMappingTask)),
      mappingJobSourceSettings = dataSourceSettings,
      terminologyServiceSettings = Some(terminologyServiceSettings)) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })

      results.length shouldBe 5

      (results.head \ "id").extract[String] shouldBe FhirMappingUtility.getHashedId("Condition", "26140")
      (results.head \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "I21"
      (results.head \ "code" \ "coding" \ "system").extract[Seq[String]].head shouldBe "http://hl7.org/fhir/sid/icd-10"
      (results.head \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "9056")
      (results.head \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "14452")
      (results.head \ "onsetDateTime").extract[String] shouldBe "2019-10-04T00:00:00Z"

      (results(4) \ "id").extract[String] shouldBe FhirMappingUtility.getHashedId("Condition", "202535")
      // multiple codes exist after terminology service conversion for 4320022 (snomed-to-icd10)
      (results(4) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "S02.85"
      (results(4) \ "code" \ "coding" \ "system").extract[Seq[String]].head shouldBe "http://hl7.org/fhir/sid/icd-10"
      (results(4) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "6356")
      (results(4) \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "17142")
      (results(4) \ "onsetDateTime").extract[String] shouldBe "2020-10-25T00:00:00Z"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(conditionMappingTask)),
        sourceSettings = dataSourceSettings,
        sinkSettings = fhirSinkSetting,
        terminologyServiceSettings = Some(terminologyServiceSettings))
      .map(unit =>
        unit shouldBe()
      )
  }

  "cause of death mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(causeOfDeathMappingTask)),
      mappingJobSourceSettings = dataSourceSettings,
      terminologyServiceSettings = Some(terminologyServiceSettings)) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 2

      (results.head \ "id").extract[String] shouldBe FhirMappingUtility.getHashedId("Observation", "Death" + "8216")
      (results.head \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "79378-6"
      (results.head \ "code" \ "coding" \ "system").extract[Seq[String]].head shouldBe "http://loinc.org"
      (results.head \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Cause of death"
      (results.head \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "8216")
      (results.head \ "valueCodeableConcept" \ "coding" \ "code").extract[Seq[String]].head shouldBe "I50"
      (results.head \ "valueCodeableConcept" \ "coding" \ "system").extract[Seq[String]].head shouldBe "http://hl7.org/fhir/sid/icd-10"
      (results.head \ "effectiveDateTime").extract[String] shouldBe "2020-06-01T23:00:00Z"


      (results(1) \ "id").extract[String] shouldBe FhirMappingUtility.getHashedId("Observation", "Death" + "3569")
      (results(1) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "79378-6"
      (results(1) \ "code" \ "coding" \ "system").extract[Seq[String]].head shouldBe "http://loinc.org"
      (results(1) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Cause of death"
      (results(1) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "3569")
      // multiple codes exist after terminology service conversion for 444074 (snomed-to-icd10)
      (results(1) \ "valueCodeableConcept" \ "coding" \ "code").extract[Seq[String]].head shouldBe "V32.4"
      (results(1) \ "valueCodeableConcept" \ "coding" \ "system").extract[Seq[String]].head shouldBe "http://hl7.org/fhir/sid/icd-10"
      (results(1) \ "effectiveDateTime").extract[String] shouldBe "2020-10-26T00:00:00Z"
    }
  }


  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(causeOfDeathMappingTask)),
        sourceSettings = dataSourceSettings,
        sinkSettings = fhirSinkSetting,
        terminologyServiceSettings = Some(terminologyServiceSettings))
      .map(unit =>
        unit shouldBe()
      )
  }


  "encounter mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(encounterMappingTask)),
      mappingJobSourceSettings = dataSourceSettings,
      terminologyServiceSettings = Some(terminologyServiceSettings)) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })

      results.size shouldBe 50
      (results.head \ "id").extract[String] shouldBe FhirMappingUtility.getHashedId("Encounter", "14452")
      (results.head \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "9056")

      (results.head \ "actualPeriod" \ "start").extract[String] shouldBe "2020-03-03T10:38:00Z"
      (results.head \ "actualPeriod" \ "end").extract[String] shouldBe "2021-04-27T05:10:00Z"

      (((results.head \ "reason")(0) \ "value")(0) \ "concept" \ "coding" \ "code").extract[Seq[String]].head shouldBe "I21"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(encounterMappingTask)),
        sourceSettings = dataSourceSettings,
        sinkSettings = fhirSinkSetting,
        terminologyServiceSettings = Some(terminologyServiceSettings))
      .map(unit =>
        unit shouldBe()
      )
  }

  "lab results mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(labResultMappingTask)),
      mappingJobSourceSettings = dataSourceSettings,
      terminologyServiceSettings = Some(terminologyServiceSettings)) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      //TODO: update lab result tests
      // println(writePretty(results))
      results.length shouldBe 1
    }
  }


  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(labResultMappingTask)),
                         sourceSettings = dataSourceSettings,
                         sinkSettings = fhirSinkSetting,
                         terminologyServiceSettings = Some(terminologyServiceSettings))
      .map(unit =>
        unit shouldBe()
      )
  }

  "medication administration mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(medAdmMappingTask)),
                                                      mappingJobSourceSettings = dataSourceSettings,
                                                      terminologyServiceSettings = Some(terminologyServiceSettings)) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })

      results.length shouldBe 7
      (results.head \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "7167")
      (results.head \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "123.0")

      (results.head \ "medication" \ "concept" \ "coding" \ "code").extract[Seq[String]].head shouldBe "A02BC03"
      (results.head \ "medication" \ "concept" \ "coding" \ "system").extract[Seq[String]].head shouldBe "http://www.whocc.no/atc"

      (results.head \ "occurencePeriod" \ "start").extract[String] shouldBe "2022-06-08T05:54:00Z"
      (results.head \ "occurencePeriod" \ "end").extract[String] shouldBe "2022-06-09T05:54:00Z"
      (results.head \ "dosage" \ "dose" \ "value").extract[Int] shouldBe 2
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(medAdmMappingTask)),
                         sourceSettings = dataSourceSettings,
                         sinkSettings = fhirSinkSetting,
                         terminologyServiceSettings = Some(terminologyServiceSettings))
      .map(unit =>
        unit shouldBe()
      )
  }

  "patient mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(patientMappingTask)),
                                                      mappingJobSourceSettings = dataSourceSettings,
                                                      terminologyServiceSettings = Some(terminologyServiceSettings)) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        r.mappedResource.get.parseJson
      })
      val genders = (JArray(results.toList) \ "gender").extract[Seq[String]]
      genders shouldBe ((1 to 1).map(_ => "female") ++ (2 to 5).map(_ => "male"))

      (results.apply(1) \ "id").extract[String] shouldBe FhirMappingUtility.getHashedId("Patient", "3569")
      (results.apply(1) \ "birthDate").extract[String] shouldBe "1940-08-01"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    //Send it to our fhir repo if they are also validated
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(patientMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting, terminologyServiceSettings = Some(terminologyServiceSettings))
      .map(unit =>
        unit shouldBe()
      )
  }

  "procedure mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(procedureMappingTask)),
                                                      mappingJobSourceSettings = dataSourceSettings,
                                                      terminologyServiceSettings = Some(terminologyServiceSettings)) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })

      results.length shouldBe 34
      (results.apply(1) \ "resourceType").extract[String] shouldBe "Condition"
      (results.apply(1) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "1978")
      (results.apply(1) \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "13674")
      (results.apply(1) \ "onsetDateTime").extract[String] shouldBe "2022-06-18T00:00:00Z"
      (results.apply(1) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "Z03.8"
      (results.apply(1) \ "code" \ "coding" \ "system").extract[Seq[String]].head shouldBe "http://hl7.org/fhir/sid/icd-10"
      (results.apply(1) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Observation for other suspected diseases and conditions"
      // if concept_code cannot be mapped to ICD10 (Condition), then it should be mapped to ICD10PCS (Procedure)
      (results.head \ "resourceType").extract[String] shouldBe "Procedure"
      (results.head \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "6581")
      (results.head \ "occurrenceDateTime").extract[String] shouldBe "2021-12-25T09:55:52Z"
      (results.head \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "0W990ZX"
      (results.head \ "code" \ "coding" \ "system").extract[Seq[String]].head shouldBe "http://hl7.org/fhir/sid/icd-10-pcs"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(procedureMappingTask)),
                         sourceSettings = dataSourceSettings,
                         sinkSettings = fhirSinkSetting,
                         terminologyServiceSettings = Some(terminologyServiceSettings))
      .map(unit =>
        unit shouldBe()
      )
  }

  "vital sign mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(vitalSignMappingTask)),
                                                      mappingJobSourceSettings = dataSourceSettings,
                                                      terminologyServiceSettings = Some(terminologyServiceSettings)) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      // TODO: our goal is to get LOINC code of lab results, however
      // there are snomed codes (concept_id) in the test data, and there are no mappings from SNOMED to LOINC in the omop database
      // so we need to check if the concept_id is LOINC or SNOMED and then map it to LOINC if it is SNOMED
      results.length shouldBe 0
    }
  }

//  it should "map test data and write it to FHIR repo successfully" in {
//    assume(fhirServerIsAvailable)
//    fhirMappingJobManager
//      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(vitalSignMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting, terminologyServiceSettings = Some(terminologyServiceSettings))
//      .map(unit =>
//        unit shouldBe()
//      )
//  }


}
