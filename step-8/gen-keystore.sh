#!/bin/sh

echo "Keys for HTTPS..."

keytool -genkey -alias test -keyalg RSA -keystore server-keystore.jks -keysize 2048 -validity 360 -dname CN=localhost -keypass secret -storepass secret

echo "Done."
