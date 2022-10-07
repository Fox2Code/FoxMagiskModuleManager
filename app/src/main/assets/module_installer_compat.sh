#!/sbin/sh

#################
# Initialization
#################

umask 022

# echo before loading util_functions
ui_print() { echo "$1"; }

require_new_magisk() {
  ui_print "*******************************"
  ui_print " Please install Magisk v19.0+! "
  ui_print "*******************************"
  exit 1
}

#########################
# Load util_functions.sh
#########################

OUTFD=$2
ZIPFILE=$3

mount /data 2>/dev/null

[ -f /data/adb/magisk/util_functions.sh ] || require_new_magisk
. /data/adb/magisk/util_functions.sh
[ $MAGISK_VER_CODE -lt 19000 ] && require_new_magisk

# Add grep_get_prop implementation if missing
if ! type grep_get_prop &>/dev/null; then
grep_get_prop() {
  local result=$(grep_prop $@)
  if [ -z "$result" ]; then
    # Fallback to getprop
    getprop "$1"
  else
    echo $result
  fi
}
fi

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

if [ $MAGISK_VER_CODE -ge 20400 ] && [ -z "$MMM_MMT_REBORN" ]; then
  # New Magisk have complete installation logic within util_functions.sh
  install_module
  exit 0
fi

#######################################################
# Legacy Support + compat mode for MMT Reborn template
#######################################################

TMPDIR=/dev/tmp
PERSISTDIR=/sbin/.magisk/mirror/persist

is_legacy_script() {
  unzip -l "$ZIPFILE" install.sh | grep -q install.sh
  return $?
}

print_modname() {
  local authlen len namelen pounds
  namelen=`echo -n $MODNAME | wc -c`
  authlen=$((`echo -n $MODAUTH | wc -c` + 3))
  [ $namelen -gt $authlen ] && len=$namelen || len=$authlen
  len=$((len + 2))
  pounds=$(printf "%${len}s" | tr ' ' '*')
  ui_print "$pounds"
  ui_print " $MODNAME "
  ui_print " by $MODAUTH "
  ui_print "$pounds"
  ui_print "*******************"
  ui_print " Powered by Magisk "
  ui_print "*******************"
}

# Override abort as old scripts have some issues
abort() {
  ui_print "$1"
  $BOOTMODE || recovery_cleanup
  [ -n $MODPATH ] && rm -rf $MODPATH
  rm -rf $TMPDIR
  exit 1
}

rm -rf $TMPDIR 2>/dev/null
mkdir -p $TMPDIR
chcon u:object_r:system_file:s0 $TMPDIR || true
cd $TMPDIR

# Preperation for flashable zips
setup_flashable

# Mount partitions
mount_partitions

# Detect version and architecture
api_level_arch_detect
API=$(grep_get_prop ro.build.version.sdk)

# Setup busybox and binaries
$BOOTMODE && boot_actions || recovery_actions

##############
# Preparation
##############

# Extract prop file
unzip -o "$ZIPFILE" module.prop -d $TMPDIR >&2
[ ! -f $TMPDIR/module.prop ] && abort "! Unable to extract zip file!"

[ -z "$NVBASE" ] && NVBASE="/data/adb"
MODDIRNAME=modules
$BOOTMODE && MODDIRNAME=modules_update
MODULEROOT=$NVBASE/$MODDIRNAME
MODID=`grep_prop id $TMPDIR/module.prop`
MODNAME=`grep_prop name $TMPDIR/module.prop`
MODAUTH=`grep_prop author $TMPDIR/module.prop`
MODPATH=$MODULEROOT/$MODID

# Create mod paths
rm -rf $MODPATH 2>/dev/null
mkdir -p $MODPATH

##########
# Install
##########

if is_legacy_script; then
  unzip -oj "$ZIPFILE" module.prop install.sh uninstall.sh 'common/*' -d $TMPDIR >&2

  # Load install script
  . $TMPDIR/install.sh

  # Callbacks
  print_modname
  on_install

  # Custom uninstaller
  [ -f $TMPDIR/uninstall.sh ] && cp -af $TMPDIR/uninstall.sh $MODPATH/uninstall.sh

  # Skip mount
  $SKIPMOUNT && touch $MODPATH/skip_mount

  # prop file
  $PROPFILE && cp -af $TMPDIR/system.prop $MODPATH/system.prop

  # Module info
  cp -af $TMPDIR/module.prop $MODPATH/module.prop

  # post-fs-data scripts
  $POSTFSDATA && cp -af $TMPDIR/post-fs-data.sh $MODPATH/post-fs-data.sh

  # service scripts
  $LATESTARTSERVICE && cp -af $TMPDIR/service.sh $MODPATH/service.sh

  ui_print "- Setting permissions"
  set_permissions
