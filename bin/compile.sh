#!/bin/sh

./gradlew compileJava -x spotbugsMain -x spotbugsTest -x spotlessJava
