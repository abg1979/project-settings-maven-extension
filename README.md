# project-settings-extension

This maven extension could load .mvn/settings.xml as project settings, and merge it into effective setting.

Maven originally only takes two configurations, user (`${user.home}/.m2/settings.xml`) and global (`${maven.home}/conf/settings.xml`). When we developing multiply projects from multiply organizations, the configurations may conflict, and are hard to manage (edit manually or some switch scripts). Another problem is when using some corp maven repositories or mirros for bootstraping the team common parent poms or plugins, the developers have to do some setup actions. But the projects should be `./mvnw verify`-ed immediately after the code is fully checked out.

Since Maven 3.3.1, we have another way to cooperate with maven, that is core extensions defined in `${project.basedir}/.mvn/extensions.xml`. Put this extension in this new config file, then when building the maven will load the project level settings from `${project.basedir}/.mvn/settings.xml`, and you can easily solve the above ploblems.

## Example

```xml
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>com.github.abg1979.maven</groupId>
    <artifactId>project-settings-extension</artifactId>
    <version>0.2.3</version>
  </extension>
</extensions>
```

## Merge Order

* project level `${maven.multiModuleProjectDirectory}/.mvn/settings.xml` (Highest priority)
* user level `${user.home}/.m2/settings.xml` (Medium priority)
* global level `${maven.home}/conf/settings.xml` (Low priority)

This order is widely used in many projects such as git.

## Special fields in projects settings

Some fields should be controlled only by the user, not any project, so they are always ignored and copied back from user or global level settings:

* interactiveMode
* offline
* usePluginRegistry
* proxies

## Property switch

- `skipProjectSettings`, a boolean property to skip loading the project settings.
- `projectSettings.dumpMergedSettings`, a boolean property that writes merged settings to `.mvn/merged-settings.xml` when set to `true`. The dump may contain credentials; do not commit it.

## Release Flow

* `./mvnw install`, pass all ut/it, no warnings
* `./mvnw clean release:clean release:prepare`
* `git pull`
* `./mvnw clean release:clean`

## Known issue

Project settings can not bootstrap loading core extensions. See [MNG-6392](https://issues.apache.org/jira/browse/MNG-6392).
