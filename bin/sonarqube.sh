#!/bin/sh

./gradlew sonarqube -Dsonar.branch.name=local-ce -Dsonar.host.url=https://sonarqube.devops.telekom.de -Dsonar.login=5392bed06c65e0bbce329ad625cf8554ce467052
