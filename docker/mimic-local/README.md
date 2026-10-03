# Re-executing the MIMIC-IV v3.1 → DT4H CDM mappings on one workstation

Step-by-step plan for re-running the `mimic-hosp-csv-to-fhir-server` job with the Ignifyr observability
features (execution results + terminology / concept-map / unit-conversion coverage) on a Windows 11 PC with
WSL2 and Docker Desktop, without blocking daily use of the machine.

Target machine used for the sizing below: Intel i7-11800H (8 cores / 16 threads), 128 GB RAM, 1 TB NVMe
(≈300 GB free). Everything in this folder (`docker/mimic-local/`) is self-contained:

| File | Purpose |
|---|---|
| `docker-compose.yml` | onFHIR (CDM definitions) + Ignifyr server/web + nginx + Elasticsearch/Fluentd/Kibana, with CPU/memory limits |
| `ignifyr-server.conf` | Spark local mode, partition/chunk sizes, coverage plugin enabled |
| `logback.xml` | Same as the enterprise default, plus coverage events in the audit file and bigger rolling files |
| `nginx.conf` | Reverse proxy: `http://localhost:6090/dt4h/ignifyr/` (UI), `/dt4h/ignifyr/kibana/` (Kibana) |
| `run-job.sh` | Starts the job (optionally `--skip-write`, optionally a subset of mapping tasks) through the REST API |
| `extract_metrics.py` | Turns the `MAPPING_JOB_RESULT` / `MAPPING_COVERAGE` / `MAPPING_RESULT` events into CSV/XLSX tables |
| `analyze_output.py` | Output quality metrics from the written NDJSON (duplicates, referential integrity, code-system and UCUM shares) |
| `MIMIC-mapping-analysis.md` | Analysis of the mappings on a 1-in-20 real-data subset: ratios, coverage, output quality, residual gaps |

## 0. What is different from the Feb-2026 cluster run

Mapping/concept-map changes made together with this plan (see the git diff of `mappings/mimic`,
`mapping-contexts/mimic`, `terminology-systems/MIMICTerminologyService`, `schemas/mimic/ext-prescriptions.json`):

* **labevents**: the precondition no longer requires a non-empty `valueuom`, so textual results (urine color,
  drug screens, specimen type …) and unit-less numerics (INR, specific gravity, pH …) are mapped instead of being
  dropped (≈ 8 M rows in v3.1). Units are normalised to UCUM per lab item through the new unit-conversion
  context `lab-unit-conversion.csv` (`mpp:convertAndReturnQuantity`, so UNIT coverage is reported); reference
  ranges use the same unit; coded textual values extended (`labitem-coded-values-to-loinc.csv`).
* **prescriptions**: the dosage period start/end logic was inverted (start was always the stop time); `basedOn`
  pointed to ServiceRequests that are never generated (medication POEs are excluded); `doses_per_24_hrs` is
  now an integer (valid `frequency`); medication now carries NDC, RxNorm ingredient, ATC (`atcCode` slice) and
  the DT4H medication group (`atcCodeGroup` slice, from ATC prefixes); dose units cover all 135 unit strings.
* **emar**: route/site were read from the child `emar_detail` rows where they are always empty – they now come
  from the parent row (fallback: prescription route); typos in the route template (`target_display)`) and the
  use of the whole prescription list instead of the current prescription for the NDC lookup are fixed; dose
  units are coded; NDC/RxNorm/ATC/medication-group codings added; event text is trimmed before status lookup.
* **ndcToMedDetails.csv** keys are now 11-digit zero-padded like `prescriptions.ndc` and 850 NDCs that were
  missing were resolved through RxNav, so 6 297 of the 6 587 distinct prescription NDCs resolve (previously 2 773).
  `rx-norm-to-atc.csv` was regenerated from RxNav for all ingredient RxCUIs (1 211 of 1 369 now have ATC codes,
  previously 566).
* **procedures-icd**: ICD-9-CM procedure codes were labelled with the ICD-10-PCS system; they now use
  `http://hl7.org/fhir/sid/icd-9-cm`, a GEM-based ICD-10-PCS coding (`icdCode` slice) is added and rows without
  any PCS equivalent are skipped (HFR-Procedure requires ICD-10-PCS); the fixed "Surgical procedure" category
  became SNOMED 71388002 "Procedure".
