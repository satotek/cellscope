#!/system/bin/sh
# Fallback mount for KernelSU (Next) 3.x, which delegates system/ mounting to a
# "metamodule". If one is installed and already mounted us, this is a no-op.
# /data (casefold f2fs) can't be an overlayfs lowerdir, so stage into tmpfs first.
MODDIR=${0%/*}
[ -f /system/priv-app/CellScope/CellScope.apk ] && exit 0
T=/dev/cellscope_privapp
mkdir -p "$T" || exit 0
mount -t tmpfs -o mode=0755 tmpfs "$T" || exit 0
cp -a "$MODDIR/system/." "$T/"
chcon -R u:object_r:system_file:s0 "$T"
for d in priv-app etc/permissions; do
    mount -t overlay overlay -o ro,lowerdir="$T/$d:/system/$d" "/system/$d"
    # let KernelSU/susfs hide the mount from denylisted apps, if available
    /data/adb/ksu/bin/ksu_susfs add_try_umount "/system/$d" 1 2>/dev/null
done
/data/adb/ksu/bin/ksu_susfs add_try_umount "$T" 1 2>/dev/null
exit 0
