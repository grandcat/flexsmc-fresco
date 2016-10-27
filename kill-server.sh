#!/bin/bash
ps aux | grep "java -cp target/flexsmc-fresco" | awk '{print $2}' | xargs kill
