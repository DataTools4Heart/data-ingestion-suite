# MIMIC-IV v3.1 → DT4H CDM mappings: analysis on real data (2 Oct 2026)

Scope: the state of branch `feat/mimic-mapping-review-and-local-execution` after the review commits of 2 Oct
(microbiology id collision, admitSource handling, microbiology struct aggregation, synthetic test data) plus the
changes described in section 3. The analysis is driven by the metrics the paper reports (Table 3: input rows,
generated resources, duration, output size) and the metrics the re-execution will add (invalid / not-mapped rows,
terminology / concept-map / unit-conversion coverage, referential integrity, code-system coverage of the output).

**Reading guide.** Sections 1–6 document the state of October 2026 (subset analysis, first full-data runs on the
laptop) and are kept as the record of how the mappings evolved. The mappings changed afterwards (PR 39 and the
fixes of 9 Nov), so their counts and coverage figures are superseded. **Section 7 holds the definitive full-data
results (9 Nov 2026, commit `f398a16`) and the current list of residual gaps; use it for the paper.**

## 1. Real-data test: 1-in-20 patient subset

A consistent subset (every table restricted to `subject_id % 20 = 0`, 18 500 patients, 7.9 M lab rows,
4.3 M emar_detail rows) was mapped with the rebuilt enterprise jar in Spark local mode on the laptop
(8 threads, 28 GB heap, gzip NDJSON written, 2 `subject_id` batches per large task). All 12 tasks finished with
**SUCCESS, 0 invalid rows, 0 not-mapped rows**; total wall time 68 minutes; output 1.4 GB gzip.

| task | input rows | resources | ratio | ratio in Feb-2026 run | duration | rows/s | full data, extrapolated |
|---|---:|---:|---:|---:|---:|---:|---:|
| patient-mapping | 18 500 | 18 500 | 1.000 | 1.000 | 0:14 | 1 321 | 0.1 h |
| careunit-mapping | 45 | 45 | 1.000 | | 0:03 | | |
| admissions-mapping | 26 898 | 26 898 | 1.000 | 1.000 | 0:16 | 1 681 | 0.1 h |
| diagnoses-mapping | 311 374 | 308 347 | 0.990 | 0.990 | 1:30 | 3 460 | 0.5 h |
| procedures-mapping | 42 662 | 41 201 | 0.966 | 1.000 | 0:18 | 2 370 | 0.1 h |
| labevents-mapping | 7 891 394 | 6 553 167 | 0.830 | 0.796 | 34:48 | 3 779 | 11.6 h |
| medications-mapping | 8 250 | 8 250 | 1.000 | 0.641 | 0:05 | 1 650 | |
| prescriptions-mapping | 999 646 | 999 646 | 1.000 | 1.000 | 9:06 | 1 831 | 3.1 h |
| poe-mapping | 2 585 241 | 991 524 | 0.384 | 0.383 | 2:36 | 16 572 | 0.9 h |
| omr-mapping | 384 051 | 525 294 | 1.368 | 1.367 | 1:14 | 5 190 | 0.4 h |
| microbiologyevents | 200 167 | 393 561 | 1.966 | 1.965 | 0:52 | 3 849 | 0.3 h |
| emar-mapping | 2 102 665 | 2 068 000 | 0.984 | 0.983 | 16:59 | 2 063 | 5.8 h |

Total extrapolated: ≈ 23 h for the full hosp module with writing enabled on this laptop (`--skip-write` is
faster; the cluster run took ≈ 3 days). The ratios reproduce the Feb-2026 run except where a fix changed them
on purpose: labevents keeps the coded/text and unit-less results (+3.4 points), procedures drops ICD-9 codes
without any ICD-10-PCS equivalent (−3.4 points, HFR-Procedure conformance), medications now covers all distinct
drugs (also those without NDC).

### Lookup coverage on the subset (MAPPING_COVERAGE events)

