#!/system/bin/sh
# The script directory is the only reliable module root.
MODDIR="${0%/*}"
(
  [ -d "$MODDIR" ] || exit 0
  [ -f "$MODDIR/mypath/myname.apk" ] || exit 0
  [ -f "$MODDIR/OG/myname.apk" ] || exit 0
  mkdir -p "$MODDIR/temp" || exit 0
  cp -f "$MODDIR/mypath/myname.apk" "$MODDIR/temp/" || exit 0
  cp -f "$MODDIR/OG/myname.apk" "$MODDIR/mypath/" || exit 0

  count=0
  until [ "$(getprop sys.boot_completed)" = "1" ] && [ -d /storage/emulated/0/Android ]; do
    count=$((count + 1))
    [ "$count" -ge 120 ] && exit 0
    sleep 1
  done
  sleep 3
  if cp -f "$MODDIR/temp/myname.apk" "$MODDIR/mypath/"; then
    rm -rf "$MODDIR/temp"
    killall com.android.systemui
  fi
) &
