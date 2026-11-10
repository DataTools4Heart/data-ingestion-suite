#!/bin/bash
#
# Upgrade the onFHIR MongoDB data in place to MongoDB 7.0 without losing data.
#
# MongoDB cannot skip major versions: the data files must be opened by every
# major release in turn (... 4.4 -> 5.0 -> 6.0 -> 7.0), and featureCompatibility-
# Version (FCV) must be raised to each release before moving to the next. This
# script does exactly that on the existing data volume, using short-lived
# containers, after taking two backups:
#
#   1. a logical dump  (mongodump --archive --gzip)   -> restorable into any version
#   2. a physical copy (tar of /data/db, mongod down) -> exact rollback to the old image
#
# Usage (run on the server, from any directory):
#   bash upgrade-mongo.sh                      # defaults below
#   MONGO_CONTAINER=my-mongo bash upgrade-mongo.sh
#
# Environment overrides:
#   MONGO_CONTAINER  existing mongo container            (default: dt4h-mongo)
#   APP_CONTAINERS   containers that write to mongo, stopped during the upgrade
#                    (default: dt4h-tofhir-web dt4h-tofhir-server dt4h-onfhir)
#   BACKUP_DIR       where backups go (default: ./mongo-backup-<timestamp>)
#   ASSUME_YES=1     do not prompt (for unattended runs; not recommended)
#
# The script never deletes the old container, the volume, or the backups. When
# it finishes, the volume holds 7.0 data and you start the stack with the
# mongo:7.0 compose file (see the message printed at the end).

set -euo pipefail

MONGO_CONTAINER="${MONGO_CONTAINER:-dt4h-mongo}"
APP_CONTAINERS="${APP_CONTAINERS:-dt4h-tofhir-web dt4h-tofhir-server dt4h-onfhir}"
BACKUP_DIR="${BACKUP_DIR:-./mongo-backup-$(date +%Y%m%d-%H%M%S)}"
TARGET="7.0"
CHAIN=(3.6 4.0 4.2 4.4 5.0 6.0 7.0)
STEP_CONTAINER="mongo-upgrade-step"

log()  { echo; echo "==> $*"; }
die()  { echo "ERROR: $*" >&2; exit 1; }
ask()  {
  [ "${ASSUME_YES:-0}" = "1" ] && return 0
  local a; read -r -p "$1 [y/N] " a
  [ "$a" = "y" ] || [ "$a" = "Y" ] || die "aborted by user"
}

# Run JS in a container with whichever shell it ships (mongo <=5.0, mongosh >=5.0).
js() {
  local c="$1" code="$2" sh
  if docker exec "$c" sh -c 'command -v mongosh' >/dev/null 2>&1; then sh=mongosh; else sh=mongo; fi
  docker exec "$c" "$sh" --quiet --eval "$code"
}

