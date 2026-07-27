# AGENTS.md

## 项目定位

Rendoc 是自托管的 Office 文档转 PDF API。公开内容使用中性、可复用的产品语言，不引用内部项目、客户、真实文档或部署地址。

## 技术基线

- Java 21
- Spring Boot 4
- Gradle Wrapper
- JODConverter + LibreOffice
- Docker / GitHub Container Registry

## 工程规则

1. 使用测试先行方式修改行为；先验证 RED，再实现 GREEN。
2. 转换接口只接受上传的二进制内容，不实现 URL 抓取。
3. 输入格式必须显式声明并限制为 DOCX/XLSX，输出仅允许 PDF。
4. API 默认要求 Bearer Token；Token 只能来自运行时环境变量或 Secret。
5. 不记录文档内容、Token、文件名或可识别业务信息。
6. 临时文件在成功和失败路径都必须清理。
7. 容器以非 root 用户运行；支持只读根文件系统和受限临时目录。
8. 仓库不分发微软等专有字体；默认使用可再分发字体，并允许部署方挂载自有字体。
9. 每个 PR 保持单一、可审查的主题，禁止把 API、容器、发布流程堆进一个超大提交。
10. 提交必须签名，使用 Conventional Commits；不 amend、不 force push。

## 验证命令

```bash
./gradlew test
./gradlew integrationTest
./gradlew check

docker build -t rendoc:local .
```

提交前还需执行 `git diff --check`、敏感信息扫描和独立代码审查。
