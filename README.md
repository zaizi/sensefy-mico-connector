# MICO Transformation Connector for Apache ManifoldCF

## Build ManifoldCF
---

```
git clone https://github.com/apache/manifoldcf.git
cd manifoldcf
git checkout origin/release-2.4-branch
mvn clean install

ant make-core-deps
ant make-deps
ant build
```

## Build MICO Client
---
```
git clone https://github.com/zaizi/mico-client.git
cd mico-client
mvn clean install -DskipTests
```


## Build MCF-MICO Connectors
---
```
git clone https://github.com/zaizi/sensefy-mico-connector.git
cd mcf-mico-multimedia-connector
mvn clean install
cd ../mcf-mico-text-connector
mvn clean install
```
