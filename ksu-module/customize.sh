#!/system/bin/sh
# KernelSU/Magisk installer hook. Nothing special: just fix perms.
set_perm_recursive "$MODPATH/system" 0 0 0755 0644
set_perm "$MODPATH/system/priv-app/CellScope/CellScope.apk" 0 0 0644
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
ui_print "- CellScope will be a privileged system app after reboot."
ui_print "- If a user-installed copy exists, remove it first: pm uninstall dev.satotek.cellscope"
