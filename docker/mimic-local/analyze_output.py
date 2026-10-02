#!/usr/bin/env python3
"""Quality metrics computed from the NDJSON output of the MIMIC job (gzip or plain, partitioned by resource type).

Reports, per resource type: resource count, duplicate ids, declared profile, and content metrics that matter for the
CDM (share of Observations with a LOINC code and a UCUM unit, share of medication resources with an ATC code / a DT4H
medication group, encounter class distribution, condition/procedure code systems), plus referential integrity: how many
references point to resources that exist in the output (Patient, Encounter, Condition, Medication, MedicationRequest,
Specimen, Observation, Location, ServiceRequest).

Usage: python analyze_output.py <output-folder> [--out metrics-folder]
"""
import argparse, collections, csv, glob, gzip, json, os, sys

UCUM = 'http://unitsofmeasure.org'
SYSTEMS = {'http://loinc.org': 'LOINC', 'http://snomed.info/sct': 'SNOMED', 'http://www.whocc.no/atc': 'ATC',
           'http://hl7.org/fhir/sid/icd-10': 'ICD-10', 'http://hl7.org/fhir/sid/icd-9-cm': 'ICD-9-CM', 'http://hl7.org/fhir/sid/icd-10-pcs': 'ICD-10-PCS',
           'http://hl7.org/fhir/sid/ndc': 'NDC', 'http://www.nlm.nih.gov/research/umls/rxnorm': 'RxNorm',
           'https://datatools4heart.eu/fhir/CodeSystem/medication-group': 'DT4H-group'}


def iter_resources(folder):
    files = sorted(glob.glob(os.path.join(folder, '**', '*.gz'), recursive=True) + glob.glob(os.path.join(folder, '**', '*.ndjson'), recursive=True)
                   + glob.glob(os.path.join(folder, '**', 'part-*'), recursive=True))
    seen = set()
    for f in files:
        if f in seen or f.endswith('.crc') or os.path.isdir(f):
            continue
        seen.add(f)
        op = gzip.open if f.endswith('.gz') else open
        with op(f, 'rt', encoding='utf-8') as fh:
            for line in fh:
                line = line.strip()
                if line:
                    yield json.loads(line)


def codings(cc):
    if not cc:
        return []
    if isinstance(cc, list):
        return [c for x in cc for c in codings(x)]
    return cc.get('coding', []) or []


