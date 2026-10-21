# TLS test fixtures

Test-only certificates for `TlsTest`, shaped like Caddy's `tls internal` chain (root, intermediate, site
certificate for `localhost`). Generated once with the commands below, valid for 100 years, and never
trusted anywhere but in that test. `other-root.pem` is an unrelated CA the test expects to be refused.

```bash
d=36500; ec="-newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes"
openssl req -x509 $ec -keyout root.key -out root.pem -days $d -subj "/CN=Test Local Authority - ECC Root" \
  -addext basicConstraints=critical,CA:TRUE -addext keyUsage=critical,keyCertSign,cRLSign
openssl req $ec -keyout int.key -out int.csr -subj "/CN=Test Local Authority - ECC Intermediate"
printf 'basicConstraints=critical,CA:TRUE,pathlen:0\nkeyUsage=critical,keyCertSign,cRLSign\n' > int.ext
openssl x509 -req -in int.csr -CA root.pem -CAkey root.key -CAcreateserial -out int.pem -days $d -extfile int.ext
openssl req $ec -keyout site.key -out site.csr -subj "/CN=localhost"
printf 'subjectAltName=DNS:localhost\nextendedKeyUsage=serverAuth\n' > site.ext
openssl x509 -req -in site.csr -CA int.pem -CAkey int.key -CAcreateserial -out site.pem -days $d -extfile site.ext
cat site.pem int.pem > chain.pem
openssl pkcs12 -export -inkey site.key -in chain.pem -name site -out server.p12 -passout pass:test
openssl req -x509 $ec -keyout other.key -out other-root.pem -days $d -subj "/CN=Unrelated Root" \
  -addext basicConstraints=critical,CA:TRUE
```
