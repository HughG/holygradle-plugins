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
   - one for publishing plugin snapshots for testing modifications to plugins without affecting existing plugin users.
   If you prefer you could use just one repository.
 
# Getting started - with a clean checkout
Create 'gradle.properties' in the root of your workspace add define these properties:
```
artifactoryServer=<fully qualified domain name of artifactory server, including port if necessary,
  e.g., http://artifact-server.company.com:8081/artifactory/>
artifactoryPluginRepo=<name of the repository for obtaining dependent plugins, e.g., plugins-release>
artifactoryPluginPublishRepo=<name of the (non-remote, non-virtual) repository for publishing plugins,
  e.g., plugins-release-local>
artifactoryUsername=<username>
artifactoryPassword=<encrypted artifactory password>

```

## Initial Publishing
If the plugins have never been published before within your organisation, run the following commands.

 - `gw -PpublishVersion=1.0.0 publishCustomGradleReally`
 - `gw -PpublishVersion=1.0.0 publishPluginsReally`

You can replace "Really" with "Locally" to publish to a folder called "local_repo", to check the contents before really
publishing, if you want.  See the section on publishing below for more details.

# Testing

Run tests with `gw test` for unit tests, and `gw integTest` for integration tests.  These use JUnit, so you can do the
usual Gradle JUnit tricks, such as running a single test class (which is especially useful for integration tests, as
these can't be run within a normal JUnit test runner).  You can't run just one test method, though.

  - `gw -Dtest.single=SomeIntegrationTest test`
  - `gw -DintegTest.single=SomeIntegrationTest integTest`

## Unit tests
Unit tests (if any) will be automatically run prior to any publish operation, so you can't publish unless the tests
pass. That said, the unit tests are very sparse or non-existent depending on the plugin.

## Integration tests
Integration tests are those whose class names end in `IntegrationTest`, extending `AbstractHolyGradleIntegrationTest`.
They can be run directly using `gw integTest`, and are run before every `publishPluginsReally` task.  They work by
launching a Gradle build which fetches the custom-gradle distribution and the plugins from a `local_repo` directory
created at the top level of the source tree.  These versions are those produced by the `publishCustomGradleLocally` and
`publishPluginsLocally` tasks.

# Publishing the custom-gradle distribution and/or plugins
The custom-gradle distribution is expected to change much less often than the plugins, and there is no Gradle-checked
dependency between them, so they can be published separately.  The plugins can only be published together as one unit.

Each of these can be published as a user-specific snapshot or as a numbered release.  Publishing user-specific snapshots
lets you test a new version of the plugins on a real project, without causing problems for other users.  Use
`gw publishCustomGradleReally` and/or `gw publishPluginsReally` to publish the custom-gradle distribution, and/or the
whole set of plugins.

 - Without any other arguments, the version used will be `<user>-SNAPSHOT`, where `<user>` is your system username (not
your BitBucket username).
 - To publish a release version, pass the version number with `-PpublishVersion=<version number>`.

The main sequence of tasks when publishing is as follows.  This is a summary of the output of
`gw --dry-run publishCustomGradleReally publishPluginsReally`.  The `credential-store` project is published with the
plugins.  The output below is not exactly true, because the `artifactory-manager-plugin` doesn't depend on
`custom-gradle`, so it's done earlier, but it's mostly true.

```
:publishPluginsLocally
  :setHgVersionInfo
  :cleanIntegrationTestState
  :publishCustomGradleLocally
    :custom-gradle:publishCustomGradleLocally
  :<each-plugin>:publishPluginsLocally
  :<each-plugin>:testClasses
  :<each-plugin>:integTest
:publishCustomGradleReally
  :custom-gradle:publishCustomGradleReally
:publishPluginsReally
  :<each-plugin>:publishPluginsReally
```

## Testing changes using snapshots
To avoid annoying people with broken plugins you should test your changes by publishing 'snapshots', as follows.

 - In your global `gradle.properties` which lives in your `GRADLE_USER_HOME` directory, add a line such as:
`systemProp.holygradle.pluginsSnapshotsUser=nm2501`. This will automatically change the requested versions to
`<user>-SNAPSHOT`.  (This switch-over is part of the `holy-gradle-init.gradle` script in the custom-gradle distribution,
rather than being part of Gradle itself.)
 - When you're testing your changes you can tell if it has picked up your new plugin because Gradle will print messages
whenever artifacts are downloaded e.g.
```
Download ..../holygradle/devenv-plugin/nm2501-SNAPSHOT/ivy-nm2501-SNAPSHOT.xml
Download ..../holygradle/devenv-plugin/nm2501-SNAPSHOT/devenv-plugin-nm2501-SNAPSHOT.jar
```
 - You can also run `gw versionInfo` to get a complete list of version numbers.

# Documentation
Some documentation is on the BitBucket wiki, but we're experimenting with building static HTML pages using AsciiDoc, to
provide code syntax highlighting and diagrams using GraphViz.

## Installing Cygwin utilities.

To install AsciiDoc under Cygwin in Windows, follow these steps.

1. Download `setup_x86_64.exe` (or setup_x86.exe for 32-bit) from `http://www.cygwin.com/`.
2. Run the following command line.  This assumes that your company has an HTTP proxy, and that
`cygwin.mirror.uk.sargasso.net` is an appropriate mirror server for you.
```
setup_x86_64.exe -q -p proxy-server.company.com:8080
  -s http://cygwin.mirror.uk.sargasso.net/ -P asciidoc dblatex texlive
```

To install GraphViz for digrams, and Pygments for syntax highlighting, you have to install packages from
`cygwinports.org`, with the following steps.

1. Follow the instructions at `http://cygwinports.org/` to register their public key and add their site to your list of
mirrors.
2. Run `setup_x86_64.exe` again as described above, without the `-P` option, and install the packages "graphviz" and
"python-pygments".  If you get the following error, add the `-X` command line option.
```
Mirror Error:  Setup.ini signature ftp://ftp.cygwinports.org/pub/cygwinports/x86_64/setup.bz2.sig from
ftp://ftp.cygwinports.org/pub/cygwinports/ failed to verify.
Possible corrupt mirror?  Setup.ini rejected.
```

## Building the documentation

Run `gw buildWebsite` to build the website.  You can also run `gw buildWebsiteQuickly` to build without syntax
colouring, which may be noticeably faster.  (The `gw buildWebsite` task always cleans the output first, in case
`gw buildWebsiteQuickly` has been run before.)

Copy the `doc\website\output` contents to a repo which has `https://bitbucket.org/holygradle/holygradle.bitbucket.org`
as master, run `hg addremove`, then commit and push to update the website.

The site is viewable at `http://holygradle.bitbucket.org/`.