#!/bin/bash
set -xeuo pipefail

SCRIPT_DIR=$(dirname $(readlink -f ${BASH_SOURCE[0]}))

# Modify for your environment. The ACR_NAME is the name of your Azure Container
# Registry, and the SERVICE_PRINCIPAL_NAME can be any unique name within your
# subscription (you can use the default below).
ACR_NAME=oblivium
SERVICE_PRINCIPAL_NAME=oblivium-ci

# Obtain the full registry ID for subsequent command args
ACR_REGISTRY_ID=$($SCRIPT_DIR/az acr show --name $ACR_NAME --query id --output tsv)

# Create the service principal with rights scoped to the registry.
# Default permissions are for docker pull access. Modify the '--role'
# argument value as desired:
# reader:      pull only
# contributor: push and pull
# owner:       push, pull, and assign roles
SP_PASSWD=$($SCRIPT_DIR/az ad sp create-for-rbac --name $SERVICE_PRINCIPAL_NAME --scopes $ACR_REGISTRY_ID --role contributor --query password --output tsv)
SP_APP_ID=$($SCRIPT_DIR/az ad sp show --id http://$SERVICE_PRINCIPAL_NAME --query appId --output tsv)

# Output the service principal's credentials; use these in your services and
# applications to authenticate to the container registry.
echo "Service principal ID: $SP_APP_ID"
echo "Service principal password: $SP_PASSWD"
