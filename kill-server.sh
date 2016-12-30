#!/bin/bash
ps aux | grep "java -Djava.util.logging.config.file=logging.properties -cp target/flexsmc-fresco" | awk '{print $2}' | xargs kill
