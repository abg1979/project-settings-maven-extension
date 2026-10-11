# Project Settings Maven Extension

This Maven core extension loads project-specific settings from `.mvn/settings.xml` and merges them with Maven's user or global settings. Maven does not load `.mvn/settings.xml` by itself.

Project settings are useful when a project needs custom repositories, mirrors, or other Maven settings. They let contributors build the project without manually modifying their personal `settings.xml`.

## Enable the extension

Create `.mvn/extensions.xml` at the top level of the project:

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>io.github.abg1979.maven</groupId>
    <artifactId>project-settings-extension</artifactId>
    <version>0.3.1</version>
  </extension>
</extensions>
```

Then place project settings in `.mvn/settings.xml` at the same project root. For multi-module builds, the extension uses the Maven multi-module project directory.

Because Maven loads core extensions before it reads project settings, `.mvn/settings.xml` cannot configure a repository needed to download this extension itself. Configure that repository through Maven's existing user or global settings.

## Settings merge behavior

The project settings have the highest priority, followed by user settings and then global settings. Some values remain controlled by the user's local configuration:

- `localRepository` uses the project value when set; otherwise, it falls back to the user or global value.
- `interactiveMode`, `offline`, and `usePluginRegistry` come from user or global settings.
- Project proxies are ignored in favor of user or global proxies.
- For matching server IDs, credentials and other local-only fields come from user or global settings.

## Properties

- `skipProjectSettings`: set to `true` to skip loading project settings, for example `mvn -DskipProjectSettings=true verify`.
- `projectSettings.dumpMergedSettings`: set to `true` to write the merged settings to `.mvn/merged-settings.xml`, for example `mvn -DprojectSettings.dumpMergedSettings=true verify`. The file may contain credentials; do not commit it.

## Build

Building this project requires JDK 21 and Maven 3.9.2 or later. The extension targets Java 17 bytecode.

```sh
mvn clean verify
```

The `mise.toml` file provides pinned Java and Maven versions and a `build` task (`mise install && mise run build`). GitHub Actions uses the Maven version from this file; `setup-java` selects JDK 21.

## Release

Releases are published to the Central Publisher Portal by the `central-publishing-maven-plugin`. Publishing is disabled by default, so a normal `mvn deploy` does not upload snapshots. Manual dispatch of the **Publish package to Maven Central** workflow from `master` is the only release path. It derives the release version from the current `-SNAPSHOT` POM, creates the Maven Release Plugin commit and tag, signs the artifacts, and automatically publishes them to Maven Central after Portal validation. Select an unused release version; the workflow does not check for existing versions before starting.

Configure these GitHub Actions repository secrets:

- `OSSRH_USERNAME` and `OSSRH_TOKEN`: use these existing secret names for the username and password from a generated Central Publisher Portal user token. Despite their legacy names, the values must be Portal user-token credentials, not retired OSSRH credentials. The namespace `io.github.abg1979.maven` must be verified, and snapshots do not need to be enabled because this project does not publish snapshots.
- `GPG_KEY` and `GPG_PASS_PHRASE`: the signing key and its passphrase.
- `DEPLOY_SSH_PRIVATE_KEY` and `DEPLOY_KNOWN_HOSTS`: SSH access for Maven Release Plugin commits and tags.

To test the Portal credentials without preparing a release or uploading artifacts, manually run the **Validate Central Publisher credentials** workflow from `master`. It makes a read-only request and reports only the HTTP status. A successful response confirms API authentication, but not publication permission for the project's namespace. The release workflow uses the existing OSSRH-named secrets and does not create GitHub Release objects.

Maven Central release versions are immutable. If `release:prepare` has pushed a tag but `release:perform` fails, the workflow does not retry that tag; leave it untouched and use the next available development version. The failed `0.3.2`, `0.3.3`, and `0.3.4` tags remain unpublished; the current release line starts at `0.3.5-SNAPSHOT`.

The verification workflow signs artifacts as well. GitHub withholds repository secrets from fork pull requests, so those runs may fail during GPG setup or signing.

## Known limitation

Project settings cannot bootstrap loading core extensions. See [MNG-6392](https://issues.apache.org/jira/browse/MNG-6392).
