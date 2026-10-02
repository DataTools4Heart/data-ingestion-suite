#!/usr/bin/env python3
"""Extract the MIMIC re-execution metrics emitted by Ignifyr into CSV (and XLSX) tables for the paper.

Two sources are supported and can be combined:
  * Elasticsearch (fluentd-* indices filled by Fluentd):   --es http://localhost:9200
  * the Logstash-JSON audit file(s) written by logback:    --log ignifyr-docker-logs/ignifyr-mappings.log [more files]

Outputs (in --out, default ./mimic-metrics):
  job_results.csv      one row per execution x mapping task: start/end time, duration, #chunks, #batches,
                       mapped resources, invalid rows, not-mapped rows, failed writes, result, write skipped
  chunk_results.csv    one row per chunk (for throughput / chunk-duration plots)
  coverage_summary.csv per execution x mapping task x lookup type x concept map: lookups, successes, coverage %
  coverage_unmapped.csv per concept map: the distinct unmapped source codes / units with lookup counts
  mapping_errors.csv   distinct error descriptions per mapping task with counts (MAPPING_RESULT events)
  mimic-metrics.xlsx   the same tables as sheets (needs openpyxl)

Usage examples:
  python extract_metrics.py --es http://localhost:9200 --out ./mimic-metrics
  python extract_metrics.py --log ./ignifyr-docker-logs/ignifyr-mappings.log --execution 1f02cf10-...
"""
import argparse, csv, json, os, sys, collections, datetime, urllib.request, zipfile, io

ES_INDEX = 'fluentd-*'


# ----------------------------------------------------------------------------------------------------------------
# Event collection
# ----------------------------------------------------------------------------------------------------------------
def es_request(base, path, body=None):
    req = urllib.request.Request(base.rstrip('/') + path, data=json.dumps(body).encode() if body is not None else None,
                                 headers={'Content-Type': 'application/json'}, method='POST' if body is not None else 'GET')
    with urllib.request.urlopen(req, timeout=300) as r:
        return json.load(r)


def es_scan(base, query, fields=None):
    """Iterate over all hits of a query with search_after paging (sorted by @timestamp, _id)."""
    body = {'size': 5000, 'query': query, 'sort': [{'@timestamp': 'asc'}, {'_id': 'asc'}]}
    if fields:
        body['_source'] = fields
    after = None
    while True:
        if after:
            body['search_after'] = after
        res = es_request(base, f'/{ES_INDEX}/_search', body)
        hits = res['hits']['hits']
        if not hits:
            return
        for h in hits:
            yield h['_source']
        after = hits[-1]['sort']


def read_log_files(paths):
    for p in paths:
        if p.endswith('.zip'):
            with zipfile.ZipFile(p) as z:
                for n in z.namelist():
                    for line in io.TextIOWrapper(z.open(n), encoding='utf-8'):
                        yield line
        else:
            with open(p, encoding='utf-8') as f:
                for line in f:
                    yield line


def collect_events(args):
    job_events, cov_events, err_events = [], [], []
    if args.es:
        q = lambda eid: {'bool': {'filter': [{'term': {'eventId.keyword': eid}}] + ([{'term': {'executionId.keyword': args.execution}}] if args.execution else [])}}
        for e in es_scan(args.es, q('MAPPING_JOB_RESULT')):
            job_events.append(e)
        for e in es_scan(args.es, q('MAPPING_COVERAGE')):
            cov_events.append(e)
        for e in es_scan(args.es, q('MAPPING_RESULT'), fields=['@timestamp', 'executionId', 'mappingTaskName', 'errorCode', 'errorDesc', 'errorExpr']):
            err_events.append(e)
    for line in read_log_files(args.log or []):
        line = line.strip()
        if not line.startswith('{'):
            continue
        try:
            e = json.loads(line)
        except json.JSONDecodeError:
            continue
        if args.execution and e.get('executionId') != args.execution:
            continue
        eid = e.get('eventId')
        if eid == 'MAPPING_JOB_RESULT':
            job_events.append(e)
        elif eid == 'MAPPING_COVERAGE':
            cov_events.append(e)
        elif eid == 'MAPPING_RESULT':
            err_events.append(e)
    return job_events, cov_events, err_events


