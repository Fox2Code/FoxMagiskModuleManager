## AnyKernel3 (AK3), and AnyKernel2/AnyKernel 2.0 (AK2) Scripts License:
#
#   AnyKernel (versions 2.0/2 and later) Android image modifying scripts.
#   Copyright (c) 2019 Chris Renshaw (osm0sis @ xda-developers),
#   and additional contributors per readily available commit history/credits.
#   All rights reserved.
#
#   Redistribution and use in source and binary forms, with or without
#   modification, are permitted (subject to the limitations in the disclaimer
#   below) provided that the following conditions are met:
#
#      * Redistributions of source code must retain the above copyright notice,
#        this list of conditions and the following disclaimer.
#
#      * Redistributions in binary form must reproduce the above copyright
#        notice, this list of conditions and the following disclaimer in the
#        documentation and/or other materials provided with the distribution.
#
#      * Neither the name of the copyright holder nor the names of its
#        contributors may be used to endorse or promote products derived from this
#        software without specific prior written permission.
#
#   NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY
#   THIS LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
#   CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
#   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
#   PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
#   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
#   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
#   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
#   BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
#   IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
#   ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
#   POSSIBILITY OF SUCH DAMAGE.
#

if [ -z "$AK3TMPFS" ]; then
  echo "AK3TMPFS is not defined? Are you running FoxMMM?"
  exit 1
fi

if [ ! -e "$AK3TMPFS" ]; then
  mkdir $AK3TMPFS
  chmod 755 $AK3TMPFS
fi

ZIPFILE=$3;

# Mount tmpfs early
mount -t tmpfs -o size=400M,noatime tmpfs $AK3TMPFS;
mount | grep -q " $AK3TMPFS " || exit 1;

unzip -p $Z tools*/busybox > $AK3TMPFS/busybox;
unzip -p $Z META-INF/com/google/android/update-binary > $AK3TMPFS/update-binary;
##

chmod 755 $AK3TMPFS/busybox;
$AK3TMPFS/busybox chmod 755 $AK3TMPFS/update-binary;
$AK3TMPFS/busybox chown root:root $AK3TMPFS/busybox $AK3TMPFS/update-binary;

# work around Android passing the app what is actually a non-absolute path
AK3TMPFS=$($AK3TMPFS/busybox readlink -f $AK3TMPFS);

# AK3 allows the zip to be flashed from anywhere so avoids any need to remount /
if $AK3TMPFS/busybox grep -q AnyKernel3 $AK3TMPFS/update-binary; then
  # work around more restrictive upstream SELinux policies for Magisk <19306
  magiskpolicy --live "allow kernel app_data_file file write" || true;
else
  echo "Module is not an AnyKernel3 module!"
  exit 1
fi;

# update-binary <RECOVERY_API_VERSION> <OUTFD> <ZIPFILE>
ASH_STANDALONE=1 AKHOME=$AK3TMPFS/anykernel $AK3TMPFS/busybox ash $AK3TMPFS/update-binary 3 1 "$Z";
RC=$?;

# Original script delete all generated files,
# But we just need to unmount as we store everything inside tmpfs
umount $AK3TMPFS;

return $RC;