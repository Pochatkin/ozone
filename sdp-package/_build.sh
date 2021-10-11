#!/bin/bash

builder=${BUILD_IMG:-sdp/bundle-ozone:v0.1.0}

docker run -it --rm -v $(pwd):/repo -v $(realpath ~/.m2):/root/.m2 -w /repo $builder bash -c "mvn install:install-file -DgroupId=com.google.protobuf -DartifactId=protoc -Dversion=2.5.0 -Dclassifier=linux-aarch_64 -Dpackaging=exe -Dfile=/usr/bin/protoc && mvn package -DskipTests"
