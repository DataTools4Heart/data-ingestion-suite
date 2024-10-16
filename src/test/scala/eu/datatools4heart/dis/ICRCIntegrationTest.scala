package eu.datatools4heart.dis

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import io.onfhir.api.Resource
import io.onfhir.client.OnFhirNetworkClient
import io.onfhir.util.JsonFormatter._
import io.tofhir.engine.mapping.context.{IMappingContextLoader, MappingContextLoader}
import io.tofhir.engine.mapping.job.FhirMappingJobManager
import io.tofhir.engine.mapping.schema.{IFhirSchemaLoader, SchemaFolderLoader}
import io.tofhir.engine.model._
import io.tofhir.engine.repository.mapping.{FhirMappingFolderRepository, IFhirMappingRepository}
import io.tofhir.engine.util.FhirMappingUtility
import org.json4s.JArray

import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

class ICRCIntegrationTest extends MappingTestSpec {

  val mappingRepository: IFhirMappingRepository =
    new FhirMappingFolderRepository(Paths.get("mappings/icrc").toAbsolutePath.toUri)

  val contextLoader: IMappingContextLoader = new MappingContextLoader

  val schemaLoader: IFhirSchemaLoader = new SchemaFolderLoader(Paths.get("schemas/icrc").toAbsolutePath.toUri)

  val dataSourceSettings: Map[String, FileSystemSourceSettings] = Map("source" ->
    FileSystemSourceSettings("test-source-icrc", "https://www.fnusa-icrc.org", Paths.get("test-data/icrc").toAbsolutePath.toString))

  val fhirMappingJobManager = new FhirMappingJobManager(mappingRepository, contextLoader, schemaLoader, functionLibraries, sparkSession)

  val fhirSinkSetting: FhirRepositorySinkSettings = FhirRepositorySinkSettings(fhirRepoUrl = sys.env.getOrElse("FHIR_REPO_URL", "http://localhost:8080/fhir"))
  implicit val actorSystem = ActorSystem("ICRCIntegrationTest")

  val onFhirClient = OnFhirNetworkClient.apply(fhirSinkSetting.fhirRepoUrl)

  val fhirServerIsAvailable =
    Try(Await.result(onFhirClient.search("Patient").execute(), FiniteDuration(5, TimeUnit.SECONDS)).httpStatus == StatusCodes.OK)
      .getOrElse(false)

  val mappingJob: FhirMappingJob = FhirMappingJob(
    name = Some("mappingJob"),
    sourceSettings = dataSourceSettings,
    sinkSettings = fhirSinkSetting,
    mappings = Seq.empty,
    dataProcessingSettings = DataProcessingSettings(saveErroneousRecords = false, archiveMode = ArchiveModes.OFF))

  val conditionMappingTask = FhirMappingTask(
    mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/condition-mapping",
    sourceBinding = Map("source" -> FileSystemSource(path = "conditions.csv"))
  )

