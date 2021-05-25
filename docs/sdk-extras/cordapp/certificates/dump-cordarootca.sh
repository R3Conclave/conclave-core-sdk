#!/usr/bin/env bash
keytool -rfc -export -keystore truststore.jks -alias cordarootca -storepass trustpass