* **diagnoses-icd**: the `icd10Code` slice name is no longer attached to the ICD-9-CM coding; `recordedDate`,
  identifier and `seq_num` extension added.
* **admissions**:
  * Encounter class: `OBSENC` for observation stays, `IMP` otherwise (now also `OBSERVATION ADMIT`), plus `priority`.
  * `admitSource`: if `edregtime` exists, it is `emd`; otherwise `admission_location` is converted with
    `admission-location-to-hl7.csv` (`CLINIC REFERRAL` and `AMBULATORY SURGERY TRANSFER` are now `outp`).
  * Services go to `serviceType` (SNOMED) and `type`.
  * The principal (seq_num 1) diagnosis is the admitting diagnosis; procedures are no longer encounter reasons.
* **patients**: HFR-Ethnicity extension (race → CDM SNOMED value set) and `communication.language` (BCP-47).
* **poe / microbiologyevents / careunits / omr**: SNOMED fixes and additions in `order-types` / `order-subtypes`,
  wrong `ad` comparator removed, organism / antibiotic components restructured, missing care units added,
  weight/height converted to kg/cm, numeric guards for `.`-valued rows.
* **Job**: gzip-compressed NDJSON per resource type; `labevents`, `emar`, `poe` and `prescriptions` are read in
  `subject_id` batches (`batchingStrategy`) instead of the manual `folder_0..9` split of the emar inputs.

The smoke test on the 2024-11 sample (`C:\development\data\mimic-iv-3.1\test\hosp`) runs all 12 mapping tasks
without mapping errors except for sample artefacts (rows whose admission is missing from the sample).

## 1. One-time preparation

### 1.1 Keep the laptop alive: temperature first, then VM size and CPU share

**Do this before the first full run; it is not optional.** On 3 Oct 2026 the machine (ThinkPad T15g Gen 2i,
i7-11800H + RTX 3070) froze three times while a heavy task group was running: display garbled, no input, hard
reset needed. The third run was instrumented and showed the cause: the CPU package reached **99 °C within one
minute** of the Spark load (8 worker threads, Turbo Boost on, all cores at 160 % of nominal clock) and stayed
there for the 88 minutes until the machine hung – this time the Linux side stopped at the same second as
Windows, i.e. the whole system halted. Memory (80 GB free), standby and the Linux side are ruled out. Measures,
from most to least important:

1. **Disable Turbo Boost** for the duration of the runs. `PROCTHROTTLEMAX 99` (maximum processor state) does
   *not* stop boosting on this firmware; use the hidden "processor performance boost mode" setting (PowerShell,
   no admin needed):

   ```powershell
   powercfg -attributes SUB_PROCESSOR be337238-0d82-4146-a960-4f3749d470c7 -ATTRIB_HIDE   # make the setting visible
   powercfg /setacvalueindex SCHEME_CURRENT SUB_PROCESSOR be337238-0d82-4146-a960-4f3749d470c7 0   # 0 = Disabled
   powercfg /setactive SCHEME_CURRENT
   ```

   Verify under load that `% Processor Performance` stays ≤ 100 and the thermal zone well below 90 °C:

   ```powershell
   Get-Counter '\Thermal Zone Information(*)\Temperature','\Processor Information(_Total)\% Processor Performance'
   ```

   (temperature in kelvin; 363 K = 90 °C). Re-enable with value 2 (Aggressive) afterwards. Also check in Lenovo
   Vantage that the thermal mode is "Performance" or "Balanced", not "Quiet", lift the rear of the laptop for
   airflow, and have the fans/heatsink cleaned if the idle temperature is already above 60 °C.
2. **Six Spark threads, not eight** (the shipped setting since 3 Oct): `cpus: "6.0"` in `docker-compose.yml`,
   `master = "local[6]"` and `numOfPartitions = 18` in `ignifyr-server.conf`. Two of the eight physical cores stay
   idle, which lowers the package power and keeps Windows responsive (≈ 25 % slower than 8 threads). If the
   temperature is still above 90 °C: `cpus: "4.0"` / `local[4]` / 12 partitions. Keep the three values in sync
   (partitions = 3 × threads).
