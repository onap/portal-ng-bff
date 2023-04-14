#!/bin/sh

./gradlew -p openapi clean compileJava -x spotbugsMain -x spotbugsTest -x spotlessJava