| task | type | map | lookups | coverage |
|---|---|---|---:|---:|
| labevents | TERMINOLOGY | labitems-to-loinc | 7 891 394 | 92.9 % |
| labevents | UNIT | lab-unit-conversion | 6 822 512 | 93.4 % (before the LOINC guard, see 3.6) |
| labevents | TERMINOLOGY | labitem-coded-values-to-other | 266 331 | 63.2 % (idem) |
| prescriptions | CONCEPT | ndcToMedDetails | 873 970 | 95.7 % |
| prescriptions | TERMINOLOGY | rx-norm-to-atc | 836 279 | 93.3 % |
| prescriptions | TERMINOLOGY | medication-dose-to-orderable-drug-form | 999 220 | 99.9 % |
| prescriptions | TERMINOLOGY | medication-route-codes-to-snomed | 999 319 | 100.0 % |
| emar | CONCEPT | ndcToMedDetails | 5 370 870 | 97.4 % |
| emar | TERMINOLOGY | rx-norm-to-atc | 3 487 052 | 95.3 % |
| emar | CONCEPT | emar-status | 2 080 254 | 99.9 % |
| poe | TERMINOLOGY | order-subtypes-to-snomed | 991 524 | 76.5 % |
| diagnoses | CONCEPT | icd9toicd10cmgem | 145 983 | 95.9 % |
| procedures | CONCEPT | icd9toicd10pcsgem | 25 267 | 99.9 % |
| microbiologyevents | TERMINOLOGY | test-itemids-to-loinc | 200 167 | 99.98 % (after 3.1) |
| microbiologyevents | TERMINOLOGY | specimen-types-to-hl7 | 96 697 | 99.9 % (after 3.2) |
| microbiologyevents | TERMINOLOGY | org-itemids-to-snomed | 82 593 | 96.4 % |
| patients | CONCEPT | race-to-ethnicity | 11 355 | 95.0 % |
| admissions, careunits, omr | all maps | | | 100 % |

### Output quality (12 M resources scanned)

* Duplicate ids: 0 in every resource type except MedicationAdministration (147 of 2 068 000, 0.007 %: emar rows
  linked to two prescription rows with identical gsn/ndc/formulary code; a FHIR server would merge them).
* Referential integrity: all Patient, Encounter, Location, Medication, MedicationRequest, Specimen and
  Observation references resolve inside the output. Unresolved: 3 027 of 311 374 Encounter.diagnosis targets
  (1 %, the ICD-9 diagnoses skipped for lack of an ICD-10 equivalent) and 712 of 225 042 `ServiceRequest.replaces`
  targets (orders of the excluded Lab/Medications POE types).
* Code systems: 100 % of Conditions carry ICD-10, 100 % of Procedures ICD-10-PCS, 100 % of lab and vital-sign
  Observations LOINC, 97.3 % of lab Observations a UCUM-coded quantity (0.3 % valueString, 2.5 %
  valueCodeableConcept), 78 % of MedicationRequests and 80 % of MedicationAdministrations an ATC code, 16 % a DT4H
  medication group, 76.5 % of ServiceRequests a SNOMED code, 95.8 % of Specimens an HL7 v2 type.
* Encounter.class: IMP 73 %, OBSENC 27 % (after the review decision to map ED admissions to IMP + priority EM).
* Microbiology Observations: 61 % have no value element (cultures without organism/dilution; the free-text
  result stays in `note`). This is a known gap of the current microbiology design, see 4.

## 2. Review of the 2 Oct fixes

* Microbiology Observation ids suffixed with `'microbiology'`: correct and necessary (labevent_id and
  microevent_id ranges overlap). Verified: 0 duplicate Observation ids on the subset.
* `struct`/`array_sort` aggregation in the microbiology `preprocessSql`: correct, removes the positional
  misalignment of parallel `array_agg` columns when some values are null. The `Ext-microbiologyevents` schema
  still described the old flat columns; it now declares the `events` BackboneElement array (section 3.3).
* `admitSource = emd` when `edregtime` exists, otherwise the translated `admission_location`: correct; coverage
  100 %.
* Encounter class without `EMAMB` (CodeSystem removed from the CDM): consistent with the CDM main branch.
* `test-itemids-to-loinc` fix: the mapping now filters empty codings, but the map itself still had codes for only
  11 of 182 tests, i.e. 2.9 % of microbiology rows would carry a LOINC code. See 3.1.
* `prescriptions` precondition `%ndcCode.exists()`: avoids dangling `Medication` references, but drops
  2 552 081 of 20 292 611 prescriptions (12.6 %, mostly IV fluids and flushes whose NDC is 0 in MIMIC). Replaced
  by section 3.4, which keeps those MedicationRequests with a resolvable Medication reference.

## 3. Issues found and fixed in this round

1. **Microbiology LOINC coverage 2.9 % → 99.98 %.** Added LOINC codes for the 26 most frequent test items (urine,
   blood, wound, respiratory, fluid, anaerobic, fungal, stool, campylobacter, throat cultures, Gram stains, MRSA
   screen, C. trachomatis / N. gonorrhoeae NAAT, RPR, O+P, C. difficile toxin, HCV viral load, VZV / rubella IgG,
   viral culture …), chosen only where the LOINC concept is unambiguous. Tests still without a code (Legionella
   urinary antigen, KOH prep, group B strep, H. pylori Ab, CMV / HIV viral load, respiratory viral antigen,
   Cryptosporidium/Giardia DFA, BV smear …) are listed in `coverage_unmapped.csv`; they are < 7 % of rows.
