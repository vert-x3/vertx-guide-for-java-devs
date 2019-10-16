#!/bin/sh

echo "Keys for HTTPS..."

# tag::https-keygen[]
keytool -genkey \
  -alias test \
  -keyalg RSA \
  -keystore server-keystore.jks \
  -keysize 2048 \
  -validity 360 \
  -dname CN=localhost \
  -keypass secret \
  -storepass secret
# end::https-keygen[]

echo "Done."
