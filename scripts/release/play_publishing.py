"""Coordinate Google Play edits and record the outcome of each guarded commit."""

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
from hashlib import sha256
from http.client import HTTPSConnection
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import parse_qs, urlsplit


SCOPE = "https://www.googleapis.com/auth/androidpublisher"
GUARD = "ERROR_IF_IN_REVIEW"
API_NUM_RETRIES = 5


class PlayStop(Exception):
    """The operator needs independent evidence before continuing."""


def package_id(value):
    if not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+", value):
        raise ValueError(f"Invalid application id: {value}")
    return value


def external_key():
    value = os.environ.get("SZA_PLAY_SERVICE_ACCOUNT_JSON")
    if not value:
        raise PlayStop("Set SZA_PLAY_SERVICE_ACCOUNT_JSON to a key file outside all repositories.")
    path = Path(value).expanduser().resolve()
    if not path.is_file() or any((parent / ".git").exists() for parent in path.parents):
        raise PlayStop("SZA_PLAY_SERVICE_ACCOUNT_JSON must point to an existing file outside all repositories.")
    return path


def state_root():
    if os.name == "nt":
        return Path(os.environ["LOCALAPPDATA"]) / "sza" / "play-publishing"
    return Path.home() / ".local" / "state" / "sza" / "play-publishing"


@contextmanager
def app_lock(package):
    root = state_root()
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    # Keep the lock file in place so waiters and future processes lock the same inode.
    with (root / f"{package_id(package)}.lock").open("a+b") as lock:
        lock.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as exc:
            raise PlayStop(f"Another Google Play edit user holds the lock for {package}.") from exc
        yield root


def read_object(path):
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    except (OSError, ValueError) as exc:
        raise ValueError(f"Cannot read JSON from {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise ValueError(f"{path} must contain a JSON object.")
    return data


def has_reference(value):
    return isinstance(value, str) and bool(value.strip())


def load_preflight(path, package, operations):
    if not path:
        raise PlayStop("Supply --preflight with the approved batch and first-use evidence before opening an edit.")
    try:
        evidence = read_object(path)
    except ValueError as exc:
        raise PlayStop(str(exc)) from exc
    if evidence.get("packageName") != package or evidence.get("operations") != operations:
        raise PlayStop("The approved preflight must match the application and its exact planned operations.")
    if not has_reference(evidence.get("authorization")):
        raise PlayStop("Record the owner's authorization for the exact Play operations.")
    if not has_reference(evidence.get("acceptedArtifact")):
        raise PlayStop("Record an accepted prior artifact for this application before using the API publisher.")
    if not has_reference(evidence.get("manifestComparison")):
        raise PlayStop("Record the manifest comparison or that this edit carries no artifact.")
    declarations = evidence.get("newForegroundServices")
    if not isinstance(declarations, dict) or any(
        not has_reference(kind) or not has_reference(saved) for kind, saved in declarations.items()
    ):
        raise PlayStop("Record a saved App content declaration for every new foreground-service type.")
    return evidence


def file_sha256(path):
    with open(path, "rb") as stream:
        digest = sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
        return digest.hexdigest()


def json_sha256(value):
    return sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def utc_now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def save_record(path, record):
    staged = path.with_suffix(".tmp")
    with staged.open("w", encoding="utf-8") as stream:
        json.dump(record, stream, indent=2, ensure_ascii=False)
        stream.flush()
        os.fsync(stream.fileno())
    staged.replace(path)


def pending_path(root, package):
    return root / f"{package}.pending.json"


def receipt_path(root, package, record):
    batch = json_sha256([record["authorization"], record["operations"]])
    return root / f"{package}.{batch}.committed.json"


def ensure_writes_allowed(root, package, evidence=None):
    pending = pending_path(root, package)
    if pending.exists():
        raise PlayStop(f"{package} has an unresolved commit in {pending}. Read the result back before any write.")
    if evidence is not None and receipt_path(root, package, evidence).exists():
        raise PlayStop(f"This authorized batch for {package} already has a commit receipt.")


def reconcile(package, edit_id, outcome, reference):
    if outcome not in ("committed", "not-committed") or not has_reference(reference):
        raise ValueError("Record a confirmed outcome and reference to independent read-back.")
    with app_lock(package) as root:
        pending = pending_path(root, package)
        record = read_object(pending)
        if record["editId"] != edit_id or record["packageName"] != package:
            raise PlayStop("The observation must match the pending application and edit.")
        record.update(outcome=outcome, readBack=reference, reconciledAt=utc_now())
        save_record(pending, record)
        destination = receipt_path(root, package, record) if outcome == "committed" else (
            root / f"{package}.{sha256(edit_id.encode()).hexdigest()}.not-committed.json"
        )
        pending.replace(destination)
        return destination


class SingleRequestHttp:
    """Use an authenticated connection that does not replay a commit on a broken socket."""

    def __init__(self, credentials):
        self.credentials = credentials

    def request(self, uri, method="GET", body=None, headers=None, **_options):
        from google_auth_httplib2 import Request
        from httplib2 import Http, Response

        url = urlsplit(uri)
        if (url.scheme, url.netloc, method) != ("https", "androidpublisher.googleapis.com", "POST"):
            raise ValueError("Unexpected endpoint for a Google Play commit.")
        headers = dict(headers or {})
        self.credentials.before_request(Request(Http(timeout=120)), method, uri, headers)
        target = url.path + ("?" + url.query if url.query else "")
        connection = HTTPSConnection(url.hostname, timeout=120)
        try:
            connection.request(method, target, body=body, headers=headers)
            response = connection.getresponse()
            response_headers = dict(response.getheaders())
            response_headers["status"] = str(response.status)
            return Response(response_headers), response.read()
        finally:
            connection.close()


def connect_publisher(package):
    from google.auth.exceptions import GoogleAuthError
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    key = external_key()
    try:
        credentials = service_account.Credentials.from_service_account_file(str(key), scopes=[SCOPE])
    except (GoogleAuthError, OSError, ValueError) as exc:
        raise PlayStop("The configured service-account key could not be loaded.") from exc
    service = build("androidpublisher", "v3", credentials=credentials, static_discovery=True)
    # Discovery can lag the REST API. Check argument support before the publisher opens an edit.
    try:
        uri = service.edits().commit(
            packageName=package, editId="discovery-check", changesInReviewBehavior=GUARD
        ).uri
        if parse_qs(urlsplit(uri).query).get("changesInReviewBehavior") != [GUARD]:
            raise ValueError("missing guard in request URI")
    except (AttributeError, TypeError, ValueError) as exc:
        raise PlayStop("Install a Google Play API client with changesInReviewBehavior support.") from exc
    return service, SingleRequestHttp(credentials)


def connect_reader(legacy_key):
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    key = external_key() if os.environ.get("SZA_PLAY_SERVICE_ACCOUNT_JSON") else Path(legacy_key)
    credentials = service_account.Credentials.from_service_account_file(str(key), scopes=[SCOPE])
    return build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)


