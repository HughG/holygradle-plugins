# Licensing
The GNU GENERAL PUBLIC LICENSE applies to this software. See [license.txt](license.txt).

# Overview
This repository contains:

 - several Gradle plugins
 - two support modules for interacting with the Windows Credential Manager.
 - a redistribution of Gradle, including an init script which facilitates usage of the plugins

# Prerequisites
 - Java JDK 1.8 - you do need the dev-kit, not just the run-time.
 - IntelliJ IDEA - optional but very useful.  Currently the source is developed with version 2017.1.
 - JAVA_HOME environment variable pointing to your JDK e.g. JAVA_HOME=C:\Program Files\Java\jdk1.8.0_141
   - In IntelliJ IDEA, you may need to configure this for the project, in the "File > Project Structure..." dialog under
   "SDKs".  BUT, see the section below about 'gradle.properties', before attempting to open the project in IDEA.
    - If you normally need JAVA_HOME to point to another version of Java such as JDK 1.8, you can set
    HOLY_GRADLE_JAVA_HOME to your JDK 1.8 location, and the "gradlew.bat" in this repo will use that instead.
 - You will also need to set a GRADLE_USER_HOME property in IntelliJ.  
   - It will tell you about this in the "Event Log" window. You can open the "Event Log" window by clicking on the 
    icon in the bottom right corner of the window. The event log will show a message like the following.
    ```
    "Load error: undefined path variables GRADLE_USER_HOME is undefined. __Fix it__
    Path variables are used to substitute absolute paths in IDEA project files and allow project file sharing in
    version control systems. Some of the files describing the current project settings contain unknown path
    variables and IDEA cannot restore those paths. (__show balloon__)".
    ```
    Click on the link on __Fix it__ and set it to the value you normally use in your Windows environment variable 
    (or the default, "%USERPROFILE%\.gradle").  After a short wait, IntelliJ will refresh.
   - You can also do this by opening the Path Varible settings. 
    For this open File > Settings > Appearance & Behavior > Path Variables here you should add a setting for
    GRADLE_USER_HOME with the path to your gradle cache. 
 - You may also need to configure IntelliJ to point to your installed JDK, in the "Project Structure" dialog.
 - Optionally, an Artifactory server configured with:
   - an Ivy repository for publishing Gradle plugins to. It should support releases and snapshots.
   - a remote repo pointing to Maven Central 
   - one for publishing plugin snapshots for testing modifications to plugins without affecting existing plugin users.
   If you prefer you could use just one repository.
 
# Getting started with a clean checkout

## Banned words list

For confidentiality reasons, the 'build.gradle' will automatically add a pretxncommit hook to your clone of this repo,
which checks that commits don't contain any words in a "banned" list, which should exist at
'local\holy-gradle-plugins-banned-words.txt'.  You must create a directory junction or symlink from 'local' to a folder
of your choice, containing a file with that name.

## Creating gradle.properties
Create 'gradle.properties' in the root of your workspace add define these properties:
```
artifactoryServer=<fully qualified domain name of artifactory server, including port if necessary,
  e.g., http://artifact-server.company.com:8081/artifactory/>
artifactoryPluginRepo=<name of the repository for obtaining dependent plugins, e.g., plugins-release>
artifactoryPluginReleasePublishRepo=<name of the (non-remote, non-virtual) repository for publishing release versions,
  e.g., plugins-release-local>
artifactoryPluginSnapshotPublishRepo=<name of the (non-remote, non-virtual) repository for publishing snapshot versions,
  e.g., plugins-integration-local>
artifactoryUsername=<username>
artifactoryPassword=<encrypted artifactory password>
```

If you don't want to use an Artifactory server, don't define the `artifactoryServer` variable.

Run 'gradlew tasks'.  You may need to supply proxy arguments if it's the first time you've run this version of Gradle
(e.g., 'gradlew -Dhttp.proxyHost=proxyserver -Dhttp.proxyPort=8080').

Note that IntelliJ IDEA will fail to open the project until you've created this file (because it parses the
"build.gradle" files, and some of them depend on these properties).

Note that the projects in the 'wrapper-starter-kit' and 'https_setup_kit' subdirectories will create a hard
link in their own folders to the root gradle.properties file when run, to make sure all projects stay in sync.
They need to be separate Gradle projects because they _use_ the published plugins.  A hard link is used
because symlinks / soft links in Windows require special permissions in some cases.