# ----------------------------------------------------------------------------------------------------------------
# Tables
# ----------------------------------------------------------------------------------------------------------------
def ts(e):
    t = e.get('@timestamp') or e.get('timestamp')
    if not t:
        return None
    t = t.replace('Z', '+00:00')
    try:
        return datetime.datetime.fromisoformat(t)
    except ValueError:
        return None


def to_int(v):
    try:
        return int(v)
    except (TypeError, ValueError):
        return 0


def job_tables(job_events):
    by_task = collections.defaultdict(list)
    for e in job_events:
        by_task[(e.get('executionId'), e.get('mappingTaskName'))].append(e)
    rows, chunk_rows = [], []
    for (ex, task), evs in sorted(by_task.items(), key=lambda kv: min(ts(x) or datetime.datetime.max for x in kv[1])):
        evs.sort(key=lambda x: ts(x) or datetime.datetime.max)
        started = [x for x in evs if x.get('result') == 'STARTED' and not x.get('chunkResult')]
        finals = [x for x in evs if not x.get('chunkResult') and x.get('result') in ('SUCCESS', 'PARTIAL_SUCCESS', 'FAILURE', 'STOPPED', 'SKIPPED')]
        chunks = [x for x in evs if x.get('chunkResult')]
        t0 = ts(started[0]) if started else (ts(evs[0]) if evs else None)
        final = finals[-1] if finals else None
        t1 = ts(final) if final else (ts(chunks[-1]) if chunks else None)
        dur = (t1 - t0).total_seconds() if t0 and t1 else None
        agg = final if final else None
        mapped = to_int(agg.get('numOfFhirResources')) if agg else sum(to_int(c.get('numOfFhirResources')) for c in chunks)
        invalid = to_int(agg.get('numOfInvalids')) if agg else sum(to_int(c.get('numOfInvalids')) for c in chunks)
        notmapped = to_int(agg.get('numOfNotMapped')) if agg else sum(to_int(c.get('numOfNotMapped')) for c in chunks)
        failed = to_int(agg.get('numOfFailedWrites')) if agg else sum(to_int(c.get('numOfFailedWrites')) for c in chunks)
        rows.append({'executionId': ex, 'mappingTaskName': task, 'start': t0.isoformat() if t0 else '', 'end': t1.isoformat() if t1 else '',
                     'duration_s': round(dur) if dur is not None else '', 'duration_hms': str(datetime.timedelta(seconds=round(dur))) if dur is not None else '',
                     'chunks': len(chunks), 'batches': max([to_int(x.get('totalNumOfBatches')) for x in evs] + [1]),
                     'fhir_resources': mapped, 'invalid_rows': invalid, 'not_mapped_rows': notmapped, 'failed_writes': failed,
                     'rows_per_second': round(mapped / dur, 1) if dur else '', 'result': final.get('result') if final else 'RUNNING/UNKNOWN',
                     'write_skipped': (final or evs[-1]).get('isWriteSkipped', '')})
        prev = t0
        for c in chunks:
            tc = ts(c)
            chunk_rows.append({'executionId': ex, 'mappingTaskName': task, 'timestamp': tc.isoformat() if tc else '', 'chunkProgress': c.get('chunkProgress', ''),
                               'batchProgress': c.get('batchProgress', ''), 'chunk_seconds': round((tc - prev).total_seconds(), 1) if tc and prev else '',
                               'fhir_resources': to_int(c.get('numOfFhirResources')), 'invalid_rows': to_int(c.get('numOfInvalids')),
                               'not_mapped_rows': to_int(c.get('numOfNotMapped')), 'failed_writes': to_int(c.get('numOfFailedWrites'))})
            prev = tc or prev
    return rows, chunk_rows


