"""Read Android vitals from the Google Play Developer Reporting API without mutating anything (S2917).

Every call here is a `get`, `query`, `search` or `list` on `playdeveloperreporting` - a read-only
service with its own OAuth scope, separate from the `androidpublisher` API the release scripts use.
No edit is opened, so there is nothing to discard and nothing a crash could leave half-done.

Two layers, so the tests run the shipped code rather than a copy of it:

  fetch_raw(service, args)      -> the raw API responses, keyed by call name (network)
  normalize(raw, args, now_utc) -> the snapshot printed on stdout (pure)

`--fixture <path>` loads the raw dict from a JSON file and skips `fetch_raw` and the key entirely.
A fixture holding an `_httpError` member is classified exactly as a live HttpError would be.

The service is built from the LIVE discovery document (`static_discovery=False`): the copy shipped
inside google-api-python-client is revision 20260423 and knows neither memory metric set, so a client
built from it simply lacks `anonrssandswapmemoryusage` and `bitmapmemoryusage`
(PLAN/S2917_play-vitals-monitor/research/01, section 7).

Values are parsed from `decimalValue.value` as floats and never rescaled: the API does not state
whether a rate is a fraction or a percentage, nor the unit of the memory percentiles (research item 6).
Interpreting them is the verdict's job, under an explicit configured assumption.

Snapshot, schema 1:

    {
      "schema": 1, "source": "api" | "fixture", "package": "..", "measuredUtc": "..Z",
      "window": {"startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD", "timeZone": "America/Los_Angeles", "days": 28},
      "sets": {
        "crashRate":   {"freshUntil": "YYYY-MM-DD", "overall": [row], "byVersionCode": [row], "byDeviceModel": [row]},
        "anrRate":     { .. same .. },
        "lmkRate":     {"freshUntil": .., "byVersionCode": [row]},
        "anonMemory":  {"freshUntil": .., "byRamBucketAppState": [row]},
        "bitmapMemory":{"freshUntil": .., "byRamBucketAppState": [row]}
      },
      "errorIssues": [{"name", "type", "cause", "location", "errorReportCount", "distinctUsers",
                       "issueUri", "lastAppVersionCode"}],
      "anomalies":   [{"name", "metricSet", "metric", "value", "dimensions", "startDate", "endDate"}]
    }
    row = {"date": "YYYY-MM-DD", "dims": {name: value}, "metrics": {name: float}}

Exit codes:
  0 - every call succeeded and the snapshot was printed
  2 - could not read: key or client missing, a fixture unreadable, or any API call failed; nothing
      is printed on stdout, so a partial read can never reach a document
"""
import argparse
import datetime as dt
import json
import os
import sys

PACKAGE_NAME = "com.sza.fastmediasorter"
KEY_PATH = os.path.join(".secrets", "play-console-key.json")
SCOPE = "https://www.googleapis.com/auth/playdeveloperreporting"
DAILY_ZONE = "America/Los_Angeles"
PLAY_PERMISSION = "View app information and download bulk reports (read-only)"

RATE_SETS = {
    # snapshot key: (resource attribute, metric set, metric stem)
    "crashRate": ("crashrate", "crashRateMetricSet", "Crash"),
    "anrRate": ("anrrate", "anrRateMetricSet", "Anr"),
}
LMK_SET = ("lmkrate", "lmkRateMetricSet")
MEMORY_SETS = {
    "anonMemory": ("anonrssandswapmemoryusage", "anonRssAndSwapMemoryUsageMetricSet", "anonRssAndSwapMemoryUsageP90"),
    "bitmapMemory": ("bitmapmemoryusage", "bitmapMemoryUsageMetricSet", "bitmapMemoryUsageP90"),
}
RATE_BREAKDOWNS = {"overall": [], "byVersionCode": ["versionCode"], "byDeviceModel": ["deviceModel"]}
MEMORY_DIMENSIONS = ["deviceRamBucket", "appState"]


class ReaderError(Exception):
    """A read that cannot be completed; its message is the one stderr line the operator reads."""


