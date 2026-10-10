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

Building this project requires JDK 21 and Maven 3.6.3 or later. The extension targets Java 17 bytecode.

```sh
mvn clean verify
```

The `mise.toml` file provides pinned Java and Maven versions and a `build` task (`mise install && mise run build`). GitHub Actions uses the Maven version from this file; `setup-java` selects JDK 21.

## Release

The GitHub Actions release workflow runs the Maven Release Plugin to prepare and publish releases.

## Known limitation

Project settings cannot bootstrap loading core extensions. See [MNG-6392](https://issues.apache.org/jira/browse/MNG-6392).