3. `C:\Users\<you>\.wslconfig` – cap the VM at 80 GB and 10 of the 16 logical CPUs, and let the page cache go
   back to Windows:

   ```ini
   [wsl2]
   memory=80GB
   processors=10
   swap=8GB
   localhostForwarding=true

   [experimental]
   autoMemoryReclaim=gradual
   ```

   then `wsl --shutdown` and restart Docker Desktop. The compose file gives the Spark JVM 6 of these 10 vCPUs
   (`cpus: "6.0"`, `local[6]`, 18 partitions); Windows keeps ≥ 48 GB and at least 8 threads.
4. Power timeouts for the duration of the runs: `powercfg /change monitor-timeout-ac 0`,
   `powercfg /change standby-timeout-ac 0`, `powercfg /change hibernate-timeout-ac 0` (hibernation was set to
   180 min and would kill a long run). Keep the laptop on its own 230 W adapter, not powered through the dock.
5. Leave a host trace running during every long run so that a further incident can be diagnosed (the file keeps
   growing while the OS is alive; the last minute is lost in a hard reset because the writes are buffered):

   ```powershell
   typeperf "\Processor Information(_Total)\% Processor Time" "\Processor Information(_Total)\% Processor Performance" "\Processor Information(_Total)\% DPC Time" "\Thermal Zone Information(*)\Temperature" "\Memory\Available MBytes" "\Process(vmmemWSL)\% Processor Time" "\Process(dwm)\% Processor Time" -si 15 -f CSV -y -o "$env:USERPROFILE\mimic-hostperf.csv"
   ```

   On the Linux side a loop with `docker stats --no-stream` and `free -m` every 15 s into
   `~/ignifyr-mimic/hostperf-linux.log` shows whether the containers outlived the Windows side (they did in the
   first two incidents, not in the third).

Before the first long run after these changes, start the job for ten minutes and watch the temperature; only
continue if it settles below 90 °C. In Docker Desktop → Settings → Resources turn **Resource Saver off** while a
run is active (it pauses the VM when the UI is idle) and keep the WSL2 backend.

Expect about 1.6× the run times estimated in section 4 with 6 threads and no Turbo Boost (labevents ≈ 19 h
instead of 12 h, the whole heavy group ≈ 30 h); 4 threads add another ≈ 50 %. Start labevents/emar in the evening; the first chunk
result in the Ignifyr UI appears after 2–3 minutes.

### 1.2 Put the MIMIC CSVs on the WSL2 ext4 file system

Reading 18 GB `labevents.csv` through `/mnt/c` (9p bridge) is 5–10x slower than from ext4. Copy once
(~50 GB, 10–20 min):

```bash
mkdir -p ~/mimic-iv-3.1 && cp -r /mnt/c/development/data/mimic-iv-3.1/hosp ~/mimic-iv-3.1/
mkdir -p ~/mimic-iv-3.1-sample && cp -r /mnt/c/development/data/mimic-iv-3.1/test/hosp ~/mimic-iv-3.1-sample/
```

Check with `df -h ~` that the WSL virtual disk has room (it grows automatically; the default max is 1 TB).

### 1.3 Build the images with the observability module

The jar in `ignifyr-enterprise/ignifyr-server/target` was built on 24 Sep, **before** the coverage commit
(`a225aa7`, 1 Oct): it does not contain `ObservabilityExtension`, so no `MAPPING_COVERAGE` events are produced.
Rebuild, then build the three enterprise images (from the `ignifyr-enterprise` root):

```bash
mvn -B -DskipTests install
unzip -l ignifyr-server/target/ignifyr-server-standalone.jar | grep ObservabilityExtension   # must list the class
bash docker/server/build.sh      # -> docker.srdc.com.tr/srdc/ignifyr-server:latest
bash docker/fluentd/build.sh     # -> docker.srdc.com.tr/srdc/ignifyr-fluentd:latest
bash docker/kibana/build.sh      # -> docker.srdc.com.tr/srdc/ignifyr-kibana:latest (imports the Mapping Coverage dashboard)
docker pull docker.srdc.com.tr/srdc/ignifyr-web:dt4h   # or build: docker build -f docker/Dockerfile --build-arg BUILD_ENV=dt4h --build-arg BASE_HREF=/dt4h/ignifyr/ -t docker.srdc.com.tr/srdc/ignifyr-web:dt4h . (in ignifyr-web)
docker pull srdc/onfhir:r5
```

