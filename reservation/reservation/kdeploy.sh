#!/bin/sh
kalix service deploy reservation registry.hub.docker.com/max8github/reservation:"$1" --secret-env INSTALL_TOKEN=tc-creds/INSTALL_TOKEN