#!/usr/bin/env sh
set -e

dir=jar/final
jar=MCPerf.jar
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
			dest="/mc/$i/plugins/$jar"
			;;

		dev|prod)
			dest="/repo/$i/$jar"
			;;

		*)
			dest="/mc/servers/$i/plugins/$jar"
			;;
	esac
	
	rsync "$jar" "$server:$dest" || x=$?
done
exit $x