Build on Windows (PowerShell) if Maven/Docker are set up there; the images end up in the same Docker Desktop
daemon either way.

### 1.4 Create the workspace in WSL

```bash
mkdir -p ~/ignifyr-mimic && cd ~/ignifyr-mimic
git clone /mnt/c/development/dt4h/data-ingestion-suite     # the checkout with the changes above (commit them first)
git clone /mnt/c/development/dt4h/common-data-model
chmod +x data-ingestion-suite/docker/mimic-local/run-job.sh
mkdir -p ignifyr-docker-logs/spark-events     # Spark refuses to start when the event-log directory does not exist
```

(`projects.json` in the suite root already contains project `mimic` with the two MIMIC jobs.)

## 2. Start the stack

```bash
cd ~/ignifyr-mimic
export MIMIC_DATA_DIR=$HOME/mimic-iv-3.1-sample      # sample first, full data later
docker compose -f data-ingestion-suite/docker/mimic-local/docker-compose.yml --project-directory . -p mimic-local up -d --wait
```

Checks:

* `curl -s localhost:6080/fhir/metadata | head -c 300` – onFHIR with the CDM profiles is up.
* `curl -s -X OPTIONS localhost:6085/ignifyr -o /dev/null -w '%{http_code}\n'` → 200.
* `docker logs mimic-ignifyr-server | grep "Loaded .* extension"` must list `observability`.
* Web UI: <http://localhost:6090/dt4h/ignifyr/> → project **mimic** → Executions. Kibana:
  <http://localhost:6090/dt4h/ignifyr/kibana/> (dashboards *Executions*, *Execution Details*, *Mapping Coverage*).
* Spark UI of the running application: <http://localhost:4040> (only while a job runs).

## 3. Smoke test on the sample (≈5 minutes)

```bash
data-ingestion-suite/docker/mimic-local/run-job.sh --skip-write
```

Expected (sample of 2024-11): all 12 tasks finish with SUCCESS/PARTIAL_SUCCESS in about 4 minutes;
`patient-mapping` 14 997 Patients, `admissions-mapping` 5 334 Encounters, `diagnoses-mapping` 12 769,
`procedures-mapping` 19 276, `labevents-mapping` 41 110, `prescriptions-mapping` 50 000, `poe-mapping` 3 716,
`emar-mapping` 8 300, `omr-mapping` 68 178, `microbiologyevents` 97 495 (DiagnosticReport + Specimen + Observation).
The only errors are sample artefacts (rows whose admission is not in the sample, one `somedate` test value).
Open *Execution Details* in Kibana for the execution id printed by the script, then *Mapping Coverage*: the
terminology/concept/unit tiles must be filled. If the coverage dashboard stays empty, the server image was built
from a jar without the observability module (step 1.3).

Then extract the same numbers from the command line:

```bash
python3 data-ingestion-suite/docker/mimic-local/extract_metrics.py --es http://localhost:9200 --out ./metrics-sample
```

## 4. Full run, phase A — metrics only (`skipWrite`)

Point the stack at the full data and restart the server container only:

```bash
export MIMIC_DATA_DIR=$HOME/mimic-iv-3.1
docker compose -f data-ingestion-suite/docker/mimic-local/docker-compose.yml --project-directory . -p mimic-local up -d ignifyr-server
```

Run the small tasks first (they finish within the hour and validate the full-size joins), then the big ones.
Each call returns immediately; the tasks of one call run sequentially, one Spark application per call.

```bash
R=data-ingestion-suite/docker/mimic-local/run-job.sh
$R --skip-write patient-mapping careunit-mapping admissions-mapping diagnoses-mapping procedures-mapping omr-mapping microbiologyevents medications-mapping
# when finished (Kibana Executions → result column, or curl .../executions):
$R --skip-write prescriptions-mapping poe-mapping
$R --skip-write labevents-mapping
$R --skip-write emar-mapping
```