def coverage_tables(cov_events):
    summ = collections.defaultdict(lambda: [0, 0])
    unmapped = collections.Counter()
    distinct_codes = collections.defaultdict(lambda: [set(), set()])
    for e in cov_events:
        key = (e.get('executionId'), e.get('mappingTaskName'), e.get('mappingType'), e.get('operation'), e.get('conceptMap') or e.get('sourceSystem') or '')
        n = to_int(e.get('numOfLookups'))
        ok = e.get('coverageResult') == 'SUCCESS'
        summ[key][0] += n
        if ok:
            summ[key][1] += n
        code = e.get('sourceCode') or e.get('sourceUnit') or ''
        distinct_codes[key][0 if ok else 1].add(code)
        if not ok:
            unmapped[(e.get('mappingTaskName'), e.get('mappingType'), e.get('conceptMap') or '', e.get('sourceSystem') or '', e.get('sourceCode') or '', e.get('sourceUnit') or '')] += n
    rows = []
    for (ex, task, mtype, op, cmap), (tot, ok) in sorted(summ.items(), key=lambda kv: (str(kv[0][0]), str(kv[0][1]), str(kv[0][2]))):
        rows.append({'executionId': ex, 'mappingTaskName': task, 'mappingType': mtype, 'operation': op, 'conceptMap': cmap,
                     'lookups': tot, 'successful_lookups': ok, 'coverage_pct': round(100.0 * ok / tot, 2) if tot else '',
                     'distinct_codes_mapped': len(distinct_codes[(ex, task, mtype, op, cmap)][0]),
                     'distinct_codes_unmapped': len(distinct_codes[(ex, task, mtype, op, cmap)][1])})
    urows = [{'mappingTaskName': k[0], 'mappingType': k[1], 'conceptMap': k[2], 'sourceSystem': k[3], 'sourceCode': k[4], 'sourceUnit': k[5], 'failed_lookups': v}
             for k, v in unmapped.most_common()]
    return rows, urows


def error_table(err_events):
    c = collections.Counter()
    for e in err_events:
        c[(e.get('executionId'), e.get('mappingTaskName'), e.get('errorCode'), (e.get('errorDesc') or '')[:300])] += 1
    return [{'executionId': k[0], 'mappingTaskName': k[1], 'errorCode': k[2], 'errorDesc': k[3], 'count': v} for k, v in c.most_common()]


def write_csv(path, rows):
    if not rows:
        open(path, 'w').close(); return
    with open(path, 'w', newline='', encoding='utf-8') as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader(); w.writerows(rows)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--es', help='Elasticsearch base URL, e.g. http://localhost:9200')
    ap.add_argument('--log', nargs='*', help='ignifyr-mappings.log file(s) (plain or .zip rolled files)')
    ap.add_argument('--execution', help='restrict to one executionId')
    ap.add_argument('--out', default='./mimic-metrics')
    args = ap.parse_args()
    if not args.es and not args.log:
        ap.error('give --es and/or --log')
    os.makedirs(args.out, exist_ok=True)
    job_events, cov_events, err_events = collect_events(args)
    print(f'events: job={len(job_events)} coverage={len(cov_events)} errors={len(err_events)}')
    jobs, chunks = job_tables(job_events)
    cov, unmapped = coverage_tables(cov_events)
    errs = error_table(err_events)
    tables = {'job_results': jobs, 'chunk_results': chunks, 'coverage_summary': cov, 'coverage_unmapped': unmapped, 'mapping_errors': errs}
    for name, rows in tables.items():
        write_csv(os.path.join(args.out, name + '.csv'), rows)
        print(f'  {name}.csv: {len(rows)} rows')
    try:
        import openpyxl
        wb = openpyxl.Workbook(); wb.remove(wb.active)
        for name, rows in tables.items():
            ws = wb.create_sheet(name[:31])
            if rows:
                ws.append(list(rows[0].keys()))
                for r in rows[:1_000_000]:
                    ws.append(list(r.values()))
        wb.save(os.path.join(args.out, 'mimic-metrics.xlsx'))
        print('  mimic-metrics.xlsx written')
    except ImportError:
        print('openpyxl not installed: XLSX skipped (pip install openpyxl)')
    for r in jobs:
        print(f"{r['mappingTaskName']:<28} {r['result']:<16} {r['duration_hms']:>10}  resources={r['fhir_resources']:>12,}  invalid={r['invalid_rows']:,} notMapped={r['not_mapped_rows']:,}")


if __name__ == '__main__':
    main()