def status_code(exc):
    status = getattr(getattr(exc, "resp", None), "status", None)
    return status if isinstance(status, int) else None


def is_edit_invalidated(exc):
    return status_code(exc) in (400, 404, 409) and bool(
        re.search(r"\b(?:this |the )?edit (?:has been|was) deleted\b", str(exc), re.IGNORECASE)
    )


def error_reason(exc, reason):
    try:
        error = json.loads(exc.content)["error"]
    except (AttributeError, TypeError, ValueError, KeyError):
        return False
    details = error.get("details") if isinstance(error, dict) else None
    return isinstance(details, list) and any(
        detail.get("reason") == reason for detail in details if isinstance(detail, dict)
    )


def hold_required(exc):
    message = str(exc).lower()
    return (status_code(exc) == 400 and "changesnotsentforreview" in message
            and ("cannot be sent for review automatically" in message
                 or bool(re.search(r"changesnotsentforreview (?:must be set to|is required to be) true", message))))


def validate_edit(service, package, edit_id):
    try:
        service.edits().validate(packageName=package, editId=edit_id).execute(num_retries=API_NUM_RETRIES)
        return True
    except Exception as exc:
        if not hold_required(exc):
            raise
        print("Play requires the held mode. This edit could not be validated through the API.")
        return False


def confirm_console(package, edit_id, mode, evidence):
    challenge = f"{package} {edit_id} {mode}"
    sys.stdout.flush()
    print(f"Confirm Publishing overview is clear of review and contains only the approved batch "
          f"({evidence['authorization']}). Type '{challenge}' to commit.", file=sys.stderr, flush=True)
    terminal = "CONIN$" if os.name == "nt" else "/dev/tty"
    try:
        with open(terminal, "r", encoding="utf-8") as console:
            answer = console.readline()
    except OSError as exc:
        raise PlayStop("An operator must verify Publishing overview immediately before committing.") from exc
    if answer.strip() != challenge:
        raise PlayStop("Commit stopped. The Publishing overview confirmation did not match.")


def discard_edit(edits, package, edit_id):
    try:
        edits.delete(packageName=package, editId=edit_id).execute(num_retries=API_NUM_RETRIES)
    except Exception as exc:
        if status_code(exc) != 404 and not is_edit_invalidated(exc):
            raise PlayStop(f"Could not close Play edit {edit_id}. Its closure must be verified before a new edit.") from exc