Measured on a 1-in-20 patient subset of the real data (8 threads, 28 GB heap, writing gzip NDJSON, see
`MIMIC-mapping-analysis.md`): labevents ≈ 3 800 rows/s, emar ≈ 2 100 rows/s, prescriptions ≈ 1 800 rows/s,
poe ≈ 16 600 rows/s. Extrapolated to the full hosp module: labevents ≈ 12 h, emar ≈ 6 h, prescriptions ≈ 3 h,
poe ≈ 1 h, all other tasks together ≈ 1.5 h, i.e. ≈ 23 h with writing enabled and less with `--skip-write`.
Start labevents and emar in the evening. While they run, the
container stays within its 6 CPUs / 56 GB.

Monitoring during the run:

* `docker stats mimic-ignifyr-server` (CPU should sit near 800 %, memory < 56 GB).
* Spark UI <http://localhost:4040> → Stages (task skew, spills), Executors (memory).
* Kibana *Execution Details*: chunk progress (`k / N` chunks, `batch i / M`), invalid / not-mapped counters,
  mapping errors table (first errors appear within minutes if a FHIRPath expression is wrong).
* `tail -f ignifyr-docker-logs/ignifyr-server.log`.

If memory pressure appears (GC storms in the Spark UI, container restarts): lower `maxChunkSize` to 150000 and
`numOfPartitions` to 16 in `ignifyr-server.conf`, or raise the number of `batchParameterSets` for the task in
the job file (e.g. 20 batches of 500 000 subject ids), then `docker compose ... restart ignifyr-server`.

## 5. Full run, phase B (optional) — write the resources

Only needed if you want output sizes or to load the resources somewhere. Run the same calls without
`--skip-write`. The sink is gzip NDJSON partitioned by resource type under
`$MIMIC_DATA_DIR/output/<ResourceType>/`; expect ≈ 30–40 GB compressed (the uncompressed cluster output was
≈ 270 GB). If you need the uncompressed size for the paper, compute it from a sample
(`zcat part-*.gz | wc -c` on one chunk file) or remove `"compression": "gzip"` from the job's `sinkSettings.options`
and write to a disk with > 300 GB free (bind-mount it instead of `mimic_spark_tmp`/data volume).

## 6. Collect the metrics for the paper

```bash
python3 data-ingestion-suite/docker/mimic-local/extract_metrics.py --es http://localhost:9200 --out ./metrics-full
# backup source if Elasticsearch lost events: the audit files written by logback
python3 data-ingestion-suite/docker/mimic-local/extract_metrics.py --log ignifyr-docker-logs/ignifyr-mappings.log ignifyr-docker-logs/ignifyr-mappings.*.log.zip --out ./metrics-from-log
```

