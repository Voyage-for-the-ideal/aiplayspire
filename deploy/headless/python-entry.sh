# Resolve entry points in the explicitly selected repo, including frozen flat snapshots.
headless_prefix() {
 if [[ -f "$1/sts_ai_framework/headless/__init__.py" ]]; then
  printf '%s' sts_ai_framework.headless.
 else
  printf '%s' sts_ai_framework.headless_
 fi
}

headless_path() {
 local file
 if [[ -f "$1/sts_ai_framework/headless/__init__.py" ]]; then
  file="sts_ai_framework/headless/$2.py"
 else
  file="sts_ai_framework/headless_$2.py"
 fi
 [[ -f "$1/$file" ]] || { printf 'Missing headless input: %s/%s\n' "$1" "$file" >&2; return 78; }
 printf '%s' "$file"
}

headless_module() {
 headless_path "$1" "$2" >/dev/null || return
 printf '%s%s' "$(headless_prefix "$1")" "$2"
}
