This repository contains:
 - several Gradle plugins
 - two support modules for interacting with the Windows Credential Manager.
 - a redistribution of Gradle, including an init script which facilitates usage of the plugins

Prerequisites:
 - Gradle 1.4, although newer versions will probably work
 - Java JDK 1.7 - you do need the dev-kit, not just the run-time.
 - A configured Artifactory server with two Ivy repositories: one for publishing release quality plugins, and one for publishing plugin snapshots for testing modifications to plugins without affecting existing plugin users. If you prefer you could use just one repository.
 
Getting started:
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