elif [ -n "$MMM_MMT_REBORN" ]; then
  # https://github.com/iamlooper/MMT-Reborn
  ui_print "[*] Using FoxMMM MMT-Reborn compatibility mode"

  load_vksel() { source "$MODPATH/addon/Volume-Key-Selector/install.sh"; }

  rmtouch() { [[ -e "$1" ]] && rm -rf "$1" 2>/dev/null; }

  unzip -o "$ZIPFILE" -d "$MODPATH" >&2

  # Load install script
  source "$MODPATH/setup.sh"

  # Remove all old files before doing installation if want to
  "$CLEANSERVICE" && rm -rf "/data/adb/modules/$MODID"

  # Enable debugging if true
  "$DEBUG" && set -x || set +x

  # Print mod info
  info_print

  # Auto vskel load
  "$AUTOVKSEL" && load_vksel

  # Main
  init_main

  # Skip mount
  "$SKIPMOUNT" && touch "$MODPATH/skip_mount"

  # Set permissions
  set_permissions

  # Remove stuffs that don't belong to modules
  rmtouch "$MODPATH/META-INF"
  rmtouch "$MODPATH/addon"
  rmtouch "$MODPATH/setup.sh"
  rmtouch "$MODPATH/LICENSE"
  rmtouch "$MODPATH/README.md"
  rmtouch "$MODPATH/system/bin/placeholder"
  rmtouch "$MODPATH/zygisk/placeholder"
  ui_print "[*] Exiting FoxMMM MMT-Reborn compatibility mode"
  sleep 0.5
else
  print_modname

  unzip -o "$ZIPFILE" customize.sh -d $MODPATH >&2

  if ! grep -q '^SKIPUNZIP=1$' $MODPATH/customize.sh 2>/dev/null; then
    ui_print "- Extracting module files"
    unzip -o "$ZIPFILE" -x 'META-INF/*' -d $MODPATH >&2

    # Default permissions
    set_perm_recursive $MODPATH 0 0 0755 0644
  fi

  # Load customization script
  [ -f $MODPATH/customize.sh ] && . $MODPATH/customize.sh
fi

# Handle replace folders
for TARGET in $REPLACE; do
  ui_print "- Replace target: $TARGET"
  mktouch $MODPATH$TARGET/.replace
done

if $BOOTMODE; then
  # Update info for Magisk Manager
  mktouch $NVBASE/modules/$MODID/update
  rm -rf $NVBASE/modules/$MODID/remove 2>/dev/null
  rm -rf $NVBASE/modules/$MODID/disable 2>/dev/null
  cp -af $MODPATH/module.prop $NVBASE/modules/$MODID/module.prop
fi

# Copy over custom sepolicy rules
if ! type copy_sepolicy_rules &>/dev/null; then
  if [ -f $MODPATH/sepolicy.rule -a -e $PERSISTDIR ]; then
    ui_print "- Installing custom sepolicy patch"
    # Remove old recovery logs (which may be filling partition) to make room
    rm -f $PERSISTDIR/cache/recovery/*
    PERSISTMOD=$PERSISTDIR/magisk/$MODID
    mkdir -p $PERSISTMOD
    cp -af $MODPATH/sepolicy.rule $PERSISTMOD/sepolicy.rule || abort "! Insufficient partition size"
  fi
else
  if [ -f $MODPATH/sepolicy.rule ]; then
    ui_print "- Installing custom sepolicy rules"
    copy_sepolicy_rules
  fi
fi

# Remove stuff that doesn't belong to modules and clean up any empty directories
rm -rf \
$MODPATH/system/placeholder $MODPATH/customize.sh \
$MODPATH/README.md $MODPATH/.git* 2>/dev/null
rmdir -p $MODPATH

#############
# Finalizing
#############

cd /
$BOOTMODE || recovery_cleanup
rm -rf $TMPDIR

ui_print "- Done"
exit 0
