#!/usr/bin/env sh
set -e

dir=jar/final
from_jar=MCPerf.jar
to_jar=MCPerf.jar
server=root@mc-0.internal.cowcraft.net

cd "$dir" || exit $?

if [ $# -eq 0 ]; then
	set -- dev || exit $?
fi

x=0
for i in "$@"; do
	echo "Deploying to $i" >&2
	case "$i" in
		common)
			dest="/mc/$i/plugins/$to_jar"
			;;

		dev|prod)
			dest="/repo/$i/$to_jar"
			;;

		*)
			dest="/mc/servers/$i/plugins/$to_jar"
			;;
	esac
	
	rsync "$from_jar" "$server:$dest" || x=$?
done
exit $x
