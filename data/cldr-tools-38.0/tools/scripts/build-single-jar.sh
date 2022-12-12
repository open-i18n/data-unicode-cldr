#!/bin/sh
#
# This replaces cldr.jar with an assembly including dependencies.
#

HERE=$(dirname $0)
set -x
mvn --file=${HERE}/../pom.xml clean && mvn -pl java package assembly:single -DskipTests=true --file=${HERE}/../pom.xml 
cp -v ${HERE}/../java/target/cldr-*-jar-with-dependencies.jar ${HERE}/../java/cldr.jar
