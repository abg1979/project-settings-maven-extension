# project-settings-extension

Fork of https://github.com/gzm55/project-settings-maven-extension.

It allows the project specific use of the following tags in `settings.xml` (which the original version denies): 
                                                                          
```xml
<settings>
  <interactiveMode>false</interactiveMode>
  <localRepository>${env.HOME}/clients/abc/.mvn_repo</localRepository>
</settings>
```

Hopefully soon avail. in maven repo central (request pending) under:
```xml
<dependency>
  <groupId>io.github.blackappsolutions</groupId>
  <artifactId>project-settings-extension</artifactId>
  <version>0.2.10</version>
</dependency>
```
