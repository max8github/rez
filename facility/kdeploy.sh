#!/bin/sh
kalix service deploy facility registry.hub.docker.com/max8github/facility:"$1" --secret-env INSTALL_TOKEN=tc-creds/INSTALL_TOKEN