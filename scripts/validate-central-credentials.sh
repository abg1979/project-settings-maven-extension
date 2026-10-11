#!/usr/bin/env bash
set -euo pipefail

export MAVEN_USERNAME="${MAVEN_USERNAME:-${OSSRH_USERNAME:-}}"
export MAVEN_PASSWORD="${MAVEN_PASSWORD:-${OSSRH_TOKEN:-}}"

if [[ -z "$MAVEN_USERNAME" || -z "$MAVEN_PASSWORD" ]]; then
  echo "Set MAVEN_USERNAME and MAVEN_PASSWORD, or OSSRH_USERNAME and OSSRH_TOKEN." >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is required to validate credentials through effective settings." >&2
  exit 1
fi

umask 077
temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/central-credentials.XXXXXX")"
generated_settings="$temp_dir/settings.xml"
effective_settings="$temp_dir/effective-settings.xml"
maven_log="$temp_dir/maven.log"

cleanup() {
  local status=$?
  trap - EXIT
  if ! rm -f -- "$generated_settings" "$effective_settings" "$maven_log"; then
    echo "Could not remove temporary Maven credential-check files." >&2
    status=1
  fi
  if ! rmdir -- "$temp_dir"; then
    echo "Could not remove temporary Maven credential-check directory." >&2
    status=1
  fi
  exit "$status"
}
trap cleanup EXIT

settings_file="${MAVEN_SETTINGS_FILE:-}"
if [[ -z "$settings_file" ]]; then
  cat >"$generated_settings" <<'XML'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
  <servers>
    <server>
      <id>central</id>
      <username>${env.MAVEN_USERNAME}</username>
      <password>${env.MAVEN_PASSWORD}</password>
    </server>
  </servers>
</settings>
XML
  settings_file="$generated_settings"
elif [[ ! -f "$settings_file" ]]; then
  echo "MAVEN_SETTINGS_FILE does not point to a readable settings file." >&2
  exit 1
fi

if ! mvn --batch-mode --no-transfer-progress \
  --settings "$settings_file" \
  org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-settings \
  -DshowPasswords=true \
  "-Doutput=$effective_settings" >"$maven_log" 2>&1; then
  echo "Maven could not produce effective settings; Maven output was suppressed to avoid exposing credentials." >&2
  exit 1
fi

python3 - "$effective_settings" <<'PY'
import base64
import sys
import xml.etree.ElementTree as ET
import urllib.error
import urllib.request

try:
    settings = ET.parse(sys.argv[1])
except (ET.ParseError, OSError):
    raise SystemExit("::error::Could not read Maven's effective settings output.")

root_tag = settings.getroot().tag
namespace = root_tag.rsplit("}", 1)[0] + "}" if "}" in root_tag else ""
server = next(
    (
        element
        for element in settings.findall(f".//{namespace}server")
        if element.findtext(f"{namespace}id") == "central"
    ),
    None,
)
if server is None:
    raise SystemExit("::error::Maven's effective settings do not contain server id 'central'.")

username = server.findtext(f"{namespace}username")
password = server.findtext(f"{namespace}password")
if not username or not password or "${" in username or "${" in password:
    raise SystemExit("::error::Maven did not resolve the Central credentials from its effective settings.")

token = base64.b64encode(f"{username}:{password}".encode()).decode()
request = urllib.request.Request(
    "https://central.sonatype.com/api/v1/publisher/deployments",
    headers={"Authorization": f"Bearer {token}"},
)

try:
    with urllib.request.urlopen(request, timeout=20) as response:
        status = response.status
except urllib.error.HTTPError as error:
    status = error.code
    error.close()
except (urllib.error.URLError, TimeoutError):
    raise SystemExit("::error::Could not reach the Central Publisher Portal API.")

if status == 200:
    print(
        "Maven resolved the Central credentials and Central accepted them (HTTP 200). "
        "This confirms API authentication, not namespace publishing permission or artifact upload."
    )
elif status == 401:
    raise SystemExit(
        "::error::Central rejected the Maven-resolved credentials (HTTP 401). "
        "Check that the values are Central Publisher Portal user-token credentials."
    )
elif status == 403:
    raise SystemExit("::error::Central denied access (HTTP 403). Check the Portal account's access.")
else:
    raise SystemExit(f"::error::Unexpected Central Publisher API response: HTTP {status}.")
PY
