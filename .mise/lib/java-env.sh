# Prefer the configured JDK, then discover JDK 21 without requiring Mise.
_java_candidates=("${JAVA_HOME:-}")
if command -v java >/dev/null 2>&1; then
    _java_candidates+=("$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")")
fi
_java_candidates+=(/usr/lib/jvm/*21* "$HOME"/.local/share/mise/installs/java/*21*)
_java_found=false
for _java_candidate in "${_java_candidates[@]}"; do
    if [[ -x "$_java_candidate/bin/java" && -f "$_java_candidate/release" ]] &&
        grep -Eq '^JAVA_VERSION="21([."]|$)' "$_java_candidate/release"; then
        export JAVA_HOME="$_java_candidate"
        export PATH="$JAVA_HOME/bin:$PATH"
        _java_found=true
        break
    fi
done
if ! $_java_found; then
    echo 'JDK 21 required. Run mise install, or set JAVA_HOME to an installed JDK 21.' >&2
    exit 1
fi
unset _java_candidates _java_candidate _java_found
