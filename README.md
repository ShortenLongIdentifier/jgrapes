JGrapes
=======

[![Build Status](https://travis-ci.org/mnlipp/jgrapes.svg?branch=master)](https://travis-ci.org/mnlipp/jgrapes)

| ---- | ----- |
| Core | [![Maven Central](https://img.shields.io/maven-central/v/org.jgrapes/org.jgrapes.core.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22org.jgrapes.core%22)
| Util | [![Maven Central](https://img.shields.io/maven-central/v/org.jgrapes/org.jgrapes.util.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22org.jgrapes.util%22)
| Io   | [![Maven Central](https://img.shields.io/maven-central/v/org.jgrapes/org.jgrapes.io.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22org.jgrapes.io%22)
| Http | [![Maven Central](https://img.shields.io/maven-central/v/org.jgrapes/org.jgrapes.http.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22org.jgrapes.http%22)

See the [project's home page](http://mnlipp.github.io/jgrapes/).

This repository comprises the sources for jars that provide the basic
packages (org.jgrapes.core, ...util, ...io etc.). The jars have augmented
manifests that allows them to be used without wrapping as OSGi bundles, 
but they do not depend in any way on the OSGi framework.
    
The JGrapes OSGi components that do depend on the OSGi framework and 
provide JGrapes based OSGi services can be found in a
[seperate repository](https://github.com/mnlipp/jgrapes-osgi) because
they profit from a different top-level build approach. 
