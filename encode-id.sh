#!/bin/bash
set -e

random()
{
	LC_ALL=C read -rn"$1" "$2" < /dev/urandom
}

random 3 r1
random 1 r2

echo -n "$r1${1%Manager}$r2" | base64