  val echocardiographMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/echocardiograph-mapping",
      sourceBinding = Map("echoObservation" -> FileSystemSource(path = "echo-observation.csv"),
                          "echoMeasurements" -> FileSystemSource(path = "echo-measurement.csv"))
    )

  val electrocardiographMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/ECG-mapping",
      sourceBinding = Map("ecg" -> FileSystemSource(path = "electrocardiograph.csv"))
    )

  val encounterMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/encounter-mapping",
      sourceBinding = Map("source" -> FileSystemSource(path = "encounters.csv"))
    )

  val labResultMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/lab-result-mapping",
      sourceBinding = Map("source" -> FileSystemSource(path = "labresults.csv"))
    )

  val medAdmMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/medication-administration-mapping",
      sourceBinding = Map("source" -> FileSystemSource(path = "medications.csv"))
    )

  val nyhaMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/nyha-mapping",
      sourceBinding = Map("source" -> FileSystemSource(path = "nyha.csv"))
    )

  val patientMappingTask = FhirMappingTask(
    mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/patient-mapping",
    sourceBinding = Map("source" -> FileSystemSource(path = "patients.csv"))
  )

  val procedureMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/procedure-mapping",
      sourceBinding = Map("source" -> FileSystemSource(path = "procedures.csv"))
    )

  val smokingStatusMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/smoking-status-mapping",
      sourceBinding = Map("source" -> FileSystemSource(path = "smoking-status.csv"))
    )

  val symptomMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/symptom-mapping",
      sourceBinding = Map("source" -> FileSystemSource(path = "symptoms.csv"))
    )

  val vitalSignMappingTask =
    FhirMappingTask(
      mappingRef = "https://datatools4heart.eu/fhir/mappings/icrc/vital-sign-mapping",
      sourceBinding = Map("source" -> FileSystemSource(path = "vitalsigns.csv"))
    )


  "conditions mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(conditionMappingTask)),
                                                      mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 10
      (results.apply(2) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "15617")
      (results.apply(4) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "I70.20"
      (results.apply(4) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Atherosclerosis of native arteries of extremities"
      (results.apply(1) \ "onsetDateTime").extract[String] shouldBe "2019-07-01"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(conditionMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "echocardiograph mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(echocardiographMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 20

      (results.apply(0) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "32232")
      (results.apply(0) \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "223394")
      (results.apply(0) \ "effectiveDateTime").extract[String] shouldBe "2003-12-01"

      (results.apply(0) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "34552-0"
      (results.apply(0) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Cardiac 2D echo panel"

      (results.apply(0) \ "component").extract[JArray].arr.length shouldBe 10
      ((results.apply(0) \ "component")(0) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8806-2"
      ((results.apply(0) \ "component")(0) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Left ventricular Ejection fraction by 2D echo"
      ((results.apply(0) \ "component")(0) \ "valueQuantity" \ "value").extract[Double] shouldBe 0.531425152
      ((results.apply(0) \ "component")(0) \ "valueQuantity" \ "unit").extract[String] shouldBe "%"

      ((results.apply(0) \ "component")(1) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "18083-6"
      ((results.apply(0) \ "component")(1) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Left ventricular Internal diameter minor axis diastole by US 2D"
      ((results.apply(0) \ "component")(1) \ "valueQuantity" \ "value").extract[Int] shouldBe 1
      ((results.apply(0) \ "component")(1) \ "valueQuantity" \ "unit").extract[String] shouldBe "mm"

      ((results.apply(0) \ "component")(2) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "79969-2"
      ((results.apply(0) \ "component")(2) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Interventricular septum Thickness at end diastole by US 2D"
      ((results.apply(0) \ "component")(2) \ "valueQuantity" \ "value").extract[Double] shouldBe 0.828838592
      ((results.apply(0) \ "component")(2) \ "valueQuantity" \ "unit").extract[String] shouldBe "mm"

      ((results.apply(0) \ "component")(3) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "18085-1"
      ((results.apply(0) \ "component")(3) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Left ventricular Internal diameter minor axis systole by US 2D"
      ((results.apply(0) \ "component")(3) \ "valueQuantity" \ "value").extract[Int] shouldBe 1
      ((results.apply(0) \ "component")(3) \ "valueQuantity" \ "unit").extract[String] shouldBe "mm"

      ((results.apply(0) \ "component")(4) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "29468-6"
      ((results.apply(0) \ "component")(4) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Left atrial Diameter anterior-posterior systole by US 2D"
      ((results.apply(0) \ "component")(4) \ "valueQuantity" \ "value").extract[Double] shouldBe 0.1173482752
      ((results.apply(0) \ "component")(4) \ "valueQuantity" \ "unit").extract[String] shouldBe "mm"

      ((results.apply(0) \ "component")(5) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "75988-6"
      ((results.apply(0) \ "component")(5) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Left ventricular End diastolic volume"
      ((results.apply(0) \ "component")(5) \ "valueQuantity" \ "value").extract[Double] shouldBe 0.1051116864
      ((results.apply(0) \ "component")(5) \ "valueQuantity" \ "unit").extract[String] shouldBe "mL"

      ((results.apply(0) \ "component")(6) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "75989-4"
      ((results.apply(0) \ "component")(6) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Left ventricular End systolic volume"
      ((results.apply(0) \ "component")(6) \ "valueQuantity" \ "value").extract[Double] shouldBe 0.415748384
      ((results.apply(0) \ "component")(6) \ "valueQuantity" \ "unit").extract[String] shouldBe "mL"

      ((results.apply(0) \ "component")(7) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "79971-8"
      ((results.apply(0) \ "component")(7) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Interventricular septum thickness at end systole by US 2D"
      ((results.apply(0) \ "component")(7) \ "valueQuantity" \ "value").extract[Double] shouldBe 0.980712
      ((results.apply(0) \ "component")(7) \ "valueQuantity" \ "unit").extract[String] shouldBe "mm"

      ((results.apply(0) \ "component")(8) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "77903-3"
      ((results.apply(0) \ "component")(8) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Tricuspid valve annulus Excursion distance during systole by US.M-mode"
      ((results.apply(0) \ "component")(8) \ "valueQuantity" \ "value").extract[Double] shouldBe 0.180802032
      ((results.apply(0) \ "component")(8) \ "valueQuantity" \ "unit").extract[String] shouldBe "cm"

      ((results.apply(0) \ "component")(9) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "80032-6"
      ((results.apply(0) \ "component")(9) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Left ventricular posterior wall Thickness at end diastole by US 2D"
      ((results.apply(0) \ "component")(9) \ "valueQuantity" \ "value").extract[Double] shouldBe 0.1510482944
      ((results.apply(0) \ "component")(9) \ "valueQuantity" \ "unit").extract[String] shouldBe "mm"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(echocardiographMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "electrocardiograph mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(electrocardiographMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 10

      (results.apply(0) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "3614")
      (results.apply(0) \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "1872797")
      (results.apply(0) \ "effectiveDateTime").extract[String] shouldBe "2021-01-19T12:22:55Z"

      (results.apply(0) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "34534-8"
      (results.apply(0) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "12 lead EKG panel"

      (results.apply(1) \ "component").extract[JArray].arr.length shouldBe 20
      ((results.apply(1) \ "component")(0) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8625-6"
      ((results.apply(1) \ "component")(0) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "P-R Interval"
      ((results.apply(1) \ "component")(0) \ "valueQuantity" \ "value").extract[Int] shouldBe -255
      ((results.apply(1) \ "component")(0) \ "valueQuantity" \ "unit").extract[String] shouldBe "ms"

      ((results.apply(1) \ "component")(1) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8633-0"
      ((results.apply(1) \ "component")(1) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "QRS duration"
      ((results.apply(1) \ "component")(1) \ "valueQuantity" \ "value").extract[Int] shouldBe 121
      ((results.apply(1) \ "component")(1) \ "valueQuantity" \ "unit").extract[String] shouldBe "ms"

      ((results.apply(1) \ "component")(2) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8634-8"
      ((results.apply(1) \ "component")(2) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Q-T interval"
      ((results.apply(1) \ "component")(2) \ "valueQuantity" \ "value").extract[Int] shouldBe 357
      ((results.apply(1) \ "component")(2) \ "valueQuantity" \ "unit").extract[String] shouldBe "ms"

      ((results.apply(1) \ "component")(3) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8636-3"
      ((results.apply(1) \ "component")(3) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Q-T interval corrected"
      ((results.apply(1) \ "component")(3) \ "valueQuantity" \ "value").extract[Int] shouldBe 422
      ((results.apply(1) \ "component")(3) \ "valueQuantity" \ "unit").extract[String] shouldBe "ms"

      ((results.apply(1) \ "component")(4) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8626-4"
      ((results.apply(1) \ "component")(4) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "P wave axis"
      ((results.apply(1) \ "component")(4) \ "valueQuantity" \ "value").extract[Int] shouldBe -255
      ((results.apply(1) \ "component")(4) \ "valueQuantity" \ "unit").extract[String] shouldBe "deg"

      ((results.apply(1) \ "component")(5) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8632-2"
      ((results.apply(1) \ "component")(5) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "QRS axis"
      ((results.apply(1) \ "component")(5) \ "valueQuantity" \ "value").extract[Int] shouldBe 87
      ((results.apply(1) \ "component")(5) \ "valueQuantity" \ "unit").extract[String] shouldBe "deg"

      ((results.apply(1) \ "component")(6) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8638-9"
      ((results.apply(1) \ "component")(6) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "T wave axis"
      ((results.apply(1) \ "component")(6) \ "valueQuantity" \ "value").extract[Int] shouldBe -34
      ((results.apply(1) \ "component")(6) \ "valueQuantity" \ "unit").extract[String] shouldBe "deg"

      ((results.apply(1) \ "component")(7) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8601-7"
      ((results.apply(1) \ "component")(7) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "EKG impression"
      ((results.apply(1) \ "component")(7) \ "valueString").extract[String] shouldBe "ATRIAL FIBRILACE S PRUDKOU VENTRIKULAR ODEZVOU || MOZNY  PREDNI MYOKARDIAL INFARKT , Z NEURCITELNEHO STARI || ABNORMAL EKG || Nepotvrzena zprava"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(electrocardiographMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }


  "encounter mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(encounterMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.size shouldBe 10
      (results.apply(1) \ "id").extract[String] shouldBe FhirMappingUtility.getHashedId("Encounter", "475596")
      (results.apply(1) \ "identifier" \ "value").extract[Seq[String]] shouldBe Seq("475596")
      (results.apply(1) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "66789")

      (results.apply(9) \ "status").extract[String] shouldBe "finished"
      (((results.apply(5) \ "class")(0) \ "coding")(0) \ "code").extract[String] shouldBe "AMB"
      (((results.apply(5) \ "class")(0) \ "coding")(0) \ "display").extract[String] shouldBe "ambulatory"

      (results.apply(6) \ "actualPeriod" \ "start").extract[String] shouldBe "2013-04-18"
      (results.apply(6) \ "actualPeriod" \ "end").extract[String] shouldBe "2013-04-18"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(encounterMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "lab results mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(labResultMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 10
      (results.apply(1) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "65393")
      (results.apply(1) \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "214875")
      (results.head \ "effectiveDateTime").extract[String] shouldBe "2022-06-06"

      (results.head \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "2160-0"
      (results.head \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Creatinine [Mass/volume] in Serum or Plasma"

      (results.head \ "valueQuantity" \ "value").extract[Double] shouldBe 17.5305
      (results.head \ "valueQuantity" \ "unit").extract[String] shouldBe "mg/L"
    }
  }


  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(labResultMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "medication administration mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(medAdmMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 10
      (results.apply(2) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "23912")
      (results.apply(2) \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "213538")

      (results.apply(2) \ "medication" \ "concept" \ "coding" \ "code").extract[Seq[String]].head shouldBe "R06AX13"
      (results.apply(2) \ "medication" \ "concept" \ "coding" \ "display").extract[Seq[String]].head shouldBe "LORATADINE"

      (results.apply(1) \ "occurencePeriod" \ "start").extract[String] shouldBe "2015-01-11"
      (results.apply(1) \ "occurencePeriod" \ "end").extract[String] shouldBe "2015-01-11"
      (results.apply(2) \ "dosage" \ "dose" \ "value").extract[Int] shouldBe 20
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(medAdmMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "nyha mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(nyhaMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 2
      (results.apply(0) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "48797")
      (results.apply(0) \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "213452")

      (results.apply(0) \ "effectiveDateTime").extract[String] shouldBe "2023-06-08T13:02:00Z"

      (results.apply(0) \ "valueCodeableConcept" \ "coding" \ "code").extract[Seq[String]].head shouldBe "LA28405-1"
      (results.apply(0) \ "valueCodeableConcept" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Class-II"

      (results.apply(1) \ "meta" \ "profile").extract[Seq[String]].head shouldBe "https://datatools4heart.eu/fhir/StructureDefinition/HFR-NYHAAssessment"

      (results.apply(1) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "88020-3"
      (results.apply(1) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Functional capacity NYHA"

    }
  }
  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(nyhaMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "patient mapping" should "map test data" in {
    //Some semantic tests on generated content
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(patientMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        r.mappedResource.get.parseJson
      })
      val genders = (JArray(results.toList) \ "gender").extract[Seq[String]]
      genders shouldBe ((1 to 5).map(_ => "male") ++ (6 to 10).map(_ => "female"))

      (results.apply(2) \ "id").extract[String] shouldBe FhirMappingUtility.getHashedId("Patient", "38677")
      (results.apply(2) \ "identifier" \ "value").extract[Seq[String]] shouldBe Seq("38677")
      (results.apply(2) \ "birthDate").extract[String] shouldBe "1957-04-07"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    //Send it to our fhir repo if they are also validated
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(patientMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "procedure mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(procedureMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 10
      (results.apply(0) \ "id").extract[String] shouldBe FhirMappingUtility.getHashedId("Procedure", "393057")
      (results.apply(0) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "57092")
      ((results.apply(0) \ "reason")(0) \ "reference" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Observation", "296589")
      (results.apply(0) \ "occurrenceDateTime").extract[String] shouldBe "2017-02-17T02:00:00Z"

      (results.apply(1) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "I48"
      (results.apply(1) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Atrial fibrillation and flutter (referred in relation to the rhythm correction in DT4H)"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(procedureMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "smoking status mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(smokingStatusMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 1

      (results.apply(0) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "66442")
      (results.apply(0) \ "effectivePeriod" \ "start").extract[String] shouldBe "2012-07-20"
      (results.apply(0) \ "effectivePeriod" \ "end").extract[String] shouldBe "2013-12-10"

      (results.apply(0) \ "valueCodeableConcept" \ "coding" \ "code").extract[Seq[String]].head shouldBe "449868002"
      (results.apply(0) \ "valueCodeableConcept" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Current every day smoker"

      (results.apply(0) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "72166-2"
      (results.apply(0) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Tobacco smoking status"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(smokingStatusMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "symptom mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(symptomMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 1

      (results.apply(0) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "65413")
      (results.apply(0) \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "219732")
      (results.apply(0) \ "effectiveDateTime").extract[String] shouldBe "2018-11-08T11:00:00Z"

      (results.apply(0) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "267039000"
      (results.apply(0) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Ankle swelling"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(symptomMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

  "vital sign mapping" should "map test data" in {
    fhirMappingJobManager.executeMappingTaskAndReturn(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(vitalSignMappingTask)), mappingJobSourceSettings = dataSourceSettings) map { mappingResults =>
      val results = mappingResults.map(r => {
        r.mappedResource shouldBe defined
        val resource = r.mappedResource.get.parseJson
        resource shouldBe a[Resource]
        resource
      })
      results.length shouldBe 10

      (results.apply(0) \ "subject" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Patient", "15617")
      (results.apply(0) \ "encounter" \ "reference").extract[String] shouldBe FhirMappingUtility.getHashedReference("Encounter", "213595")
      (results.apply(0) \ "effectiveDateTime").extract[String] shouldBe "2016-01-11T13:02:00Z"

      (results.apply(0) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "29463-7"
      (results.apply(0) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Body weight"

      (results.apply(1) \ "code" \ "coding" \ "code").extract[Seq[String]].head shouldBe "8302-2"
      (results.apply(1) \ "code" \ "coding" \ "display").extract[Seq[String]].head shouldBe "Body height"

      (results.apply(2) \ "valueQuantity" \ "value").extract[String] shouldBe "60"
      (results.apply(2) \ "valueQuantity" \ "unit").extract[String] shouldBe "kg"

      (results.apply(3) \ "valueQuantity" \ "value").extract[String] shouldBe "164"
      (results.apply(3) \ "valueQuantity" \ "unit").extract[String] shouldBe "cm"
    }
  }

  it should "map test data and write it to FHIR repo successfully" in {
    assume(fhirServerIsAvailable)
    fhirMappingJobManager
      .executeMappingJob(mappingJobExecution = FhirMappingJobExecution(job = mappingJob, mappingTasks = Seq(vitalSignMappingTask)), sourceSettings = dataSourceSettings, sinkSettings = fhirSinkSetting)
      .map(unit =>
        unit shouldBe()
      )
  }

}