def parse_args(argv):
    parser = argparse.ArgumentParser(description="Read Android vitals from the Play Developer Reporting API (read-only).")
    parser.add_argument("--package", default=PACKAGE_NAME)
    parser.add_argument("--key", default=KEY_PATH)
    parser.add_argument("--days", type=int, default=28)
    parser.add_argument("--top-issues", type=int, default=10)
    parser.add_argument("--fixture", default=None)
    return parser.parse_args(argv)


def rate_metrics(stem):
    lower = stem[0].lower() + stem[1:]
    return [f"userPerceived{stem}Rate", f"userPerceived{stem}Rate28dUserWeighted", f"{lower}Rate", "distinctUsers"]


LMK_METRICS = ["userPerceivedLmkRate", "userPerceivedLmkRate28dUserWeighted", "distinctUsers"]


def describe_http_error(status, content):
    """One operator-facing line for an API refusal. `content` is the error body (bytes, str or dict)."""
    body = content
    if isinstance(body, (bytes, bytearray)):
        body = body.decode("utf-8", errors="replace")
    if isinstance(body, str):
        try:
            body = json.loads(body)
        except ValueError:
            return f"HTTP {status}: {body[:300]}"
    error = (body or {}).get("error", {}) if isinstance(body, dict) else {}
    message = error.get("message", "")
    reason = None
    activation_url = None
    for detail in error.get("details", []) or []:
        if not isinstance(detail, dict):
            continue
        reason = reason or detail.get("reason")
        activation_url = activation_url or (detail.get("metadata") or {}).get("activationUrl")
    if reason == "SERVICE_DISABLED":
        target = activation_url or "the Google Cloud console for the service account's project"
        return (f"HTTP {status} SERVICE_DISABLED - the Play Developer Reporting API is not enabled on the "
                f"service account's Cloud project. The owner enables it at {target}")
    if status == 403 or error.get("status") == "PERMISSION_DENIED":
        return (f"HTTP {status} PERMISSION_DENIED - the service account lacks the Play Console permission "
                f"'{PLAY_PERMISSION}' for this app. {message}").strip()
    return f"HTTP {status} {error.get('status', '')}: {message}".strip()


def date_of(value):
    """'YYYY-MM-DD' from an API DateTime dict."""
    return f"{int(value['year']):04d}-{int(value['month']):02d}-{int(value['day']):02d}"


def fresh_end(get_response):
    """Exclusive DAILY end date from a metric set's freshnessInfo, as a date."""
    for fresh in ((get_response or {}).get("freshnessInfo") or {}).get("freshnesses", []) or []:
        if fresh.get("aggregationPeriod") == "DAILY" and fresh.get("latestEndTime"):
            end = fresh["latestEndTime"]
            return dt.date(int(end["year"]), int(end["month"]), int(end["day"]))
    raise ReaderError(f"metric set {get_response.get('name', '?')} reports no DAILY freshness")


def window_for(get_response, days):
    """(start inclusive, end exclusive) DAILY dates for one metric set."""
    end = fresh_end(get_response)
    return end - dt.timedelta(days=days), end


def api_date(value):
    return {"year": value.year, "month": value.month, "day": value.day, "timeZone": {"id": DAILY_ZONE}}


def paged(call_factory, key):
    """Collect every page of a list/search/query response into one {key: [...]} dict."""
    items = []
    token = None
    while True:
        response = call_factory(token).execute()
        items.extend(response.get(key, []) or [])
        token = response.get("nextPageToken")
        if not token:
            return {key: items}