def sys_names(cc):
    return {SYSTEMS.get(c.get('system'), c.get('system')) for c in codings(cc)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('folder'); ap.add_argument('--out', default=None)
    a = ap.parse_args()
    counts = collections.Counter(); ids = collections.defaultdict(set); dups = collections.Counter()
    profiles = collections.Counter(); refs = collections.Counter(); ref_targets = collections.defaultdict(set)
    metrics = collections.Counter()
    enc_class = collections.Counter(); cond_sys = collections.Counter(); proc_sys = collections.Counter(); obs_cat = collections.Counter()
    units = collections.Counter(); med_group = collections.Counter(); srq_sys = collections.Counter()

    def ref(res_type, r):
        if isinstance(r, dict):
            r = r.get('reference')
        if isinstance(r, str) and '/' in r:
            refs[(res_type, r.split('/')[0])] += 1; ref_targets[r.split('/')[0]].add(r.split('/')[1])

    for r in iter_resources(a.folder):
        t = r.get('resourceType'); counts[t] += 1
        rid = r.get('id')
        if rid in ids[t]:
            dups[t] += 1
        ids[t].add(rid)
        for p in (r.get('meta') or {}).get('profile', []) or ['(none)']:
            profiles[(t, p.rsplit('/', 1)[-1])] += 1
        for k in ('subject', 'encounter', 'specimen', 'request', 'basedOn', 'replaces'):
            v = r.get(k)
            for x in (v if isinstance(v, list) else [v]):
                if x:
                    ref(t, x)
        if t == 'Encounter':
            for c in codings(r.get('class')):
                enc_class[c.get('code')] += 1
            for d in r.get('diagnosis', []) or []:
                for cr in d.get('condition', []) or []:
                    ref(t, cr.get('reference'))
            for rs in r.get('reason', []) or []:
                for v in rs.get('value', []) or []:
                    ref(t, v.get('reference'))
            for l in r.get('location', []) or []:
                ref(t, (l.get('location') or {}))
            if r.get('serviceType'):
                metrics[('Encounter', 'with serviceType')] += 1
            if r.get('priority'):
                metrics[('Encounter', 'with priority')] += 1
        elif t == 'Condition':
            for s in sys_names(r.get('code')):
                cond_sys[s] += 1
            if 'ICD-10' in sys_names(r.get('code')):
                metrics[('Condition', 'with ICD-10')] += 1
        elif t == 'Procedure':
            for s in sys_names(r.get('code')):
                proc_sys[s] += 1
            if 'ICD-10-PCS' in sys_names(r.get('code')):
                metrics[('Procedure', 'with ICD-10-PCS')] += 1
        elif t == 'Observation':
            cat = next(iter(sys_names(r.get('category')) and [c.get('code') for c in codings(r.get('category'))]), '?')
            obs_cat[cat] += 1
            if 'LOINC' in sys_names(r.get('code')):
                metrics[('Observation', f'{cat}: with LOINC')] += 1
            q = r.get('valueQuantity')
            if q:
                metrics[('Observation', f'{cat}: valueQuantity')] += 1
                if q.get('system') == UCUM and q.get('code'):
                    metrics[('Observation', f'{cat}: UCUM coded unit')] += 1
                units[(cat, q.get('code'))] += 1
            elif r.get('valueCodeableConcept'):
                metrics[('Observation', f'{cat}: valueCodeableConcept')] += 1
            elif r.get('valueString'):
                metrics[('Observation', f'{cat}: valueString')] += 1
            else:
                metrics[('Observation', f'{cat}: no value')] += 1
            if r.get('referenceRange'):
                metrics[('Observation', f'{cat}: referenceRange')] += 1
            if r.get('interpretation'):
                metrics[('Observation', f'{cat}: interpretation')] += 1
        elif t in ('MedicationRequest', 'MedicationAdministration'):
            med = r.get('medication') or {}
            s = sys_names(med.get('concept'))
            for n in ('NDC', 'RxNorm', 'ATC', 'DT4H-group'):
                if n in s:
                    metrics[(t, f'with {n}')] += 1
            if 'DT4H-group' in s:
                for c in codings(med.get('concept')):
                    if c.get('system', '').endswith('medication-group'):
                        med_group[c.get('code')] += 1
            if med.get('reference'):
                ref(t, med['reference'])
                metrics[(t, 'with Medication reference')] += 1
            di = r.get('dosageInstruction') or ([r.get('dosage')] if r.get('dosage') else [])
            if di and any(d.get('route') for d in di):
                metrics[(t, 'with route')] += 1
            if di and any(d.get('doseAndRate') or d.get('dose') for d in di):
                metrics[(t, 'with dose')] += 1
        elif t == 'Medication':
            s = sys_names(r.get('code'))
            if 'NDC' in s:
                metrics[(t, 'with NDC')] += 1
            if r.get('ingredient'):
                metrics[(t, 'with ingredient')] += 1
        elif t == 'ServiceRequest':
            for s in sys_names((r.get('code') or {}).get('concept')):
                srq_sys[s] += 1
            if 'SNOMED' in sys_names((r.get('code') or {}).get('concept')):
                metrics[(t, 'with SNOMED code')] += 1
        elif t == 'Patient':
            if r.get('extension'):
                metrics[(t, 'with ethnicity extension')] += 1
            if r.get('communication'):
                metrics[(t, 'with language')] += 1
            if r.get('maritalStatus'):
                metrics[(t, 'with maritalStatus')] += 1
        elif t == 'Specimen':
            if 'http://terminology.hl7.org/CodeSystem/v2-0487' in {c.get('system') for c in codings(r.get('type'))}:
                metrics[(t, 'with HL7 v2 specimen type')] += 1
        elif t == 'DiagnosticReport':
            for x in r.get('result', []) or []:
                ref(t, x)

    out = []
    def p(s=''):
        print(s); out.append(s)
    p('== resources');
    for t, n in counts.most_common():
        p(f'{t:<26}{n:>12,}   duplicate ids: {dups[t]:,}')
    p('\n== profiles')
    for (t, pr), n in sorted(profiles.items()):
        p(f'{t:<26}{pr:<32}{n:>12,}')
    p('\n== content metrics (count, % of resource type)')
    for (t, m), n in sorted(metrics.items()):
        base = counts[t] if not m.split(':')[0] in obs_cat else obs_cat[m.split(':')[0]]
        p(f'{t:<26}{m:<40}{n:>12,}  {100.0*n/base if base else 0:6.1f}%')
    p('\n== Observation categories'); [p(f'  {k:<16}{v:>12,}') for k, v in obs_cat.most_common()]
    p('== Encounter.class'); [p(f'  {k:<16}{v:>12,}') for k, v in enc_class.most_common()]
    p('== Condition code systems'); [p(f'  {k:<16}{v:>12,}') for k, v in cond_sys.most_common()]
    p('== Procedure code systems'); [p(f'  {k:<16}{v:>12,}') for k, v in proc_sys.most_common()]
    p('== ServiceRequest code systems'); [p(f'  {k:<16}{v:>12,}') for k, v in srq_sys.most_common()]
    p('== DT4H medication groups'); [p(f'  {k:<16}{v:>12,}') for k, v in med_group.most_common()]
    p('\n== referential integrity (references from -> to: total, resolved within the output)')
    for (src, tgt), n in sorted(refs.items()):
        resolved = 0
    # second pass over refs is expensive; recompute resolution from ref_targets vs ids
    for (src, tgt), n in sorted(refs.items()):
        missing = len(ref_targets[tgt] - ids.get(tgt, set()))
        p(f'{src:<24}-> {tgt:<22} refs={n:>12,}  distinct targets={len(ref_targets[tgt]):>10,}  unresolved targets={missing:>10,}')
    p('\n== top units per category'); [p(f'  {c:<12}{u!s:<20}{n:>12,}') for (c, u), n in units.most_common(40)]
    if a.out:
        os.makedirs(a.out, exist_ok=True)
        open(os.path.join(a.out, 'output_quality.txt'), 'w', encoding='utf-8').write('\n'.join(out))
        with open(os.path.join(a.out, 'output_units.csv'), 'w', newline='', encoding='utf-8') as f:
            w = csv.writer(f); w.writerow(['category', 'ucum_code', 'count']); [w.writerow([c, u, n]) for (c, u), n in units.most_common()]


if __name__ == '__main__':
    main()
