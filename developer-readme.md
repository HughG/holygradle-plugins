# Licensing
The GNU GENERAL PUBLIC LICENSE applies to this software. See [license.txt](license.txt).

# Overview
This repository contains:

 - several Gradle plugins
 - two support modules for interacting with the Windows Credential Manager.
 - a redistribution of Gradle, including an init script which facilitates usage of the plugins

# Prerequisites
 - Java JDK 1.7 - you do need the dev-kit, not just the run-time.
 - JAVA_HOME environment variable pointing to your JDK e.g. JAVA_HOME=C:\Program Files\Java\jdk1.7.0_09
 - An Artifactory server configured with:
   - an Ivy repository for publishing Gradle plugins to. It should support releases and snapshots.
   - a remote repo pointing to Maven Central 
 one for publishing plugin snapshots for testing modifications to plugins without affecting existing plugin users. If you prefer you could use just one repository.
 
# Getting started - with a clean checkout
Create 'gradle.properties' in the root of your workspace add define these properties:
```
artifactoryServer=<fully qualified domain name of artifactory server, including port if necessary> e.g. http://artifact-server.company.com:8081/artifactory/
artifactoryServerName=<fully qualified domain name of artifactory server without any http:// or slashes, including port if necessary> e.g. artifact-server.company.com:8081
artifactoryPluginRepo=<name of the repository for obtaining dependent plugins, e.g. plugins-release>
artifactoryPluginPublishRepo=<name of the (non-remote, non-virtual) repository for publishing plugins e.g. plugins-release-local>
artifactoryUsername=<username>
artifactoryPassword=<encrypted artifactory password>

```

# Getting started - if the plugins have never been published before within your organisation:
 - Run `gw --no-daemon -PpubAs=release publishCustomGradleReally`
 - Run `gw --no-daemon -PpubAs=release publishPluginsReally`

You can replace "Really" with "Locally" to publish to a folder called "local_repo", to check the contents before really
publishing, if you want.

# Testing changes using snapshots
To avoid annoying people with broken plugins you should test your changes by publishing 'snapshots':

 - Use `gw publishSnapshot` to publish a plugin with the version number `<user>-SNAPSHOT`, where <user> is your system username (not your BitBucket username). This means that you can do plugin development at the same time as others without conflicts.
 - In your global gradle.properties which lives in your GRADLE_USER_HOME directory, add a line such as: `holyGradlePluginsSnapshots=nm2501`. This will switch you over to using 'plugins-integration-local' for all plugins and automatically change the requested versions to `<user>-SNAPSHOT`. (This switch-over is part of the publishing logic in the top-level `build.gradle` script, rather than being part of Gradle itself, and doesn't need the `holy-gradle-plugins` to be built already.)
 - Since the `holyGradlePluginsSnapshots` property affects all holy-gradle plugins you will need to make sure that all of them are published as snapshots in your name. The easiest way to do this is to run 'gw publishSnapshot' on the directory above the individual plugins, so that the task is invoked for all plugins.
 - When you're testing your changes you can tell if it has picked up your new plugin because Gradle will print messages whenever artifacts are downloaded e.g.
```
Download ..../holygradle/devenv-plugin/nm2501-SNAPSHOT/ivy-nm2501-SNAPSHOT.xml
Download ..../holygradle/devenv-plugin/nm2501-SNAPSHOT/devenv-plugin-nm2501-SNAPSHOT.jar
```
 - You can also run `gw versionInfo` to get a complete list of version numbers.
 
# Unit tests
Unit tests (if any) will be automatically run prior to any "-PpubAs=release" publish operation, so you can't publish unless the tests pass. That said, the unit tests are very sparse or non-existent depending on the plugin.

Writing unit tests is a trade-off: on one hand there's the cost of writing the unit tests, on the other there's the cost of unwittingly introducing defects. This can be broken down further to: the impact of the defect on consumers of the plugins, the cost of fixing defects and manually verifying the fix. I would prefer to stay very agile, and keep the cost of fixing defects to a minimum. However as the number of plugin developers and consumers grows the balance will quickly shift towards writing unit tests.

# Documentation
Some documentation is on the BitBucket wiki, but we're experimenting with building static HTML pages using AsciiDoc, to provide code syntax highlighting and diagrams using GraphViz.

## Installing Cygwin utilities.

To install AsciiDoc under Cygwin in Windows, follow these steps.

1. Download `setup_x86_64.exe` (or setup_x86.exe for 32-bit) from `http://www.cygwin.com/`.
2. Run the following command line.  This assumes that your company has an HTTP proxy, and that `cygwin.mirror.uk.sargasso.net` is an appropriate mirror server for you.
```
setup_x86_64.exe -q -p proxy-server.company.com:8080 -s http://cygwin.mirror.uk.sargasso.net/ -P asciidoc dblatex texlive
```

To install GraphViz for digrams, and Pygments for syntax highlighting, you have to install packages from `cygwinports.org`, with the following steps.
1. Follow the instructions at `http://cygwinports.org/` to register their public key and add their site to your list of mirrors.
2. Run `setup_x86_64.exe` again as described above, without the `-P` option, and install the packages "graphviz" and "python-pygments".  If you get the following error, add the `-X` command line option.
```
Mirror Error:  Setup.ini signature ftp://ftp.cygwinports.org/pub/cygwinports/x86_64/setup.bz2.sig from ftp://ftp.cygwinports.org/pub/cygwinports/ failed to verify.
Possible corrupt mirror?  Setup.ini rejected.
```

## Building the documentation

Run `gw buildWebsite` to build the website.

Copy the `doc\website\output` contents to a repo which has `https://bitbucket.org/holygradle/holygradle.bitbucket.org` as master, run `hg addremove`, then commit and push to update the website.

The site is viewable at `http://holygradle.bitbucket.org/`.