def fetch_raw(service, args):
    app = f"apps/{args.package}"
    vitals = service.vitals()
    raw = {"freshness": {}, "queries": {}}

    def resource(attribute):
        return getattr(vitals, attribute)()

    def query(attribute, metric_set, start, end, dimensions, metrics):
        body = {
            "timelineSpec": {"aggregationPeriod": "DAILY", "startTime": api_date(start), "endTime": api_date(end)},
            "dimensions": dimensions,
            "metrics": metrics,
            "pageSize": 100000,
        }

        def call(token):
            page = dict(body)
            if token:
                page["pageToken"] = token
            return resource(attribute).query(name=f"{app}/{metric_set}", body=page)
        return paged(call, "rows")

    for key, (attribute, metric_set, stem) in RATE_SETS.items():
        got = resource(attribute).get(name=f"{app}/{metric_set}").execute()
        raw["freshness"][key] = got
        start, end = window_for(got, args.days)
        for breakdown, dimensions in RATE_BREAKDOWNS.items():
            raw["queries"][f"{key}.{breakdown}"] = query(attribute, metric_set, start, end, dimensions, rate_metrics(stem))

    attribute, metric_set = LMK_SET
    got = resource(attribute).get(name=f"{app}/{metric_set}").execute()
    raw["freshness"]["lmkRate"] = got
    start, end = window_for(got, args.days)
    raw["queries"]["lmkRate.byVersionCode"] = query(attribute, metric_set, start, end, ["versionCode"], LMK_METRICS)

    for key, (attribute, metric_set, metric) in MEMORY_SETS.items():
        got = resource(attribute).get(name=f"{app}/{metric_set}").execute()
        raw["freshness"][key] = got
        start, end = window_for(got, args.days)
        raw["queries"][f"{key}.byRamBucketAppState"] = query(
            attribute, metric_set, start, end, MEMORY_DIMENSIONS, [metric, "distinctUsers"])

    start, end = window_for(raw["freshness"]["crashRate"], args.days)

    def issues(token):
        params = {
            "parent": app,
            "orderBy": "distinctUsers desc",
            "pageSize": max(1, args.top_issues),
            "interval_startTime_year": start.year, "interval_startTime_month": start.month,
            "interval_startTime_day": start.day, "interval_startTime_hours": 0,
            "interval_startTime_timeZone_id": "UTC",
            "interval_endTime_year": end.year, "interval_endTime_month": end.month,
            "interval_endTime_day": end.day, "interval_endTime_hours": 0,
            "interval_endTime_timeZone_id": "UTC",
        }
        if token:
            params["pageToken"] = token
        return vitals.errors().issues().search(**params)
    # One page is the whole answer: the list is ordered and only the top of it reaches a ticket.
    raw["errorIssues"] = issues(None).execute()

    since = f'"{start.isoformat()}T00:00:00Z"'

    def anomalies(token):
        params = {"parent": app, "filter": f"activeBetween({since}, UNBOUNDED)", "pageSize": 100}
        if token:
            params["pageToken"] = token
        return service.anomalies().list(**params)
    raw["anomalies"] = paged(anomalies, "anomalies")
    return raw


def dimension_value(entry):
    if "stringValue" in entry:
        return str(entry["stringValue"])
    if "int64Value" in entry:
        return str(entry["int64Value"])
    return entry.get("valueLabel", "")


def decimal_of(metric):
    value = (metric.get("decimalValue") or {}).get("value", "")
    # The Decimal contract says an empty string is zero.
    return float(value) if value not in ("", None) else 0.0


def normalize_rows(response):
    rows = []
    for row in (response or {}).get("rows", []) or []:
        rows.append({
            "date": date_of(row["startTime"]),
            "dims": {d.get("dimension"): dimension_value(d) for d in row.get("dimensions", []) or []},
            "metrics": {m.get("metric"): decimal_of(m) for m in row.get("metrics", []) or []},
        })
    rows.sort(key=lambda r: (r["date"], json.dumps(r["dims"], sort_keys=True)))
    return rows


def last_data_date(get_response):
    return (fresh_end(get_response) - dt.timedelta(days=1)).isoformat()


def normalize_issue(issue):
    last = issue.get("lastAppVersion") or {}
    return {
        "name": issue.get("name"),
        # The Play console cluster id, the only exact key two reads of the same crash share (S3286).
        # `name` is apps/{package}/errorIssues/{clusterId}; a name without a slash yields the whole
        # string, which is harmless because the dedup only ever matches an id against itself.
        "clusterId": (issue.get("name") or "").rsplit("/", 1)[-1] or None,
        "type": issue.get("type"),
        "cause": issue.get("cause"),
        "location": issue.get("location"),
        "errorReportCount": int(issue.get("errorReportCount", 0) or 0),
        "distinctUsers": int(issue.get("distinctUsers", 0) or 0),
        "issueUri": issue.get("issueUri"),
        "lastAppVersionCode": str(last.get("versionCode")) if last.get("versionCode") is not None else None,
    }