@contextmanager
def open_edit(service, package, root):
    edits = service.edits()
    active = root / f"{package}.active.json"
    pending = pending_path(root, package)
    if active.exists():
        discard_edit(edits, package, read_object(active)["editId"])
        active.unlink()
    edit_id = edits.insert(packageName=package, body={}).execute()["id"]
    try:
        save_record(active, {"editId": edit_id})
    except OSError as exc:
        discard_edit(edits, package, edit_id)
        raise PlayStop(f"Could not persist the open Play edit {edit_id}. It was closed.") from exc
    edit = {"id": edit_id, "committed": False}
    try:
        yield edit
    except Exception as exc:
        if is_edit_invalidated(exc):
            raise PlayStop("The Play edit was invalidated. Check other edit users and Console activity.") from exc
        raise
    finally:
        if not pending.exists() or read_object(pending)["editId"] != edit_id:
            if not edit["committed"]:
                discard_edit(edits, package, edit_id)
            active.unlink()


def ensure_probe_allowed(service, root, package):
    pending = pending_path(root, package)
    if not pending.exists():
        return
    edit_id = read_object(pending)["editId"]
    try:
        service.edits().get(packageName=package, editId=edit_id).execute(num_retries=API_NUM_RETRIES)
    except Exception as exc:
        if status_code(exc) == 404 or is_edit_invalidated(exc):
            return
        raise PlayStop(f"Could not verify whether unresolved edit {edit_id} is closed.") from exc
    raise PlayStop(f"Unresolved edit {edit_id} is still open. Inspect Publishing overview without opening another edit.")


@contextmanager
def probe_edit(package, legacy_key):
    with app_lock(package) as root:
        service = connect_reader(legacy_key)
        ensure_probe_allowed(service, root, package)
        with open_edit(service, package, root) as edit:
            yield service, edit["id"]


@contextmanager
def publishing_edit(package, operations, preflight, connector=None):
    evidence = load_preflight(preflight, package, operations)
    with app_lock(package) as root:
        ensure_writes_allowed(root, package, evidence)
        service, http = (connector or connect_publisher)(package)
        with open_edit(service, package, root) as edit:
            yield service, edit, root, evidence, http


def commit_edit(service, package, edit, root, evidence, http=None, confirm=None):
    confirm = confirm or confirm_console
    edit_id = edit["id"]
    pending = pending_path(root, package)
    record = {"packageName": package, "editId": edit_id, **{
        key: evidence[key] for key in ("authorization", "operations")
    }}

    def send(mode):
        held = mode == "held"
        confirm(package, edit_id, mode, evidence)
        request = service.edits().commit(
            packageName=package, editId=edit_id, changesInReviewBehavior=GUARD,
            **({"changesNotSentForReview": True} if held else {})
        )
        record.update(mode=mode, attemptedAt=utc_now(), outcome="unknown")
        try:
            save_record(pending, record)
        except OSError as exc:
            raise PlayStop("Could not persist the pending commit. No commit was sent.") from exc
        try:
            response = request.execute(http=http)
            if not isinstance(response, dict) or response.get("id") != edit_id:
                raise ValueError("Commit response did not identify the edit.")
        except Exception as exc:
            status = status_code(exc)
            if (error_reason(exc, "CHANGES_ALREADY_IN_REVIEW")
                    or (status == 400 and "already have changes in review" in str(exc).lower())):
                pending.unlink()
                raise PlayStop("Google Play reports changes already in review. Wait for that review.") from exc
            if is_edit_invalidated(exc) or (status and 400 <= status < 500 and status not in (408, 409, 429)):
                pending.unlink()
                raise
            raise PlayStop(f"Commit outcome unknown for {package}, edit {edit_id}. "
                           f"Use independent read-back before any further write. Record: {pending}") from exc
        edit["committed"] = True
        record.update(outcome="committed", committedAt=utc_now())
        try:
            save_record(pending, record)
            pending.replace(receipt_path(root, package, record))
        except OSError as exc:
            raise PlayStop(f"Play accepted edit {edit_id}, but its receipt could not be saved. "
                           f"Reconcile the pending record: {pending}") from exc

    try:
        send("automatic")
    except Exception as exc:
        if not hold_required(exc):
            raise
        print("Play requires the held mode. Recheck Publishing overview before the second request.")
        send("held")
        return True
    return False


def main(argv=None):
    parser = argparse.ArgumentParser(description="Record the read-back of an uncertain Play commit.")
    parser.add_argument("command", choices=["reconcile"])
    parser.add_argument("--package", required=True)
    parser.add_argument("--edit-id", required=True)
    parser.add_argument("--outcome", required=True, choices=["committed", "not-committed"])
    parser.add_argument("--evidence", required=True, help="Reference to independently observed Play state.")
    args = parser.parse_args(argv)
    try:
        path = reconcile(args.package, args.edit_id, args.outcome, args.evidence)
    except (OSError, PlayStop, ValueError) as exc:
        print(f"Reconciliation stopped: {exc}", file=sys.stderr)
        return 2
    print(f"Read-back recorded: {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
