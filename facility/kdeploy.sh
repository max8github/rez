#!/bin/sh
kalix service deploy facility registry.hub.docker.com/max8github/facility:"$1" --secret-env INSTALL_TOKEN=tw-creds/INSTALL_TOKEN