def normalize_anomaly(anomaly):
    metric = anomaly.get("metric") or {}
    spec = anomaly.get("timelineSpec") or {}
    return {
        "name": anomaly.get("name"),
        "metricSet": anomaly.get("metricSet"),
        "metric": metric.get("metric"),
        "value": decimal_of(metric),
        "dimensions": {d.get("dimension"): dimension_value(d) for d in anomaly.get("dimensions", []) or []},
        "startDate": date_of(spec["startTime"]) if spec.get("startTime") else None,
        "endDate": date_of(spec["endTime"]) if spec.get("endTime") else None,
    }


def normalize(raw, args, now_utc):
    freshness = raw.get("freshness") or {}
    queries = raw.get("queries") or {}
    missing = [k for k in ("crashRate", "anrRate", "lmkRate", "anonMemory", "bitmapMemory") if k not in freshness]
    if missing:
        raise ReaderError(f"raw responses lack the freshness of {', '.join(missing)}")
    start, end = window_for(freshness["crashRate"], args.days)
    sets = {}
    for key in RATE_SETS:
        sets[key] = {"freshUntil": last_data_date(freshness[key])}
        for breakdown in RATE_BREAKDOWNS:
            sets[key][breakdown] = normalize_rows(queries.get(f"{key}.{breakdown}"))
    sets["lmkRate"] = {
        "freshUntil": last_data_date(freshness["lmkRate"]),
        "byVersionCode": normalize_rows(queries.get("lmkRate.byVersionCode")),
    }
    for key in MEMORY_SETS:
        sets[key] = {
            "freshUntil": last_data_date(freshness[key]),
            "byRamBucketAppState": normalize_rows(queries.get(f"{key}.byRamBucketAppState")),
        }
    issues = [normalize_issue(i) for i in ((raw.get("errorIssues") or {}).get("errorIssues", []) or [])]
    return {
        "schema": 1,
        "source": "fixture" if args.fixture else "api",
        "package": args.package,
        "measuredUtc": now_utc.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "window": {
            "startDate": start.isoformat(),
            "endDate": (end - dt.timedelta(days=1)).isoformat(),
            "timeZone": DAILY_ZONE,
            "days": args.days,
        },
        "sets": sets,
        "errorIssues": issues[: max(0, args.top_issues)],
        "anomalies": [normalize_anomaly(a) for a in ((raw.get("anomalies") or {}).get("anomalies", []) or [])],
    }


def load_fixture(path):
    try:
        with open(path, encoding="utf-8") as handle:
            raw = json.load(handle)
    except (OSError, ValueError) as exc:
        raise ReaderError(f"fixture '{path}' is unreadable ({exc})") from exc
    if "_httpError" in raw:
        failure = raw["_httpError"]
        raise ReaderError(describe_http_error(failure.get("status"), failure.get("content")))
    return raw


def read_live(args):
    if not os.path.exists(args.key):
        raise ReaderError(f"service-account key not found at '{args.key}'")
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
        from googleapiclient.errors import HttpError
    except ImportError as exc:
        raise ReaderError(f"Google API client unavailable ({exc})") from exc
    try:
        creds = service_account.Credentials.from_service_account_file(args.key, scopes=[SCOPE])
        service = build("playdeveloperreporting", "v1beta1", credentials=creds,
                        cache_discovery=False, static_discovery=False)
        return fetch_raw(service, args)
    except HttpError as exc:
        raise ReaderError(describe_http_error(exc.resp.status, exc.content)) from exc
    except ReaderError:
        raise
    except Exception as exc:  # network, discovery or credential failures - all mean "could not read"
        raise ReaderError(f"{type(exc).__name__}: {exc}") from exc


def main(argv):
    args = parse_args(argv)
    try:
        raw = load_fixture(args.fixture) if args.fixture else read_live(args)
        snapshot = normalize(raw, args, dt.datetime.now(dt.timezone.utc))
    except ReaderError as exc:
        print(f"read-play-vitals: {exc}", file=sys.stderr)
        return 2
    except (KeyError, TypeError, ValueError) as exc:
        print(f"read-play-vitals: response shape not understood ({type(exc).__name__}: {exc})", file=sys.stderr)
        return 2
    print(json.dumps(snapshot, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
