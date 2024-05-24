#!/bin/bash

# Usage: run-client.sh [city] [day|week]
#    or: run-client.sh [day|week] [city]

ARGS="$@"
cd `dirname $0`

# To run client from root POM: https://andresalmiray.com/multi-module-project-builds-with-maven-and-gradle/
mvn -q -am -pl client compile exec:java -Dexec.args="$ARGS"
