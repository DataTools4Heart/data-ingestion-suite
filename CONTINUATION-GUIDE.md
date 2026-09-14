# DT4H Data Ingestion & Feature Extraction
## Continuation Guide for Clinical Sites

*How to keep mapping your hospital data to the DT4H Common Data Model, and keep producing AI-ready datasets, after the project.*

---

## 1. What this guide is

This guide explains **how a clinical site keeps using the DT4H data tools on its own**.

It covers two things:

- **Data ingestion** — turning your hospital data into the DT4H Common Data Model (CDM).
- **Feature extraction** — turning CDM data into a tabular, AI-ready dataset.

**What it is not:** it is not an installation manual. The installation steps already live in the repository READMEs, and this guide links to them. Read this guide to understand *what to change and when*; follow the READMEs for the exact commands.

---

## 2. Who this guide is for

- The person at your site who runs the servers.
- The person who knows your hospital's source data — tables, columns, local codes.
- The person who will write or adjust mappings.

These can be three people or one person. Section 8 lists the skills needed.

---

## 3. The pipeline in one picture

```
   Your hospital data                DT4H tools                      Result
   (CSV files or SQL DB)
          |
          |  [1] INGESTION
          v
   +----------------+     mappings     +----------------------+
   |  toFHIR /      | ---------------> |  onFHIR              |
   |  Ignifyr       |                  |  (CDM data store,    |
   |  (ETL engine)  |                  |   FHIR resources)    |
   +----------------+                  +----------------------+
                                                  |
                                                  |  [2] EXTRACTION
                                                  v
                                       +----------------------+
                                       |  feast               |     CSV / Parquet
                                       |  (feature extraction)| --> tabular dataset
                                       +----------------------+     for ML training
```

Two steps, two tools, two repositories of configuration. Nothing else is required.

---

## 4. The three repositories

