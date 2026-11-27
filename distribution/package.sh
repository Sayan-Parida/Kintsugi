#!/bin/sh
set -eu
[ "$(uname -s)" = Linux ] || { echo "Packaging requires Linux." >&2; exit 1; }
cd "$(dirname "$0")/.."
version=0.1.0-alpha
jar=orchestrator/target/orchestrator-$version.jar
[ -f "$jar" ] || { echo "Build first with sh ./mvnw verify." >&2; exit 1; }
mkdir -p target/dist
stage=$(mktemp -d)
bundle=$stage/kintsugi-$version
mkdir "$bundle"
mkdir "$bundle/licenses"
cp "$jar" "$bundle/kintsugi.jar"
cp distribution/kintsugi distribution/install.sh distribution/uninstall.sh LICENSE NOTICE THIRD-PARTY-NOTICES.md "$bundle/"
cp licenses/Apache-2.0.txt "$bundle/licenses/"
chmod 755 "$bundle/kintsugi" "$bundle/install.sh" "$bundle/uninstall.sh"
cp "$jar" "target/dist/kintsugi-$version.jar"
cp RELEASE-NOTES.md target/dist/RELEASE-NOTES.md
tar -czf "target/dist/kintsugi-$version-linux.tar.gz" -C "$stage" "kintsugi-$version"
(cd target/dist && sha256sum "kintsugi-$version.jar" "kintsugi-$version-linux.tar.gz" RELEASE-NOTES.md > SHA256SUMS)
# Delete only the known staging files.
rm "$bundle/kintsugi.jar" "$bundle/kintsugi" "$bundle/install.sh" "$bundle/uninstall.sh" "$bundle/LICENSE" "$bundle/NOTICE" "$bundle/THIRD-PARTY-NOTICES.md"
rm "$bundle/licenses/Apache-2.0.txt"
rmdir "$bundle/licenses" "$bundle" "$stage"
