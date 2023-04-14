#!/bin/sh

./gradlew test -x spotbugsMain -x spotbugsTest -x spotlessJava
