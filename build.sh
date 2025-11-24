#!/bin/bash

echo "Building SkillsPlugin..."

mvn clean package

if [ $? -eq 0 ]; then
    echo ""
    echo "Build successful!"
    echo "Plugin JAR: target/SkillsPlugin-1.0.0.jar"
    echo ""
    cp target/SkillsPlugin-1.0.0.jar ../../plugins/
    echo "Plugin copied to server plugins directory!"
else
    echo "Build failed!"
    exit 1
fi
