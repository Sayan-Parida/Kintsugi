#!/bin/sh
set -eu
umask 077
fail() { printf 'install: %s\n' "$*" >&2; exit 1; }
[ "$(uname -s)" = Linux ] || fail "Linux is required."
prefix=${HOME:?HOME must be set}/.local
case "$#" in
    0) ;;
    2) [ "$1" = --prefix ] || fail "Usage: sh install.sh [--prefix ABSOLUTE_DIRECTORY]"; prefix=$2 ;;
    *) fail "Usage: sh install.sh [--prefix ABSOLUTE_DIRECTORY]" ;;
esac
case "$prefix" in /*) ;; *) fail "Prefix must be absolute." ;; esac
[ "$prefix" != / ] || fail "The filesystem root cannot be an installation prefix."
for tool in cp mkdir chmod mv mktemp rm rmdir ln stat id; do
    command -v "$tool" >/dev/null || fail "Required utility is unavailable: $tool"
done
src=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
for file in kintsugi kintsugi.jar uninstall.sh LICENSE NOTICE THIRD-PARTY-NOTICES.md; do
    [ -f "$src/$file" ] || fail "Incomplete bundle: missing $file"
done
[ ! -e "$prefix/bin/kintsugi" ] && [ ! -L "$prefix/bin/kintsugi" ] ||
    fail "Launcher already exists. Uninstall the existing copy first."
[ ! -e "$prefix/lib/kintsugi" ] && [ ! -L "$prefix/lib/kintsugi" ] ||
    fail "Application directory already exists. Uninstall the existing copy first."
mkdir -p -- "$prefix/bin" "$prefix/lib" || fail "Cannot create user-local directories."
for dir in "$prefix" "$prefix/bin" "$prefix/lib"; do
    [ ! -L "$dir" ] && [ "$(stat -c %u "$dir")" = "$(id -u)" ] ||
        fail "Installation directories must be owned by the current user and cannot be symlinks."
    mode=$(stat -c %a "$dir")
    [ $((0$mode & 022)) -eq 0 ] || fail "Installation directories cannot be writable by other users."
done
stage=$(mktemp -d "$prefix/lib/.kintsugi-install.XXXXXX")
cleanup() {
    rm -f -- "$stage/kintsugi" "$stage/kintsugi.jar" "$stage/uninstall.sh" "$stage/LICENSE" "$stage/NOTICE" "$stage/THIRD-PARTY-NOTICES.md" "$stage/INSTALLATION"
    rmdir -- "$stage" 2>/dev/null || :
}
trap cleanup EXIT HUP INT TERM
cp -- "$src/kintsugi" "$src/kintsugi.jar" "$src/uninstall.sh" "$src/LICENSE" "$src/NOTICE" "$src/THIRD-PARTY-NOTICES.md" "$stage/"
printf '%s\n' 'kintsugi-0.1.0-alpha' > "$stage/INSTALLATION"
chmod 755 "$stage/kintsugi" "$stage/uninstall.sh"
if [ -n "${JAVA_HOME:-}" ]; then java=$JAVA_HOME/bin/java; else java=$(command -v java) || fail "Java 21 is required."; fi
[ -x "$java" ] || fail "Cannot execute Java. Check JAVA_HOME."
version=$("$java" -version 2>&1) || fail "Cannot execute Java."
case "$version" in *'version "21.'*|*'version "21"'*) ;; *) fail "Java 21 is required." ;; esac
# GNU mv -T refuses a concurrently created nonempty target instead of nesting the stage inside it.
mv -T -- "$stage" "$prefix/lib/kintsugi"
trap - EXIT HUP INT TERM
# A hard link atomically fails if another process creates the launcher first.
ln -- "$prefix/lib/kintsugi/kintsugi" "$prefix/bin/kintsugi"
chmod 755 "$prefix/bin/kintsugi"
printf 'Installed 0.1.0-alpha in %s\nAdd %s/bin to PATH.\n' "$prefix" "$prefix"