2. **Specimen types**: 11 missing `spec_itemid`s (227 K rows, mainly SWAB, URINE, TISSUE, FLUID) added →
   99.9 %.
3. **Microbiology source schema** rewritten for the aggregated structure (`events` array with the per-row
   columns).
4. **Prescriptions without NDC are kept**: `medications-without-rxn` now creates a Medication for every distinct
   drug (NDC coding only when an NDC exists, GSN identifier and formulary code otherwise), `prescriptions` and
   `emar` reference it and emit `medication.concept` only when an NDC exists (HFR requires `concept.coding` when
   `concept` is present). Result on the subset: 999 646 / 999 646 prescriptions mapped, 100 % of Medication
   references resolve.
5. **NDC codes lose their leading zeros**: Spark infers `prescriptions.ndc` as a number, so `ndc.toString()`
   yields e.g. `169750111` instead of `00169750111`. The lookup keys already exist in both forms; the NDC
   *codings* in Medication, MedicationRequest and MedicationAdministration are now left-padded to 11 digits in
   FHIRPath (explicit length cases; note that a chained call on a parenthesised concatenation such as
   `('000' & x).substring(n)` is not applied by the engine's FHIRPath evaluator, the padding therefore uses `iif`).
   Verified on the sample: 95.4 % of NDC lookups resolve with the padded key, unmapped keys are now 11-digit.
6. **Coverage metrics were inflated by rows that the precondition later drops**: the labevents variables were
   evaluated for every row, so the unit-conversion and coded-value lookups were also counted for the ~8 M
   placeholder rows (items `H`, `L`, `I`, `HOLD` tubes) that have no LOINC and never become resources. The
   lookups are now guarded by `%convertedLabLoinc.exists()`, so UNIT and coded-value coverage will be reported
   over mapped rows only (expected ≈ 99 % and ≈ 85 %).
7. Dose-unit and route maps extended with the residual strings seen on the subset (`CADD`, `PUMP`, `Unit`,
   `Units Regular/Humalog/Glargine`, `ng`, lowercase `po`, `NGT`, `OGT`).

## 4. Residual gaps to state in the paper (not fixed, by design or data-limited)

(State of October 2026 on the subset. The current figures and gaps are in section 7.3 and 7.4.)

* `labitems-to-loinc`: 7.1 % of lab rows have no LOINC — 70 % of them are the three placeholder items
  `50934 H`, `51678 L`, `50947 I` (≈ 8 M rows, no clinical meaning), the rest are tube-hold / specimen-type
  items (`Green Top Hold`, `Blue Top Hold`, `Uhold`, `UTX*`, `STX*`). These rows cannot become LabResult
  resources and should be reported as "non-result rows" rather than as a terminology gap.
* `order-subtypes-to-snomed`: 23.5 % of mapped POE rows carry no SNOMED code; 51 % of those are the literal
  subtype `Other`, the rest nursing orders (`Activity`, `Restraints`, `NPO/Diet for Procedure`, `Code status`,
  `Precautions`) for which no single SNOMED procedure concept is adequate.
* `rx-norm-to-atc`: 6.7 % of prescription rows with a resolved ingredient have no ATC (sodium lactate, PEG 3350,
  human albumin, sodium/potassium phosphate, insulin isophane, simethicone, vaccines …): these ingredients have
  no ATC property in RxNav.
* `ndcToMedDetails`: 4.3 % of prescription rows have an NDC that RxNav cannot resolve to an ingredient
  (290 distinct NDCs, mostly local compounding / repackager codes).
* Encounter.diagnosis references to the 1 % of ICD-9 diagnoses without ICD-10 equivalent, and
  `ServiceRequest.replaces` references to excluded POE types, are intentionally left dangling.
* Microbiology Observations without organism / dilution have no `value[x]`; the free text is kept in `note`.
  Modelling negative cultures (SNOMED "No growth") would need a rule on the `comments` text.
* Diagnoses: when the ICD-9→ICD-10 GEM offers several equivalents (16 % of ICD-9 rows), the last entry is
  taken; the choice is deterministic but arbitrary.

## 5. Metrics the re-execution will provide

| paper metric | source | note |
|---|---|---|
| input rows read | CSV row counts (profiling) | not emitted by Ignifyr; rows filtered by preconditions = input − (resources + invalid + not mapped) for 1-to-1 mappings |
| generated resources | `numOfFhirResources` (final `chunkResult:false` event) | equals mapped rows when `skipWrite` |
| invalid rows / not-mapped rows | `numOfInvalids`, `numOfNotMapped` | 0 on the subset |
| duration | `@timestamp` STARTED → final event | mapping-only when `skipWrite` |
| output size | sink folder size | only with writing; gzip ≈ 1.4 GB per 1/20 ⇒ ≈ 28 GB full |
| terminology / concept / unit coverage | `MAPPING_COVERAGE` sums | per task × map; use the "mapped rows only" definition (3.6) |
| output quality | `analyze_output.py` on the NDJSON | duplicates, referential integrity, code-system shares, UCUM share |

## 6. Full-data run, group 1 (2 Oct 2026, `skipWrite`, execution 1ebca051)

(Laptop run with the mappings of 2 Oct; superseded by section 7.)

Eight tasks in one execution, 8 Spark threads, 48 GB heap (42 GB peak), 80 minutes wall clock, no spills.

| task | duration | resources | invalid | not mapped |
|---|---|---|---|---|
| patient-mapping | 1:00 | 364 627 | 0 | 0 |
| careunit-mapping | 0:03 | 46 | 0 | 0 |
| admissions-mapping | 4:14 | 546 028 | 0 | 0 |
| diagnoses-mapping | 27:44 | 6 301 870 | 0 | 0 |
| procedures-mapping | 3:08 | 828 404 | 0 | 0 |
| omr-mapping | 25:06 | 10 597 462 | 0 | 0 |
| microbiologyevents | 19:01 | 7 836 802 | 0 | 0 |
| medications-mapping | 0:36 | 25 812 | 1 | 0 |

The single invalid row is a `prescriptions` entry without a drug name (gsn 016579, `formulary_drug_cd` SIMVIND);
`drug` is required by the schema, so it is counted as invalid input – a data finding, not a mapping error.

Lookup coverage (full data) – only the maps below 100 %:

| task | type | map | lookups | coverage | distinct unmapped |
|---|---|---|---|---|---|
| diagnoses | CONCEPT | icd9toicd10cmgem | 3 019 790 | 95.9 % (row level 97.8 %, see README §6) | 332 (all 3/4-char probes) |
| procedures | CONCEPT | icd9toicd10pcsgem | 521 431 | 99.8 % | 7 (probes) |
| medications | CONCEPT | ndcToMedDetails | 16 541 | 95.9 % | 290 |
| medications | TERMINOLOGY | rx-norm-to-atc | 15 855 | 91.6 % | 137 |
| microbiology | TERMINOLOGY | org-itemids-to-snomed | 1 635 365 | 96.5 % | 253 |
| microbiology | TERMINOLOGY | specimen-types-to-hl7 | 1 924 289 | 99.95 % | 11 |
| microbiology | TERMINOLOGY | ab-itemids-to-atc | 1 410 258 | 99.93 % | 23 |
| microbiology | TERMINOLOGY | test-itemids-to-loinc | 3 988 224 | 99.98 % | 1 |
| patients | CONCEPT | race-to-ethnicity | 223 452 | 95.4 % | 4 |
| patients | CONCEPT | language-to-bcp47 | 222 818 | 99.7 % | 1 |

Fixes applied after this run (3 Oct 2026):

* **Microbiology organisms**: 93 % of the organism misses were two non-organism "results": `CANCELLED`
  (item 90760, 41 504 rows) and `MIXED BACTERIAL FLORA` (90785, 11 757). Observations whose organism is
  `CANCELLED` now get `status = cancelled` and no value; `MIXED BACTERIAL FLORA` and `2ND ISOLATE` (80265) keep
  the MIMIC coding with the text but are no longer probed against the SNOMED map (there is no SNOMED organism
  for them); `POSITIVE` / `NEGATIVE` (90855 / 90856, C. difficile assays) map to the SNOMED qualifiers
  10828004 / 260385009. The organism value now keeps the local MIMIC coding even when no SNOMED equivalent
  exists (before, the whole `valueCodeableConcept` was dropped). 40 further organisms were added to
  `org-itemids-to-snomed.csv` (Raoultella, Hafnia, Achromobacter, Pantoea, Shewanella, Rothia, …), covering
  all items with ≥ 15 lookups; expected organism coverage ≈ 99.8 %.
* **Antibiotics**: all 23 unmapped antimicrobials (minocycline, fluconazole, ertapenem, ceftazidime/avibactam,
  fosfomycin, ceftolozane/tazobactam, …) now have ATC codes ⇒ 100 %.
* **Interpretation**: `D` → SDD (susceptible-dose dependent; cefepime, fluconazole, daptomycin rows) and
  `N` → NS (non-susceptible). `Z` (omadacycline / tigecycline, 56 rows) has no HL7 equivalent and stays unmapped.
* **Tests / specimens**: `SHIGA TOXIN (EHEC)` → LOINC 21262-1; `CRE Screen` → rectal swab, the viral-culture and
  `SWAB` specimen items → SNOMED Swab, `URINE,PROSTATIC MASSAGE` → urine, `BLOOD BAG FLUID` → specimen from
  blood bag. Remaining specimen misses (`XXX`, `MICRO PROBLEM PATIENT`, `C, E, & A Screening`, empty) are not
  specimens.
* **Units / language**: medication unit `CELLS` → UCUM `{cells}`; language `Other` → BCP-47 `mis`
  (uncoded languages).
* **Not changed**: race `OTHER`, `AMERICAN INDIAN/ALASKA NATIVE`, `NATIVE HAWAIIAN OR OTHER PACIFIC ISLANDER`,
  `MULTIPLE RACE/ETHNICITY` (10 349 patients, 4.6 %) have no target because the CDM ethnicity value set only
  contains the African, Asian, Caucasian, Hispanic and Unknown racial groups – report as a CDM limitation.

`microbiologyevents` and `patient-mapping` have to be re-run (≈ 20 minutes) to refresh these numbers.

Full-data prescriptions (3–4 Oct, 20 292 608 resources, 4 h 18 min): 1 invalid row (null `drug`, the same row as in
medications) and 2 not-mapped rows. The latter had `dose_val_rx = "-"`: the dose-range guard used
`split('-').all(... toDecimal().exists())`, which is true for the empty split result, so the range block was
rendered with nothing to aggregate. The guard now requires exactly two parsed numbers; a bare "-" produces a
MedicationRequest without `doseAndRate` (verified on the sample).

## 7. Full-data run, all tasks (9 Nov 2026, `skipWrite`) – reference results

### 7.1 Setup

* Mappings, concept maps and job at commit `f398a16` of this repository (PR 39 merged, plus the fixes of 7.5).
* AWS EC2 m6i.8xlarge (32 vCPU Intel Ice Lake, 128 GB), eu-west-1, Ubuntu 26.04, 200 GB gp3 (6000 IOPS,
  1000 MB/s), Docker stack of this folder with `docker-compose.ec2.yml`: Spark `local[28]` inside the server JVM,
  84 partitions, 80 GB heap, `maxChunkSize` 250 000.
* Twelve executions, one per mapping task, started in sequence by `run-tasks.sh --skip-write` on a fresh stack
  (empty Elasticsearch, fresh logs). `skipWrite` runs the complete read / join / map / validate pipeline and skips
  only the sink, so the durations are mapping durations and no patient-level output is produced.
* Extracts (`extract_metrics.py`): paper folder `metrics/2026-11-09-ec2-full/`; audit and Spark event logs in
  `metrics/metrics-ec2-2026-11-09.tgz`.

### 7.2 Counts and durations

Input rows are the CSV row counts (for microbiologyevents the raw rows; the mapping aggregates them to 1 924 289
specimen-level rows). "Chunk" is the median duration of one 250 000-row chunk.

| task | input rows | resources | ratio | duration | input rows/s | chunks | chunk | invalid | not mapped |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| labevents-mapping | 158 374 764 | 152 015 415 | 0.960 | 8:23:19 | 5 244 | 638 | 46.8 s | 0 | 0 |
| emar-mapping | 42 808 594 | 38 831 259 | 0.907 | 2:47:40 | 4 255 | 177 | 54.7 s | 0 | 0 |
| prescriptions-mapping | 20 292 611 | 17 357 251 | 0.855 | 1:12:20 | 4 676 | 84 | 51.1 s | 1 | 0 |
| poe-mapping | 52 212 109 | 19 991 424 | 0.383 | 0:22:22 | 38 906 | 211 | 6.0 s | 0 | 0 |
| procedures-mapping | 859 655 | 828 840 | 0.964 | 0:37:47 | 379 | 4 | 563 s | 0 | 0 |
| omr-mapping | 7 753 027 | 10 595 375 | 1.367 | 0:11:01 | 11 729 | 32 | 20.6 s | 0 | 0 |
| diagnoses-mapping | 6 364 488 | 6 326 773 | 0.994 | 0:09:24 | 11 285 | 26 | 21.5 s | 0 | 0 |
| microbiologyevents | 3 988 224 | 7 836 802 | 1.965 | 0:07:08 | 9 318 | 8 | 52.9 s | 0 | 0 |
| admissions-mapping | 546 028 | 546 028 | 1.000 | 0:01:55 | 4 748 | 3 | | 0 | 0 |
| patient-mapping | 364 627 | 364 627 | 1.000 | 0:00:21 | 17 363 | 2 | | 0 | 0 |
| medications-mapping | 25 813 | 25 812 | 1.000 | 0:00:14 | | 1 | | 1 | 0 |
| careunit-mapping | 46 | 46 | 1.000 | 0:00:01 | | 1 | | 0 | 0 |
| **total** | | **254 719 652** | | **13:53:32** | | 1 187 | | 2 | 0 |

All executions ended with SUCCESS, except prescriptions and medications with PARTIAL_SUCCESS because of one
invalid input row each: the same prescription without a drug name (`drug` is required by the source schema), a data
finding. No row failed in a mapping expression. Wall clock from the first start to the last end: 14 h 14 min.

Where the resource count differs from the input count, the difference is fully explained by the lookup counters:

* **labevents**: 2 929 993 rows have no LOINC item (7.4) and 3 429 356 rows of a LOINC-coded item carry no result
  at all (no numeric value, no text, no usable comment); both are excluded by the precondition.
* **prescriptions**: 2 552 081 rows have no NDC (mostly IV fluids and flushes), 334 785 have an NDC that does not
  resolve to an ingredient, 48 493 resolve to an ingredient without ATC code, 1 row is invalid. The CDM profile
  requires an ATC code, so these rows are skipped: 20 292 611 − 2 935 360 = 17 357 251.
* **emar**: a MedicationAdministration is created only when the administered drug resolves to an ATC code.
* **diagnoses / procedures**: ICD-10 rows always map; ICD-9 rows without a GEM target are skipped
  (37 715 diagnoses, 30 815 procedures): 6 364 488 − 37 715 = 6 326 773 and 859 655 − 30 815 = 828 840.
* **poe**: only the order types that correspond to a ServiceRequest are mapped (medication and lab orders are
  represented by the prescriptions and labevents resources). **omr**: blood pressure rows yield two Observations;
  values ≤ 0 are skipped (2 087 Observations fewer than in October). **microbiologyevents**: DiagnosticReport + Specimen + Observations.

### 7.3 Lookup coverage (MAPPING_COVERAGE events)

| task | type | map | lookups | coverage | distinct unmapped |
|---|---|---|---:|---:|---:|
| labevents | TERMINOLOGY | labitems-to-loinc | 158 374 764 | 98.15 % | 99 |
| labevents | TERMINOLOGY | labitems code system (display) | 152 015 415 | 100 % | 0 |
| labevents | UNIT | lab-unit-conversion | 137 072 766 | 99.99 % | 6 |
| labevents | TERMINOLOGY | labitem-coded-values-to-other | 14 942 541 | 89.91 % | 959 |
| emar | CONCEPT | ndcToMedDetails | 117 122 867 | 99.53 % | 67 |
| emar | CONCEPT | formulary-drug-to-ndc | 9 410 695 | 32.46 % (90.8 % without `NACLFLUSH`) | 371 |
| emar | TERMINOLOGY | rx-norm-to-atc | 77 736 607 | 99.90 % | 26 |
| emar | CONCEPT | emar-status | 42 362 491 | 100 % | 0 |
| emar | TERMINOLOGY | medication-dose-to-orderable-drug-form | 76 210 600 | 100.00 % (3 440 misses) | 17 |
| emar | TERMINOLOGY | medication-route-codes-to-snomed | 36 718 398 | 100 % | 0 |
| prescriptions | CONCEPT | ndcToMedDetails | 17 740 529 | 98.11 % | 108 |
| prescriptions | TERMINOLOGY | rx-norm-to-atc | 17 405 744 | 99.72 % | 30 |
| prescriptions | TERMINOLOGY | medication-dose-to-orderable-drug-form | 20 283 262 | 99.97 % | 9 |
| prescriptions | TERMINOLOGY | medication-route-codes-to-snomed | 17 357 243 | 100.00 % (9 misses) | 4 |
| poe | TERMINOLOGY | order-subtypes-to-snomed | 19 991 424 | 87.90 % | 17 |
| poe | TERMINOLOGY | order-types-to-snomed | 19 991 424 | 100 % | 0 |
| diagnoses | CONCEPT | icd9cm-to-icd10cm | 2 908 741 | 98.70 % | 288 |
| procedures | CONCEPT | icd9-to-icd10pcs | 469 209 | 93.43 % | 41 |
| medications | CONCEPT | ndcToMedDetails | 16 541 | 98.11 % | 108 |
| medications | TERMINOLOGY | rx-norm-to-atc | 16 228 | 98.99 % | 30 |
| microbiology | TERMINOLOGY | org-itemids-to-snomed | 1 581 957 | 100.00 % (7 misses) | 1 |
| microbiology | TERMINOLOGY | test-itemids-to-loinc, specimen-types-to-hl7, ab-itemids-to-atc, interpretation | 8 733 029 | 100 % | 0 |
| patients | CONCEPT / TERMINOLOGY | race-to-ethnicity, language-to-bcp47, marital-status-to-hl7 | 659 641 | 100 % | 0 |
| admissions | CONCEPT / TERMINOLOGY | admission type, admission / discharge location, services (map and display) | 2 867 637 | 100 % | 0 |
| omr | CONCEPT / UNIT | omr-result-name-map, omr-unit-conversion | 12 661 071 | 100 % | 0 |
| careunits, medications | TERMINOLOGY | careunits-to-hl7, medication-units-to-ucum-or-drugform | 60 898 | 100 % | 0 |

The ICD maps are looked up once per ICD-9 row, so their lookup coverage is the row coverage. NDC, ATC, dose-form
and route lookups are row-weighted (one lookup per row that reaches the expression).

### 7.4 Residual gaps (current)

* **Lab rows without LOINC (1.85 %, 2 929 993 rows, 99 items)** are not results: tube-hold items (`Green / Blue /
  Light Green / Red Top Hold`, `Uhold`, `Urine tube, held`, `EDTA Hold`: 1.74 M), internal slots of send-out panels
  (`STX*`, `UTX*`, `HPE*`, `PAN*`, `ARCH-1`, `COV1x`, `EE*`, `UCU*`: 0.85 M), ventilation flags stored in the lab
  table (`Intubated`, `Assist/Control`: 0.21 M), voided / problem specimens and miscellaneous-fluid markers
  (0.08 M), PCR internal controls and endpoint-fluorescence values of the COVID panels (≈ 0.05 M). Real tests
  left without a code because no LOINC concept exists or could be assigned with confidence: Ct values (`CMV CT`,
  `HCV CT`, `HIV Viral Load Ct`, `HBV VL CT`), `MXD` counts, `% Parasitemia` (together about 2 100 rows).
* **Lab coded values (89.91 %)**: 65 % of the 1 507 050 misses are the string `NORMAL`, followed by microscopy
  ranges (`0-2`, `3-5`, `6-10`, `11-20`), `DONE`, `Negative` and specimen descriptors
  (`CENTRAL VENOUS`, `SPONTANEOUS`). The textual result is kept; only the coding is missing.
* **Lab units (99.99 % in the run, closed afterwards)**: 17 880 lookups missed a conversion row; the quantity
  kept the source unit, so no value was lost. The rows were added after the run (10 Nov), which brings the unit
  coverage to 100 % without changing any resource count: the unit-less cycle-threshold items 53105 `E GENE CT`,
  53111 `N2 GENE CT` (8 167 rows each) and 53175 `RdRP Ct` (1 408), one unit-less row of the eGFR item 53161, and
  the blood items 53085 `Albumin` (69 rows) and 53096 `Protein, Total` (68 rows). The last two are a source data
  finding: MIMIC labels them `mg/dL`, but the values (albumin 2.7–4.9, total protein 5.8–8.8) and the reference
  ranges (3.3–5.5 and 6.4–8.1) are in g/dL, so the conversion row keeps the value and corrects the unit to g/dL.
* **Order subtypes (poe, 87.90 %)**: 2 383 228 of the 2 418 149 misses are the literal subtype `Other`; the rest
  are local order sets (`Radiology OK to use line`, `Special Procedures`, `COVID-19 Precautions De-escalation`).
* **Medication (prescriptions / emar)**: 12.6 % of the prescriptions have no NDC and are skipped together with the
  1.9 % whose NDC does not resolve (108 distinct NDCs, mostly repackager codes) or whose ingredient has no ATC
  (30 ingredients). This is the price of the CDM rule that every medication carries an ATC code. The emar fallback
  `formulary-drug-to-ndc` resolves 90.8 % of the NDC-less administrations once the saline flush `NACLFLUSH`
  (6.05 M rows, no NDC by nature) is set aside. Known modelling limits: an ingredient with several ATC codes
  gets all of them, combination products are represented by one ingredient.
* **ICD-9 → ICD-10**: 1.30 % of the ICD-9 diagnosis rows (E93x–E94x adverse-effect codes, 707.2x pressure-ulcer
  stages, V64.41) and 6.57 % of the ICD-9 procedure rows have no GEM target and are skipped. The procedure misses
  are adjunct codes that qualify another procedure (00.40–00.47 number of vessels / stents, 36.06 / 36.07,
  81.62 / 81.63, 84.51). A Condition or Procedure lists all GEM targets of its ICD-9 code, which for procedures
  means many ICD-10-PCS codings per resource.
* **Patients**: the MIMIC races `OTHER`, `AMERICAN INDIAN/ALASKA NATIVE`, `NATIVE HAWAIIAN OR OTHER PACIFIC
  ISLANDER` and `MULTIPLE RACE/ETHNICITY` (4.6 % of patients) are mapped to SNOMED "Unknown racial group" because
  the CDM value set has no other target; the 100 % coverage therefore hides a CDM limitation.
* Microbiology Observations without organism or dilution have no `value[x]` (free text kept in `note`); references
  to skipped ICD-9 diagnoses and to excluded POE types stay dangling (unchanged from section 4).

### 7.5 Differences to the run of 5–6 Oct 2026 (same instance type, mappings before PR 39)

| task | Oct duration | Nov duration | Oct resources | Nov resources | cause |
|---|---:|---:|---:|---:|---|
| labevents | 5:04:48 | 8:23:19 | 131 732 211 | 152 015 415 | interference indices H / L / I mapped to LOINC (≈ 8 M rows), results parsed from `comments`, further LOINC items; per-row regular expressions and computed interpretation make a chunk take 47 s instead of 28 s |
| emar | 2:43:04 | 2:47:40 | 42 285 138 | 38 831 259 | resource only when an ATC code exists; one prescription row per administration (no duplicate ids) |
| prescriptions | 1:24:38 | 1:12:20 | 20 292 610 | 17 357 251 | rows without ATC code skipped (14.5 %) |
| procedures | 0:01:52 | 0:37:47 | 828 404 | 828 840 | all ICD-10-PCS targets of an ICD-9 code are emitted as codings; the size of the resources, not their number, drives the duration |
| diagnoses | 0:11:21 | 0:09:24 | 6 301 870 | 6 326 773 | simplified GEM with manual additions; no prefix probing |
| omr | 0:10:25 | 0:11:01 | 10 597 462 | 10 595 375 | values ≤ 0 skipped |
| others | | | | | unchanged counts (poe, microbiology, admissions, patients, medications, careunits) |
| total | 10:07:57 | 13:53:32 | 240 802 434 | 254 719 652 | |

Coverage moved accordingly: `labitems-to-loinc` 92.97 % → 98.15 %, `ndcToMedDetails` 95.70 % → 98.11 %
(prescriptions) and 97.38 % → 99.53 % (emar), `rx-norm-to-atc` 93.27 % → 99.72 % and 95.21 % → 99.90 %,
`order-subtypes-to-snomed` 76.49 % → 87.90 %, `race-to-ethnicity` 95.37 % → 100 %, ICD-9 diagnoses row coverage
97.8 % → 98.70 %. The ICD-9 procedure figure of October (99.83 %) was a lookup coverage, not a row
coverage: the number of skipped ICD-9 procedure rows is practically unchanged (31 251 then, 30 815 now), and
93.43 % is the row coverage.

Two defects were found and fixed on the day of the run, before the reference executions were started:

* **Lab items missing from the code system.** PR 39 had added 39 items to `labitems-to-loinc.csv` without adding
  them to `labitems-code-system.csv`, and the mapping resolved the display of every item through a non-optional
  placeholder. Every row of these items (respiratory viral panel, COVID-19 tests, the 531xx duplicates of Lactate,
  Neutrophils, MCHC, MCH, RDW, Platelet Count, eGFR 2021) failed with a mapping error. The error table of the
  execution dashboard showed it within minutes of the first start. Fix (`e94867d`, `f398a16`): the code system
  was completed against `d_labitems` v3.1 (1 683 items; item 53168 has an empty label and is left out), the
  display is optional, and 13 further items that are real tests were mapped to LOINC.
* **License.** The server image enforces an Ignifyr Enterprise license since October 2026; the compose file now
  passes the license file path explicitly (`2fc530e`, README 1.4).
