#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
JAVA_HOME=$(dirname $(dirname "$SCRIPT_DIR"))/build/linux-x86_64-server-release/images/jdk
PATH=$JAVA_HOME/bin:$PATH

( cd $SCRIPT_DIR && (
    rm -rf customjre
    #jlink -v --seal=*:final=y:sealed=y:excludefile=../excludedclasses.txt:log=info --module-path "%JAVA_HOME%\jmods":modules --add-modules jlinkModule --output customjre --launcher customjrelauncher=jlinkModule/helloworld.HelloWorld
    #jlink -J-Djlink.debug=true -J--module-path=plugins --list-plugins
    #jlink --module-path=plugins --suggest-providers jdk.tools.jlink.plugin.Plugin
    jlink --output out -J--module-path=plugins -p ./testapp.jar --add-modules xxx --dyna-test
))
