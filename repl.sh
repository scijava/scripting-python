#!/bin/sh
set -e
cd "$(dirname "$0")"
python -c '
import os.path
import xml.etree.ElementTree as ET
import scyjava
import subprocess
from pathlib import Path

def exec(cmd):
    try:
        args = cmd.split(" ")
        subprocess.check_output(cmd.split(" "))
    except subprocess.CalledProcessError as e:
        print("== OPERATION FAILED ==")
        print(e.stdout.decode())
        print(e.stderr.decode())
        raise e

pom = ET.parse("pom.xml")
artifactId = pom.find("{http://maven.apache.org/POM/4.0.0}artifactId").text
version = pom.find("{http://maven.apache.org/POM/4.0.0}version").text
jar = Path(f"target/{artifactId}-{version}.jar")
if not jar.exists():
    print("Building JAR file...")
    exec("mvn -Denforcer.skip -Dmaven.test.skip clean package")
deps_dir = Path("target/dependency")
if not deps_dir.exists():
    print("Copying dependencies...")
    exec("mvn -DincludeScope=runtime dependency:copy-dependencies")

scyjava.config.add_option("-Djava.awt.headless=true")
scyjava.config.add_classpath(jar.absolute())
deps = scyjava.config.find_jars(deps_dir.absolute())
scyjava.config.add_classpath(*deps)
Context = scyjava.jimport("org.scijava.Context")
context = Context(False)
print(f"Context created with {context.getServiceIndex().size()} services.")
scyjava.enable_python_scripting(context)
ScriptREPL = scyjava.jimport("org.scijava.script.ScriptREPL")
repl = ScriptREPL(context, "Python")
repl.loop()
context.dispose()
'
