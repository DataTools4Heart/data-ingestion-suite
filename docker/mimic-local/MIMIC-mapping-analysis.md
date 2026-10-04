# MIMIC-IV v3.1 → DT4H CDM mappings: analysis on real data (2 Oct 2026)

Scope: the state of branch `feat/mimic-mapping-review-and-local-execution` after the review commits of 2 Oct
(microbiology id collision, admitSource handling, microbiology struct aggregation, synthetic test data) plus the
changes described in section 3. The analysis is driven by the metrics the paper reports (Table 3: input rows,
generated resources, duration, output size) and the metrics the re-execution will add (invalid / not-mapped rows,
terminology / concept-map / unit-conversion coverage, referential integrity, code-system coverage of the output).

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
* `atc-to-medication-group`: by construction ≈ 7 % of prefix probes succeed; report "share of medication
  resources in a DT4H group" (16 %) instead.
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