## IntelliJ IDEA

Before opening the project in IntelliJ, make sure you have GRADLE_USER_HOME set as desired,
and then run `gradlew tasks` in this folder, to make sure the base Gradle distribution is
downloaded to your cache, so you can point IntelliJ to it.

If you open the project with IntelliJ IDEA, it will complain that GRADLE_USER_HOME isn't set.
You need to set it to the same value as your GRADLE_USER_HOME environment variable.

## Initial Publishing
If the plugins have never been published before within your organisation, run the following commands.

 - `gradlew -PpublishVersion=1.0.0 publishCustomGradleReally`
 - `gradlew -PpublishVersion=1.0.0 publishPluginsReally`
 - `cd wrapper-starter-kit`
 - `set WRAPPER_STARTER_KIT_VERSION=1.0.0`
 - `gradlew -PwrapperVersion=1.0.0 publish`

You can replace "Really" with "Locally" to publish to a folder called "local_repo", to check the contents before really
publishing, if you want.  See the section on publishing below for more details.

# Testing

> NOTE: On Windows 8 and above, up till Windows 10 2017 Fall Creators Update (with Developer Mode enabled), the
> SymlinkTest subclass needs to be run with Administrator privileges if the current user is a member of the
> Administrators group.  If you run these tests from a non-admin command prompt, you'll get an exception like the
> following.
> 
>     java.nio.file.FileSystemException: <filename>: A required privilege is not held by the client
> 
> If this error occurs even though you are running in an admin command prompt, it may be because your build is re-using
> a compatible Gradle daemon which was started from a non-admin process.  Please run `gradlew --stop` then try again.
>  
> We may remove symlink support, and hence this test, once directory junction creation has been fully tested.

