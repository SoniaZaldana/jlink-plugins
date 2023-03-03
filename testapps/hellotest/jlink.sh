#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
JAVA_HOME=$(dirname $(dirname "$SCRIPT_DIR"))/build/linux-x86_64-server-release/images/jdk
PATH=$JAVA_HOME/bin:$PATH

( cd $SCRIPT_DIR && (
    jdeps --module-path out -s --module jlinkModule
    rm -rf customjre
    jlink -v --seal=*:final=y:sealed=y:excludefile=../excludedclasses.txt:log=info --module-path "%JAVA_HOME%\jmods":out --add-modules jlinkModule --output customjre --launcher customjrelauncher=jlinkModule/helloworld.HelloWorld
))
