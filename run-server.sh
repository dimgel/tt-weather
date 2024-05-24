#!/bin/bash
cd `dirname $0`

# Without explicit `compile`, it fails on first run.
# To run server from root POM: https://stackoverflow.com/a/41096754/4247442
mvn -q -am -pl server compile spring-boot:run