`job_results.csv` gives per mapping task: start/end, wall-clock duration (difference between the STARTED event
and the final `chunkResult:false` event – Ignifyr emits no duration field), number of chunks and batches,
generated resources, invalid rows (schema violations), not-mapped rows (expression errors), resources per
second. `chunk_results.csv` gives the per-chunk timeline (throughput plots). `coverage_summary.csv` gives, per
mapping task × lookup type (TERMINOLOGY = `trms:translate*`/`lookup*`, CONCEPT = `mpp:getConcept`,
UNIT = `mpp:convertAndReturnQuantity`) × concept map, the number of lookups, successful lookups and coverage
in percent; `coverage_unmapped.csv` lists the unmapped source codes/units with their lookup counts (the material
for a "residual gaps" table). Input row counts per source table are not emitted by Ignifyr; use the row counts
of the CSV files (e.g. `wc -l` or the profiling numbers in the paper folder's `coverage-prepass`).

Interpretation notes for `coverage_summary.csv`:

* `icd9toicd10cmgem.csv` / `icd9toicd10pcsgem.csv`: when the full ICD-9 code has no usable GEM entry, the mapping
  retries with the 4- and 3-character prefixes and every attempt is counted. The lookup coverage (95.9 % for
  diagnoses on the full data) therefore understates the row coverage: the number of ICD-9 rows that end without
  an ICD-10 code equals the number of failed 3-character probes in `coverage_unmapped.csv` (62 618 of ≈ 2.8 M
  ICD-9 diagnosis rows ⇒ 97.8 % row coverage). The residual rows are GEM "NoDx" codes (E930–E949 adverse
  effects, 707.2x pressure-ulcer stages, V64.41) and category-level codes used by MIMIC (0414, 2841, 9974).
* `atc-to-medication-group-concept-map.csv` is probed with every ATC prefix (3, 4, 5 and 7 characters) of every
  ATC code, so its "coverage" is the share of prefix probes that hit one of the 18 DT4H medication groups
  (≈ 8 % is expected: most drugs are not heart-failure drugs). Report it as "prescriptions tagged with a CDM
  medication group", not as a terminology gap.
* `ndcToMedDetails.csv` lookups happen once per prescription/administration row with a non-zero NDC, so the
  coverage is row-weighted (95 % on the sample); the distinct-NDC coverage is 6 297 / 6 587. The CSV carries every
  NDC both zero-padded and unpadded because Spark reads the `ndc` column of prescriptions.csv as a number and the
  mapping's `ndc.toString()` therefore loses the leading zeros.
* The `labitems-to-loinc` failures are dominated by the three MIMIC placeholder items `50934 H`, `51678 L`
  and `50947 I` (≈ 8 M rows in v3.1) that have no clinical meaning; list them separately in the paper.

Kibana dashboards can be exported as PNG for figures; the Spark event logs under
`ignifyr-docker-logs/spark-events` can be opened with a Spark History Server
(`docker run -v $PWD/ignifyr-docker-logs/spark-events:/events -p 18080:18080 apache/spark:3.5.1 /opt/spark/sbin/start-history-server.sh`
with `SPARK_HISTORY_OPTS=-Dspark.history.fs.logDirectory=/events`) if per-stage timings are wanted.

## 7. Troubleshooting

* **`Problem while calling terminology service! Future timed out after [1 minute]` and the REST API (and `run-job.sh`)
  hang while a task runs**: the Akka dispatcher of the server has one thread per CPU visible to the container and the
  Spark worker threads exhaust it. `ignifyr-server.conf` raises `akka.actor.default-dispatcher` to 32–64 threads;
  keep it above `local[N]` if you change the CPU limit.
* **No coverage events / empty Mapping Coverage dashboard**: server jar built without `ignifyr-observability`
  (step 1.3), or Fluentd not reachable (`docker logs mimic-fluentd`). Coverage events are also in
  `ignifyr-docker-logs/ignifyr-mappings.log` thanks to `logback.xml`.
* **Elasticsearch stops indexing**: disk watermark (needs ≥ 10 % free on the WSL disk) – free space or set
  `cluster.routing.allocation.disk.threshold_enabled=false` on the ES service; check `curl localhost:9200/_cluster/health`.
* **`FIELD_NOT_FOUND ... in mainSource` right after a chunk of a multi-source mapping**: appears when
  `saveErroneousRecords` is true for a mapping with joined sources (engine issue observed with `patient-mapping`
  on the sample); the job keeps `saveErroneousRecords: false`, the erroneous rows are still logged as
  `MAPPING_RESULT` events with the source row.
* **Slow reads (minutes before the first chunk)**: data under `/mnt/c` → move to ext4 (step 1.2).
* **Container killed (exit 137)**: heap + Spark off-heap exceeded 56 GB → lower `-Xmx` to 40g and
  `maxChunkSize` to 150000.
* **Docker Desktop paused the VM**: Resource Saver (step 1.1).
* **Windows freezes (unresponsive, garbled display) while a heavy task runs**: the CPU package runs at its
  thermal limit (99 °C) under the Spark load with Turbo Boost on; the machine hangs after minutes to hours (three
  times on 3 Oct 2026). Apply step 1.1: disable Turbo Boost (boost mode 0), check the temperature under load,
  reduce the Spark threads if needed. After a hard reset the execution is gone (no checkpoint for batch CSV
  sources): start the remaining tasks again; nothing was written with `--skip-write`, and the tasks that had
  finished keep their events in Elasticsearch (check the Executions dashboard before re-running them).
