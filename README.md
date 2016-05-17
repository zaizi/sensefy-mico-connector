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

## Configuring Connectors with ManifoldCF
---

Copy both connector jars with dependencies to $MANIFOLD_DIR/connectors-lib directory (if you build ManifoldCF using above commands $MANIFOLD_DIR will  be dist/example)

To Configure connectors edit $MANIFOLD_DIR/connectors.xml with the followings
```
<transformationconnector name="MICO Multimedia" class="org.apache.manifoldcf.agents.transformation.mico.multimedia.MicoExtractor" />
<transformationconnector name="MICO Text" class="org.apache.manifoldcf.agents.transformation.mico.text.MicoExtractor" />
```
