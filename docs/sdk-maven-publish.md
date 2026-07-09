# 从 GitHub Packages 解析 FlowFoundry SDK

在独立 App 仓库的 `pom.xml` 中增加：

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/Tinet-AaronAn/flowfoundry</url>
  </repository>
</repositories>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.tinet.flowfoundry</groupId>
      <artifactId>flowfoundry-sdk-bom</artifactId>
      <version>1.0.3</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

`~/.m2/settings.xml` 需配置 GitHub Packages 认证（`server` id 与 repository id 均为 `github`）：

```xml
<server>
  <id>github</id>
  <username>${env.GITHUB_ACTOR}</username>
  <password>${env.GITHUB_TOKEN}</password>
</server>
```

发布：推送 tag `v*` 触发 `.github/workflows/publish-sdk.yml`。