| Repository | What it holds | Do you edit it? |
|---|---|---|
| [`common-data-model`](https://github.com/DataTools4Heart/common-data-model) | The FHIR profiles, code systems and value sets that define the CDM. Shared by all sites | No — shared, read only |
| [`data-ingestion-suite`](https://github.com/DataTools4Heart/data-ingestion-suite) | Mapping definitions per site, plus Docker deployment for toFHIR/Ignifyr and onFHIR | **Yes** — your site's folders |
| [`feature-extraction-suite`](https://github.com/DataTools4Heart/feature-extraction-suite) | Populations, feature groups, feature sets, and the feast deployment | Sometimes — for new cohorts or features |

Everything is public on GitHub. Clone it, keep it, and pull updates when you need them.

---

## 5. What your site owns

Inside `data-ingestion-suite` there are four folders, each with one subfolder per site
(`amc`, `buch`, `gem`, `i2b2`, `icrc`, `mimic`, `nlp`, `rs-chd`, `rs-nbhw`, `rs-sodersjukhuset`, `ucl`, `umcu`, `umcu-uc2`, `vhir`).

**Only your own subfolder is yours.** Example for the `buch` site:

| Folder | What is in it |
|---|---|
| `schemas/buch/` | The shape of your source data — one file per source table |
| `mappings/buch/` | The transformation scripts — one file per CDM resource type |
| `mapping-contexts/buch/` | Lookup tables (CSV) for local codes and units |
| `mapping-jobs/buch/` | Where the data comes from, and where results are written |

If you change nothing outside your own subfolders, you can never break another site.

---

## 6. The four building blocks of ingestion

**1. Schema** — describes one source table, so the engine knows which columns exist.
`schemas/buch/nyha.json` declares fields such as `patient_id`, `admission_id`, `value`, `unit`, `admission_date`.

**2. Mapping** — the actual transformation script. It reads schema fields and writes CDM fields, using FHIRPath expressions.
`mappings/buch/nyha-mapping.json`

**3. Concept map** — a simple CSV lookup table, used to convert local codes and units into standard ones.
`mapping-contexts/buch/nyha-mappings.csv`:

```csv
"source_code","target_code","target_display"
"I","LA28404-4","Class I"
"II","LA28405-1","Class II"
```

**5. Mapping job** — ties everything together: the input location, the output target, and the list of mappings to run.
`mapping-jobs/buch/buch-deploy.json` points at a source data folder and writes to the onFHIR server.

---

## 7. The three building blocks of extraction

**1. Population** — which patients belong to the cohort.
`definitions/population/study1.json`

**2. Feature group** — a group of raw features for one kind of resource: labs, medications, vital signs. It produces a time-series table. One matching record becomes one row; one defined feature becomes one column.
`definitions/featuregroup/lab-results-fg.json`

**3. Feature set** — turns the time-series tables into the final flat dataset. It picks reference time points, groups data into time windows, and applies aggregations.
`definitions/featureset/study1.json`

Extraction is started with a single `curl` request. The result appears under
`feature-extraction-suite/output-data/myFhirServer/...`

---

## 8. Skills you need

| Skill | Needed for | How much |
|---|---|---|
| Linux and Docker basics | Starting, stopping and updating the tools | Essential |
| Git basics (`clone`, `pull`, `commit`) | Getting updates, keeping your changes | Essential |
| Knowledge of your own source data | Every mapping change | Essential |
| SQL or CSV handling | Preparing and checking source data | Essential |
| Reading JSON | All configuration files | Essential |
| FHIRPath expressions | Writing or changing a mapping | For mapping work |
| Basic HL7 FHIR concepts | Understanding what the CDM expects | For mapping work |

You do not need to be a FHIR expert to re-run the pipeline. You do need FHIRPath to change a mapping.

---

## 9. Scenario map: what can I do, and how hard is it?

| # | Scenario | Difficulty | What you touch |
|---|---|---|---|
| A | New data export, same structure — re-run ingestion | **Easy** | Nothing; just run the job |
| B | A new local code or unit appeared | **Easy** | One CSV in `mapping-contexts/` |
| C | Re-produce an existing study dataset | **Easy** | Nothing; one `curl` request |
| D | Source table changed: new, renamed or removed column | **Moderate** | `schemas/` + `mappings/` |
| E | A new cohort, or new features in the dataset | **Moderate** | `population/`, `featuregroup/`, `featureset/` |
| F | Move to a new CDM version or new tool images | **Advanced** | Full pull, re-map, re-extract |

Scenarios A to C are routine operations. D and E are configuration work. F is a migration.

---

## 10. Scenario A — Re-run ingestion on refreshed data

Your hospital produced a new export, and the structure is unchanged.

1. Place the new files where your mapping job expects them — see `sourceSettings.dataFolderPath` in `mapping-jobs/<site>/<site>-deploy.json`.
2. Open `http://<hostname>/dt4h/tofhir` in a browser.
3. Select your project → **Open** → **Executions**.
4. Click the green arrow next to your `-deploy` job.
5. Click the double-right-arrow to select all mappings, then **Run**.
6. Use **Refresh** to follow progress.

Check the logs at `http://<hostname>/dt4h/tofhir/kibana` (menu → *Discover*).

> If you want an empty data store first, see *Clean Installation from Scratch* in the [ingestion README](https://github.com/DataTools4Heart/data-ingestion-suite#clean-installation-from-scratch-optional). **This deletes all previously mapped data.**

---

## 11. Scenario B — A new local code or unit appeared

Your data now contains a code that was not there before, so it is not converted.

1. Find the relevant CSV in `mapping-contexts/<site>/`.
2. Add one row: the local code, the standard target code, and the display name.
3. Save, then re-run the mapping job as in Scenario A.

Concept maps are plain CSV files. This is the safest and most common change a site will make.

*Tip: the Kibana logs show which codes were not matched. That list tells you exactly which rows to add.*

---

## 12. Scenario C — Re-produce an existing study dataset

The CDM store is populated, and you want the dataset again — for example after new data was ingested.

Send one request, replacing `<hostname>` and `<basePath>`:

```bash
curl --request POST --url 'http://<hostname>/<basePath>/feast/api/DataSource/myFhirServer/FeatureSet/study1-fs/Population/study1_cohort/$extract?entityMatching=pid|pid,encounterId|encounterId&reset=true' --header 'Content-Type: application/json' --data '{ "name": "Study1" }'
```

Ready-made requests for every DT4H study — Study 1, 2, 3, MAGGIC-MLP, CARE-HEART — are in the [extraction README](https://github.com/DataTools4Heart/feature-extraction-suite#starting-feature-extraction).

Extraction can take a long time on large datasets. The result is written to `output-data/`.

---

## 13. Scenario D — Your source table changed

A column was added, renamed or removed in your hospital export.

1. **Update the schema.** Open the matching file in `schemas/<site>/` and add, rename or remove the field. Keep the type correct: `string`, `date`, `integer`.
2. **Update the mapping.** Open the matching file in `mappings/<site>/` and change the FHIRPath expressions that referenced the old field.
3. **Test on a small sample first.** Point the mapping job at a small folder, run it, and read the Kibana log.
4. **Run on the full data** once the sample is clean.

Use the toFHIR/Ignifyr web interface for this. It has a FHIRPath editor that lets you test an expression against a sample record before saving.

> Compare with another site's files in the same repository. They solve the same problem for different source data, and make good examples.

---

## 14. Scenario E — A new cohort or new features

You want a dataset that does not exist yet.

**New cohort, same features:** copy an existing file in `definitions/population/`, change the inclusion criteria, give it a new id, and use that id in your extraction request.

**New features:** find the feature group for that resource type — for example `lab-results-fg.json` — and add the feature there. Then add it to the feature set, so it appears as a column in the final dataset.

**A new study:** create a population, a feature set, and a pipeline that connects them.

Copying an existing definition is almost always faster and safer than writing one from scratch. `study1.json`, `study2.json` and `study3.json` are complete, working examples.

---

## 15. Scenario F — Moving to a new CDM version or new images

The CDM, the mappings, or the tool images have been updated centrally.

1. Stop the containers: `sh ./data-ingestion-suite/docker/server/stop.sh`
2. Run `git pull` in `common-data-model`, `data-ingestion-suite` and `feature-extraction-suite`.
3. Pull the new images: `sh ./data-ingestion-suite/docker/server/pull.sh`
4. Start again: `sh ./data-ingestion-suite/docker/server/run.sh`
5. **Re-run your mappings**, then **re-run extraction**. A CDM change usually means the mapped data must be produced again.

Before you pull, commit your own site's changes to Git. Otherwise a pull can overwrite them.

---

## 16. Routine maintenance

**Monthly, or after any upstream change:**

```bash
cd common-data-model && git pull && cd ..
cd data-ingestion-suite && git pull && cd ..
cd feature-extraction-suite && git pull && cd ..
sh ./data-ingestion-suite/docker/server/pull.sh
sh ./data-ingestion-suite/docker/server/run.sh
```

**Good habits:**

- Keep your site's mapping changes in Git, and push them to the shared repository. This is how your work survives a server rebuild.
- Never keep real patient data in the repository. Only configuration belongs there.
- Note the CDM version you mapped against, together with each dataset you produce.
- Keep the source export that produced a dataset, so results can be reproduced.

---

## 17. How to know it worked

| Check | Where                                                                           |
|---|---------------------------------------------------------------------------------|
| Did the mappings run without errors? | Kibana: `http://<hostname>/dt4h/tofhir/projects/<site>/executions` → *Discover* |
| Did the data reach the CDM store? | onFHIR: `http://<hostname>/dt4h/onfhir/Patient?_summary=count`                  |
| What datasets exist? | `http://<hostname>/<basePath>/feast/api/Dataset`                                |
| Statistics for one dataset? | `http://<hostname>/<basePath>/feast/api/Dataset/<datasetId>`                    |
| Does the dataset meet quality rules? | `definitions/datasetqualitycriteria/` in the extraction suite                   |

A mapping run that reports no errors is not automatically correct. Always look at the record counts, and compare them with what you expect from the source data.

---

## 18. Prerequisites checklist

Before a site can work independently, confirm all of these:

- [ ] A Linux server with Git and Docker installed
- [ ] Enough disk space for the CDM store and the extracted datasets
- [ ] Local clones of `data-ingestion-suite`, `common-data-model` and `feature-extraction-suite`
- [ ] Access to the SRDC Docker registry for the `tofhir-web` image — this image is not on public Docker Hub; see the ingestion README
- [ ] Nginx configured, or the provided Nginx container running
- [ ] A named technical contact at the site, with Git write access
- [ ] A repeatable way to produce the source export from your hospital systems

The last point is the one most often forgotten. If only one person can produce the export, the pipeline stops when that person is away.

---

## 19. Where to find things

| What | Link |
|---|---|
| Ingestion — concepts, deployment, running mappings | [data-ingestion-suite README](https://github.com/DataTools4Heart/data-ingestion-suite#readme) |
| Extraction — concepts, deployment, study requests | [feature-extraction-suite README](https://github.com/DataTools4Heart/feature-extraction-suite#readme) |
| CDM — profiles, code systems, value sets, full Excel definition | [common-data-model README](https://github.com/DataTools4Heart/common-data-model#readme) |
| ETL engine — how it works, tutorial, FAQ | [ignifyr.io](https://ignifyr.io) (formerly toFHIR) |
| ETL engine — open source code | [github.com/srdc/tofhir](https://github.com/srdc/tofhir) |
| FHIR data repository | [onfhir.io](https://onfhir.io) |

---

## 20. Glossary

| Term | Plain meaning |
|---|---|
| **HL7 FHIR** | An international standard for representing health data |
| **CDM** | Common Data Model — the agreed DT4H shape for heart failure data |
| **Profile** | A rule set saying what a valid CDM resource looks like |
| **FHIRPath** | The small expression language used inside mappings |
| **Schema** | A description of one of your source tables |
| **Mapping** | The script that converts source records into CDM resources |
| **Concept map** | A CSV lookup table for codes and units |
| **Mapping job** | The configuration saying what to run, from where, to where |
| **toFHIR / Ignifyr** | The ETL engine that runs the mappings |
| **onFHIR** | The server that stores the CDM data |
| **feast** | The feature extraction engine |
| **Population** | The definition of which patients form a cohort |
| **Feature group** | Raw features extracted for one resource type |
| **Feature set** | The final tabular dataset definition |

---

## 21. Summary

- Everything you need is public on GitHub. Clone it, and keep it.
- Your site owns four folders. Nothing you do there can affect another site.
- Re-running ingestion and extraction on new data is routine work.
- Changing a schema or a mapping is the real skill, and it needs FHIRPath.
- Copy an existing, working definition instead of starting from an empty file.
- Commit your changes to Git, or they will be lost on the next rebuild.

---

## 22. Training and consultancy: Pontegra

[Pontegra Software Technologies](https://pontegra.com/) is a digital health engineering company founded by the SRDC team — the same people who built the DT4H ingestion and extraction tooling. It is headquartered in Rotterdam, the Netherlands, which matters when the data or the contract has to stay inside the EU.

It works with the same standards as your stack: HL7 FHIR, OMOP, SNOMED-CT, LOINC, ICD and ATC.

**What is relevant to a clinical site:**

| | What it covers |
|---|---|
| **Training** | Mapping and FHIRPath training for your own staff, so a new team member can take over schema and mapping work. |
| **Consultancy** | Supervised mapping and feature extraction — new cohorts, CDM upgrades, and EHDS-aligned secondary use. |
| **The same tooling, maintained** | Ignifyr is the ETL engine that already runs your DT4H mappings. Repofyr covers FHIR hosting and APIs. |

**Contact:** [pontegra.com](https://pontegra.com/) · info@pontegra.com · Brabantsestraat 16, 3074RS Rotterdam, Netherlands