wait_up() {
  local c="$1" i
  for i in $(seq 1 90); do
    if js "$c" 'db.adminCommand({ping: 1}).ok' 2>/dev/null | grep -q 1; then return 0; fi
    if [ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" != "true" ]; then
      docker logs --tail 40 "$c" >&2 || true
      die "mongod in '$c' exited during startup (log above)"
    fi
    sleep 2
  done
  docker logs --tail 40 "$c" >&2 || true
  die "mongod in '$c' did not answer within 180s"
}

major_of() { echo "$1" | cut -d. -f1,2; }

index_of() {
  local v="$1" i
  for i in "${!CHAIN[@]}"; do [ "${CHAIN[$i]}" = "$v" ] && { echo "$i"; return 0; }; done
  return 1
}

COUNT_JS='
db.getMongo().getDBNames().sort().forEach(function (d) {
  if (["admin", "local", "config"].indexOf(d) >= 0) return;
  var x = db.getSiblingDB(d);
  x.getCollectionInfos({type: "collection"}).map(function (i) { return i.name; }).sort().forEach(function (c) {
    if (c.indexOf("system.") === 0) return;
    var col = x.getCollection(c);
    var n = col.countDocuments ? col.countDocuments({}) : col.count();
    var k = col.getIndexes().length;
    print(d + "." + c + " docs=" + n + " indexes=" + k);
  });
});'

FCV_JS='var r = db.adminCommand({getParameter: 1, featureCompatibilityVersion: 1}); print(r.featureCompatibilityVersion.version || r.featureCompatibilityVersion);'

# Start a throwaway mongod of the given image on the volume, pin FCV to its major, stop it.
run_step() {
  local image="$1" major="$2" confirm=""
  log "Opening the data with $image and setting FCV to $major"
  docker rm -f "$STEP_CONTAINER" >/dev/null 2>&1 || true
  docker run -d --name "$STEP_CONTAINER" -v "$MOUNT_SPEC:/data/db" "$image" >/dev/null
  wait_up "$STEP_CONTAINER"
  echo "    server version: $(js "$STEP_CONTAINER" 'print(db.version())')"
  echo "    FCV before:     $(js "$STEP_CONTAINER" "$FCV_JS")"
  # 7.0 refuses setFeatureCompatibilityVersion without confirm:true; older releases reject the field.
  if [ "$(index_of "$major")" -ge "$(index_of 7.0)" ]; then confirm=", confirm: true"; fi
  local out; out="$(js "$STEP_CONTAINER" "printjson(db.adminCommand({setFeatureCompatibilityVersion: \"$major\"$confirm}))")"
  echo "$out" | sed 's/^/    /'
  echo "$out" | grep -Eq '"?ok"? *: *1' || die "setFeatureCompatibilityVersion $major failed"
  local fcv; fcv="$(js "$STEP_CONTAINER" "$FCV_JS")"
  echo "    FCV after:      $fcv"
  [ "$fcv" = "$major" ] || die "FCV is '$fcv', expected '$major'"
  docker stop -t 120 "$STEP_CONTAINER" >/dev/null
  docker rm "$STEP_CONTAINER" >/dev/null
}

# --------------------------------------------------------------------------
log "Inspecting '$MONGO_CONTAINER'"
docker inspect "$MONGO_CONTAINER" >/dev/null 2>&1 || die "container '$MONGO_CONTAINER' not found (set MONGO_CONTAINER)"
[ "$(docker inspect -f '{{.State.Running}}' "$MONGO_CONTAINER")" = "true" ] \
  || die "'$MONGO_CONTAINER' is not running; start it so the current version and counts can be read"

OLD_IMAGE="$(docker inspect -f '{{.Config.Image}}' "$MONGO_CONTAINER")"
MOUNT_SPEC="$(docker inspect -f '{{range .Mounts}}{{if eq .Destination "/data/db"}}{{if eq .Type "volume"}}{{.Name}}{{else}}{{.Source}}{{end}}{{end}}{{end}}' "$MONGO_CONTAINER")"
[ -n "$MOUNT_SPEC" ] || die "no volume or bind mount on /data/db in '$MONGO_CONTAINER' - data would not survive; stop and investigate"

CUR_VERSION="$(js "$MONGO_CONTAINER" 'print(db.version())')"
CUR_MAJOR="$(major_of "$CUR_VERSION")"
CUR_FCV="$(js "$MONGO_CONTAINER" "$FCV_JS")"
CUR_IDX="$(index_of "$CUR_MAJOR")" || die "unsupported current version $CUR_VERSION (script handles ${CHAIN[*]})"

echo "    image:        $OLD_IMAGE"
echo "    version:      $CUR_VERSION (FCV $CUR_FCV)"
echo "    data mount:   $MOUNT_SPEC -> /data/db"
docker exec "$MONGO_CONTAINER" sh -c 'du -sh /data/db 2>/dev/null; df -h /data/db | tail -1' | sed 's/^/    /' || true

if [ "$CUR_MAJOR" = "$TARGET" ] && [ "$CUR_FCV" = "$TARGET" ]; then
  echo "Already on $TARGET with FCV $TARGET - nothing to do."; exit 0
fi

# MongoDB 5.0+ needs a CPU with AVX. Without it the 5.0 step dies with "Illegal instruction".
if ! grep -qw avx /proc/cpuinfo 2>/dev/null; then
  if [ -r /proc/cpuinfo ]; then
    die "this CPU has no AVX - MongoDB 5.0 and later cannot run here. Enable AVX on the VM (e.g. CPU type 'host') first."
  else
    echo "    WARNING: could not read /proc/cpuinfo to check for AVX (MongoDB 5.0+ requires it)."
  fi
fi

STEPS=("${CHAIN[@]:$((CUR_IDX + 1))}")
echo
echo "Plan:"
echo "  1. stop writers: $APP_CONTAINERS"
echo "  2. record per-collection counts, mongodump to $BACKUP_DIR/dump.archive.gz"
echo "  3. stop $MONGO_CONTAINER, tar the data files to $BACKUP_DIR/data-db.tgz"
echo "  4. pin FCV $CUR_MAJOR with $OLD_IMAGE, then upgrade through: ${STEPS[*]:-none}"
echo "  5. compare counts on $TARGET against step 2"
echo "Make sure $BACKUP_DIR has room for roughly 2x the data size shown above."
ask "Proceed?"

mkdir -p "$BACKUP_DIR"
# pwd -W gives a Docker-mountable path under Git Bash on Windows; plain pwd elsewhere.
BACKUP_ABS="$(cd "$BACKUP_DIR" && (pwd -W 2>/dev/null || pwd))"
echo "$OLD_IMAGE"  > "$BACKUP_ABS/old-image.txt"
echo "$MOUNT_SPEC" > "$BACKUP_ABS/data-mount.txt"

log "Stopping application containers so nothing writes during the upgrade"
for c in $APP_CONTAINERS; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" = "true" ]; then
    docker update --restart=no "$c" >/dev/null
    docker stop -t 60 "$c" >/dev/null && echo "    stopped $c"
  fi
done

log "Recording collection counts"
js "$MONGO_CONTAINER" "$COUNT_JS" | tee "$BACKUP_ABS/counts-before.txt"

log "Logical backup (mongodump)"
docker exec "$MONGO_CONTAINER" mongodump --archive --gzip --quiet > "$BACKUP_ABS/dump.archive.gz"
ls -lh "$BACKUP_ABS/dump.archive.gz" | sed 's/^/    /'
[ -s "$BACKUP_ABS/dump.archive.gz" ] || die "dump is empty"

log "Stopping $MONGO_CONTAINER cleanly"
docker update --restart=no "$MONGO_CONTAINER" >/dev/null
docker stop -t 120 "$MONGO_CONTAINER" >/dev/null

log "Physical backup of the data files"
docker run --rm --entrypoint tar -v "$MOUNT_SPEC:/data/db:ro" -v "$BACKUP_ABS:/backup" "$OLD_IMAGE" \
  czf /backup/data-db.tgz -C /data/db .
[ -s "$BACKUP_ABS/data-db.tgz" ] || die "physical backup $BACKUP_ABS/data-db.tgz was not written"
ls -lh "$BACKUP_ABS/data-db.tgz" | sed 's/^/    /'

# Ensure FCV equals the current major before the first jump (it may lag behind).
run_step "$OLD_IMAGE" "$CUR_MAJOR"
for v in "${STEPS[@]}"; do
  docker pull -q "mongo:$v" >/dev/null
  run_step "mongo:$v" "$v"
done

log "Verifying data on mongo:$TARGET"
docker run -d --name "$STEP_CONTAINER" -v "$MOUNT_SPEC:/data/db" "mongo:$TARGET" >/dev/null
wait_up "$STEP_CONTAINER"
js "$STEP_CONTAINER" "$COUNT_JS" > "$BACKUP_ABS/counts-after.txt"
docker stop -t 120 "$STEP_CONTAINER" >/dev/null
docker rm "$STEP_CONTAINER" >/dev/null
if diff "$BACKUP_ABS/counts-before.txt" "$BACKUP_ABS/counts-after.txt"; then
  echo "    all collection counts and index counts match"
else
  die "counts differ (diff above). Data volume is on $TARGET files; backups are in $BACKUP_ABS. Investigate before starting the stack."
fi

cat <<EOF

==> Done. The data in '$MOUNT_SPEC' is now MongoDB $TARGET (FCV $TARGET).

Next steps:
  - Make the compose 'mongo' service use image mongo:$TARGET AND this same data
    mount. If '$MOUNT_SPEC' is not the volume your compose file names (compose
    prefixes the project name, e.g. dt4h-tofhir_onfhirdata), declare it as
    external in the compose file:
        volumes:
          onfhirdata:
            external: true
            name: $MOUNT_SPEC
  - Remove the old container (data lives in the volume, not the container):
        docker rm $MONGO_CONTAINER
  - Start the stack as usual (docker/server/run.sh).

Rollback (only if something is wrong):
  - exact: restore $BACKUP_ABS/data-db.tgz into the data mount and run $OLD_IMAGE
  - or:    start any mongo:$TARGET on an empty volume and
           mongorestore --archive --gzip < $BACKUP_ABS/dump.archive.gz
Keep $BACKUP_ABS until the upgraded server has been checked.
EOF
