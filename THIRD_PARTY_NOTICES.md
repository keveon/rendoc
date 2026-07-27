# 第三方组件与许可证说明

Rendoc 源代码以 MIT License 发布。容器镜像同时包含来自 Debian 与 Maven Central 的第三方组件；镜像标签中的 `org.opencontainers.image.licenses=MIT` 仅描述 Rendoc 自身许可证，不替代第三方组件各自的许可条款。

## 容器运行时

运行镜像基于 Debian 13，并通过 Debian 官方仓库安装 OpenJDK、LibreOffice、fontconfig、curl 与 tini。各 Debian 包的完整版权和许可证文本保留在镜像的 `/usr/share/doc/<package>/copyright` 中。

内置替代字体均来自 Debian 可再分发包：

- `fonts-noto-cjk`：SIL Open Font License 1.1；
- `fonts-liberation2`：SIL Open Font License 1.1；
- `fonts-crosextra-carlito`：SIL Open Font License 1.1；
- `fonts-crosextra-caladea`：SIL Open Font License 1.1。

Rendoc 不包含微软字体或其他从历史内部镜像复制的专有字体。部署者挂载到 `/usr/local/share/fonts/custom` 的字体不属于 Rendoc 分发内容，其授权与合规责任由部署者自行确认。

## Java 依赖

Java 依赖由 Gradle 从 Maven Central 解析，其坐标与传递依赖可通过以下命令审计：

```bash
./gradlew dependencies
```

发布 JAR 在 `META-INF` 中携带 Rendoc 的 `LICENSE` 与本说明，嵌套依赖 JAR 保留各自的 `META-INF` 许可材料；运行镜像另在 `/usr/share/doc/rendoc` 中携带相同文件。GitHub Release 工作流还会为正式镜像生成包含组件元数据的 SBOM 与 provenance attestation。部署者仍应结合自身使用场景审查所有第三方许可证与安全公告。
