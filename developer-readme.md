# Licensing
The GNU GENERAL PUBLIC LICENSE applies to this software. See license.txt.

# Overview
This repository contains:
 - several Gradle plugins
 - two support modules for interacting with the Windows Credential Manager.
 - a redistribution of Gradle, including an init script which facilitates usage of the plugins

# Prerequisites
 - Gradle 1.4, although newer versions will probably work
 - Java JDK 1.7 - you do need the dev-kit, not just the run-time.
 - A configured Artifactory server with two Ivy repositories: one for publishing release quality plugins, and one for publishing plugin snapshots for testing modifications to plugins without affecting existing plugin users. If you prefer you could use just one repository.
 
# Getting started
 - Edit '<root>/gradle.properties' and enter the url of your Artifactory server as well as credentials for connecting to it. 
 - Edit '<root>/custom-gradle/gradle.properties' and enter the name of the Artifactory server.
 - Publish 'custom-gradle':
   - Edit 'holy-gradle-init.gradle'. There's a hard-coded version number for 'custom-gradle-core'. If you have never published 'custom-gradle-core-plugin' before then either change this hard-coded version number, or make sure that when you do publish 'custom-gradle-core-plugin' that you choose the appropriate version number.
   - Run gw publishRelease --no-daemon
 - Publish the 'credential-store' and 'hg-credential-store' modules:
   - Build them in Release.
   - Run gw publishRelease --no-daemon
 - Publish each of the plugins:
   - Note: For the intrepid and my-credentials plugins their build.gradle files specify dependencies on particular versions of the 'credential-store' modules. Make sure that these version numbers make sense for your repository.
   - Run gw publishRelease --no-daemon
   
# Testing changes using snapshots
To avoid annoying people with broken plugins you should test your changes by publishing 'snapshots':

 - Use gw publishSnapshot to publish a plugin with the version number <user>-SNAPSHOT, where <user> is your username. This means that you can do plugin development at the same time as others without conflicts.
 - In your global gradle.properties which lives in your GRADLE_USER_HOME directory, add a line such as: holyGradlePluginsSnapshots=nm2501. This will switch you over to using 'plugins-integration-local' for all plugins and automatically change the requested versions to <user>-SNAPSHOT.
 - Since the holyGradlePluginsSnapshots property affects all holy-gradle plugins you will need to make sure that all of them are published as snapshots in your name. The easiest way to do this is to run 'gw publishSnapshot' on the directory above the individual plugins, so that the task is invoked for all plugins.
 - When you're testing your changes you can tell if it has picked up your new plugin because Gradle will print messages whenever artifacts are downloaded e.g.
Download ..../holygradle/devenv-plugin/nm2501-SNAPSHOT/ivy-nm2501-SNAPSHOT.xml
Download ..../holygradle/devenv-plugin/nm2501-SNAPSHOT/devenv-plugin-nm2501-SNAPSHOT.jar
 - You can also run gw versionInfo to get a complete list of version numbers.
 
# Unit tests
Unit tests (if any) will be automatically run prior to any publish operation, so you can't publish unless the tests pass. That said, the unit tests are very sparse or non-existent depending on the plugin.

Writing unit tests is a trade-off: on one hand there's the cost of writing the unit tests, on the other there's the cost of unwittingly introducing defects. This can be broken down further to: the impact of the defect on consumers of the plugins, the cost of fixing defects and manually verifying the fix. I would prefer to stay very agile, and keep the cost of fixing defects to a minimum. However as the number of plugin developers and consumers grows the balance will quickly shift towards writing unit tests.