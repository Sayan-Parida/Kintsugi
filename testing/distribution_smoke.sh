#!/bin/sh
# Local release lifecycle checks. No VM, profile, enrollment or key is accessed.
set -eu
umask 077
[ "$(uname -s)" = Linux ] || { echo "Linux is required." >&2; exit 1; }
cd "$(dirname "$0")/.."
command -v jq >/dev/null || { echo "Install jq to run this developer test." >&2; exit 1; }
if [ -n "${JAVA_HOME:-}" ]; then java=$JAVA_HOME/bin/java; else java=$(command -v java); fi
dist=$PWD/target/dist
(cd "$dist" && sha256sum -c SHA256SUMS)
work=$(mktemp -d)
# Only this test's fresh, private temporary directory is removed.
trap 'rm -rf -- "$work"' EXIT HUP INT TERM
tar -xzf "$dist/kintsugi-0.1.0-alpha-linux.tar.gz" -C "$work"
bundle=$work/kintsugi-0.1.0-alpha
find "$bundle" -type f -printf '%P\n' | sort > "$work/actual"
printf '%s\n' LICENSE NOTICE THIRD-PARTY-NOTICES.md install.sh kintsugi kintsugi.jar licenses/Apache-2.0.txt uninstall.sh | sort > "$work/expected"
cmp "$work/expected" "$work/actual"
[ "$(find "$bundle" -type l | wc -l)" -eq 0 ]
"$java" -jar "$dist/kintsugi-0.1.0-alpha.jar" --help > "$work/standalone-help"
grep -q 'inventory' "$work/standalone-help"
export HOME="$work/home with spaces"
mkdir "$HOME"
printf 'private operator record\n' > "$HOME/operator-evidence"
expect_failure() {
    if "$@" > "$work/refusal" 2>&1; then
        echo "Expected refusal: $*" >&2
        exit 1
    fi
}
check_cli() {
    "$1/bin/kintsugi" --help > "$work/help"
    grep -q 'assess' "$work/help"
    "$1/bin/kintsugi" inventory --json > "$work/inventory.json"
    jq -e '.command == "inventory" and (.jvms | type == "array")' "$work/inventory.json" >/dev/null
    set +e
    "$1/bin/kintsugi" unsupported-release-test-command --json > "$work/invalid" 2>&1
    result=$?
    set -e
    [ "$result" -eq 2 ]
    cmp "$bundle/licenses/Apache-2.0.txt" "$1/lib/kintsugi/licenses/Apache-2.0.txt"
}
sh "$bundle/install.sh"
prefix=$HOME/.local
check_cli "$prefix"
printf 'unrelated\n' > "$prefix/bin/unrelated"
cp "$prefix/bin/kintsugi" "$work/original-launcher"
expect_failure sh "$bundle/install.sh"
cmp "$work/original-launcher" "$prefix/bin/kintsugi"
sh "$prefix/lib/kintsugi/uninstall.sh"
[ ! -e "$prefix/bin/kintsugi" ] && [ ! -e "$prefix/lib/kintsugi" ]
[ "$(cat "$HOME/operator-evidence")" = 'private operator record' ]
[ "$(cat "$prefix/bin/unrelated")" = unrelated ]
echo "PASS: default installation, standalone/installed CLI, duplicate refusal and removal"
prefix="$work/custom prefix"
sh "$bundle/install.sh" --prefix "$prefix"
check_cli "$prefix"
# Replace the test launcher inode, leaving the installed comparison copy intact.
rm "$prefix/bin/kintsugi"
printf 'changed\n' > "$prefix/bin/kintsugi"
expect_failure sh "$prefix/lib/kintsugi/uninstall.sh" --prefix "$prefix"
[ -f "$prefix/lib/kintsugi/kintsugi.jar" ]
rm "$prefix/bin/kintsugi"
ln "$prefix/lib/kintsugi/kintsugi" "$prefix/bin/kintsugi"
sh "$prefix/lib/kintsugi/uninstall.sh" --prefix "$prefix"
[ ! -e "$prefix/bin/kintsugi" ] && [ ! -e "$prefix/lib/kintsugi" ]
echo "PASS: custom prefix with spaces, changed-launcher refusal and removal"
sh "$bundle/install.sh" --prefix "$prefix"
printf 'preserve\n' > "$prefix/lib/kintsugi/extra"
expect_failure sh "$prefix/lib/kintsugi/uninstall.sh" --prefix "$prefix"
[ "$(cat "$prefix/lib/kintsugi/extra")" = preserve ]
echo "PASS: additional application files preserved and reported"
mkdir "$work/unsafe"
chmod 777 "$work/unsafe"
expect_failure sh "$bundle/install.sh" --prefix "$work/unsafe"
[ ! -e "$work/unsafe/lib/kintsugi" ]
mkdir "$work/real"
ln -s "$work/real" "$work/symlink"
expect_failure sh "$bundle/install.sh" --prefix "$work/symlink"
[ ! -e "$work/real/lib/kintsugi" ]
expect_failure sh "$bundle/install.sh" --prefix relative
echo "PASS: unsafe, symlink and relative prefixes refused"
echo "Distribution smoke checks passed. This is not protected-VM qualification."
