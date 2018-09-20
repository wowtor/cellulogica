#!/bin/bash

STRINGS=app/src/main/res/values/strings.xml

cat $STRINGS \
| sed '/name="revision"/ {
	a\    <string name="revision">'$(git rev-parse HEAD)'</string>
	d
}' \
>x
mv x $STRINGS
