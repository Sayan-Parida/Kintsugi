#!/bin/sh
set -eu
fail() { printf 'uninstall: %s\n' "$*" >&2; exit 1; }
prefix=${HOME:?HOME must be set}/.local
case "$#" in
    0) ;;
    2) [ "$1" = --prefix ] || fail "Usage: sh uninstall.sh [--prefix ABSOLUTE_DIRECTORY]"; prefix=$2 ;;
    *) fail "Usage: sh uninstall.sh [--prefix ABSOLUTE_DIRECTORY]" ;;
esac
case "$prefix" in /*) ;; *) fail "Prefix must be absolute." ;; esac
[ "$prefix" != / ] || fail "Invalid installation prefix."
app=$prefix/lib/kintsugi
[ ! -L "$app" ] && [ -f "$app/INSTALLATION" ] || fail "No recognized installation at this prefix."
[ "$(cat "$app/INSTALLATION")" = kintsugi-0.1.0-alpha ] || fail "Unknown installation marker."
if [ -e "$prefix/bin/kintsugi" ] || [ -L "$prefix/bin/kintsugi" ]; then
    [ ! -L "$prefix/bin/kintsugi" ] && cmp -s "$prefix/bin/kintsugi" "$app/kintsugi" ||
        fail "Launcher has changed. Resolve it before uninstalling."
    rm -- "$prefix/bin/kintsugi"
fi
# Only named installation files are removed. User configuration and evidence are retained.
rm -f -- "$app/kintsugi.jar" "$app/kintsugi" "$app/LICENSE" "$app/NOTICE" "$app/THIRD-PARTY-NOTICES.md" "$app/INSTALLATION" "$app/uninstall.sh"
rm -f -- "$app/licenses/Apache-2.0.txt"
rmdir -- "$app/licenses" 2>/dev/null || :
rmdir -- "$app" || fail "Additional files remain in $app. They were preserved."
printf 'Removed Kintsugi from %s. Configuration and evidence files were retained.\n' "$prefix"
