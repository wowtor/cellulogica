#!/bin/bash

STRINGS=app/src/main/res/values/version_info.xml

echo '<resources>
    <string name="revision">080f789ef8c83af8b6b2bb23d5ba2b7f6623fbe7</string>
</resources>
' >$STRINGS
