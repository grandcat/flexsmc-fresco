#!/bin/bash
java -Djava.util.logging.config.file="logging.properties" -cp target/flexsmc-fresco-0.0.1-SNAPSHOT-jar-with-dependencies.jar de.tum.flexsmc.smc.CLIMain -c "unix:///tmp/grpc-flexsmc.sock"