Run tests with `gradlew test` for unit tests, and `gradlew integTest` for integration tests.  These use JUnit, so you can do the
usual Gradle JUnit tricks, such as running a single test class (which is especially useful for integration tests, as
these can't be run within a normal JUnit test runner).  You can't run just one test method, though.  You need to include
the subproject in the task name, or Gradle will try to run the same test in all subprojects, whereas it probably only 
exists in one of them.

  - `gradlew subproject:test -Dtest.single=SomeTest`
  - `gradlew subproject:integTest -DintegTest.single=SomeIntegrationTest`

## Unit tests
Unit tests (if any) will be automatically run prior to any publish operation, so you can't publish unless the tests
pass. That said, the unit tests are very sparse or non-existent depending on the plugin.

## Integration tests
Integration tests are those whose class names end in `IntegrationTest`, extending `AbstractHolyGradleIntegrationTest`.
They can be run directly using `gradlew integTest`, and are run before every `publishPluginsReally` task.  They work by
launching a Gradle build which fetches the custom-gradle distribution and the plugins from a `local_repo` directory
created at the top level of the source tree.  These versions are those produced by the `publishCustomGradleLocally` and
`publishPluginsLocally` tasks.

# Publishing the custom-gradle distribution and/or plugins
The custom-gradle distribution is expected to change much less often than the plugins, and there is no Gradle-checked
dependency between them, so they can be published separately.  The plugins can only be published together as one unit.

Each of these can be published as a user-specific snapshot or as a numbered release.  Publishing user-specific snapshots
lets you test a new version of the plugins on a real project, without causing problems for other users.  Use
`gradlew publishCustomGradleReally` and/or `gradlew publishPluginsReally` to publish the custom-gradle distribution, and/or the
whole set of plugins.

 - Without any other arguments, the version used will be `<user>SNAPSHOT-0`, where `<user>` is your system username (not
your BitBucket username).
 - To publish a release version, pass the version number with `-PpublishVersion=<version number>`.
 - To skip unit testing (for example, when repeatedly using `-DintegTest.single`), pass `-PnoTest`.  This only works for
SNAPSHOT releases.
 - To skip integration testing, pass `-PnoIntegTest`.  This only works for SNAPSHOT releases.

You can't publish a release version unless the working copy has no uncommitted changes, and the
parent of the working copy has been pushed (i.e., is in the "public" phase).

The main sequence of tasks when publishing is as follows.  This is a summary of the output of
`gradlew --dry-run publishCustomGradleReally publishPluginsReally`.  The `credential-store` project is published with the
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
`<user>SNAPSHOT-0`.  (This switch-over is part of the `holy-gradle-init.gradle` script in the custom-gradle distribution,
rather than being part of Gradle itself.)
 - When you're testing your changes you can tell if it has picked up your new plugin because Gradle will print messages
whenever artifacts are downloaded e.g.
```
Download ..../holygradle/devenv-plugin/nm2501SNAPSHOT/ivy-nm2501SNAPSHOT-0.xml
Download ..../holygradle/devenv-plugin/nm2501SNAPSHOT/devenv-plugin-nm2501SNAPSHOT-0.jar
```
 - You can also run `gradlew versionInfo` to get a complete list of version numbers.

# Diagnosing suspected plugin problems found in gradle scripts 

## clone/update the plugins
## if necessary, follow the "getting started" steps detailed above
## add your println/log entries to the plugins for helping to diagnosing the problem
## use "gradlew -PnoIntegTest pubPR" to build.  This by-passes integration tests (which would fail with your added printlns).  This will default to publishing <user>SNAPSHOT-0
## now to force the user's build script to use the snapshots you've just published, set the property "systemProp.holygradle.pluginsSnapshotsUser=<user>" in your %GRADLE_USER_HOME%/gradle.properties file.
## before fixing, try to add a new integration test that replicates the issue, to help verify the fix and prevent future regression

# Kotlin Development Notes

Gradle requires `Task` subclasses to be open, but Kotlin creates final classes by default, so we have to
mark them all open explicitly.  See https://github.com/gradle/kotlin-dsl/issues/731 which describes this
and https://github.com/gradle/kotlin-dsl/issues/390 which describes why we can't just use the
`kotlin-allopen` plugin yet.

# Documentation
Documentation was previously in the wiki for this repo but has moved to
http://holygradle.bitbucket.org/, using AsciiDoc to allow for richer diagrams, linking, etc.

If you're using the Holy Gradle within a company, there may be a custom local build of those web
pages available.

## Installing Cygwin utilities.

To install AsciiDoc under Cygwin in Windows, follow these steps.

1. Download `setup-x86_64.exe` (or setup_x86.exe for 32-bit) from `http://www.cygwin.com/`.
2. Run the following command line.  This assumes that your company has an HTTP proxy, and that
`cygwin.mirror.uk.sargasso.net` is an appropriate mirror server for you.
```
setup-x86_64.exe -q -p proxy-server.company.com:8080
  -s http://cygwin.mirror.uk.sargasso.net/ -P asciidoc dblatex texlive
```

To install GraphViz for diagrams, and Pygments for syntax highlighting, you have to install packages from
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

To publish the public documentation, follow these steps.

1. Set `publicWebsitePublishUrl` in your `gradle.properties` to a file URL which contains a repo
with `https://bitbucket.org/holygradle/holygradle.bitbucket.org` as master.
2. Run `gradlew buildPublicWebsite`.  (You can also run with `-Pquickly` to build without syntax
colouring, which may be noticeably faster.)
3. Run `hg addremove`, then commit and push to update the website.  The site is viewable at
`http://holygradle.bitbucket.org/`.

You can also build a custom local version for your own organisation, containing specialised or
confidential information.  For that you need to set `publicWebsitePublishUrl` and run
`gradlew buildLocalWebsite` to build the website.  If you want to publish it to IIS, a simple approach
is to publish to a `file:` URL which is a Windows share on the IIS server.  You will also need to
set up extra MIME-type mappings for the example source code (e.g., `.gradle` as `text/plain`).

The `Local` version will include specific site-local, private files from `doc\website\local`.  You
can conditionally include files from there by putting lines like the following in the AsciiDoc.

```
include::{localDoc}/secret.ascinc[]
```

The buildLocal... tasks will define the `localDoc` attribute to point to the `local` subfolder.  If the attribute is not
defined, AsciiDoc silently skips the file.  Both tasks only include the files "*.ascidoc" directly within the
"doc/website" folder, not subfolders, due to restrictions on how AsciiDoc works.  See `website.gradle` for details. 
