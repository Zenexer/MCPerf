#!/usr/bin/env sh
set -e

jar=MCPerf.jar
server=root@mc-0.internal.cowcraft.net

cd jar || exit $?

if [ $# -eq 0 ]; then
	set -- dev kitpvp || exit $?
fi

x=0
for i in "$@"; do
	echo "Deploying to $i" >&2
	case "$i" in
		common)
			dest="/mc/$i/plugins/$jar"
			;;

		*)
			dest="/mc/servers/$i/plugins/$jar"
			;;
	esac
	
	rsync "$jar" "$server:$dest" || x=$?
done
exit $x
