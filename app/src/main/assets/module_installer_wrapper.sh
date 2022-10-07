#!/sbin/sh

# echo before loading util_functions
ui_print() { echo "$1"; }

OUTFD=$2
ZIPFILE=$3
TMPDIR=/dev/tmp

# Prevent setenforce because it can causes issues on some devices
setenforce() { true; }
# prevent old modules from disabling hidden_apis, please use LSPosed library instead.
# See: https://github.com/LSPosed/AndroidHiddenApiBypass
settings() {
  if [ "$1" == "put" ] && [ "$2" == "global" ] && ([ "$3" == "hidden_api_policy" ] || \
  [ "$3" == "hidden_api_policy_p_apps" ] || [ "$3" == "hidden_api_policy_pre_p_apps" ]); then
    true
  else
    "$(which settings)" "$@"
  fi
}

rm -rf $TMPDIR 2>/dev/null
mkdir -p $TMPDIR
chcon u:object_r:system_file:s0 $TMPDIR || true
cd $TMPDIR

abort() {
  ui_print "$1"
  rm -rf $TMPDIR
  exit 1
}

unzip -o "$ZIPFILE" "META-INF/com/google/android/update-binary" -d $TMPDIR >&2
[ ! -f "$TMPDIR/META-INF/com/google/android/update-binary" ] && abort "! Unable to extract zip file!"

. "$TMPDIR/META-INF/com/google/android/update-binary"

rm -rf $TMPDIR
exit 0
