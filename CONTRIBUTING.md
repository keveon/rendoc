# 贡献指南

感谢你改进 Rendoc。提交前请先确认改动保持项目的受限转换边界：只接收上传的 DOCX/XLSX 二进制内容，只输出 PDF，不引入远程 URL 抓取、任意服务器路径或未经验证的 LibreOffice 参数。

## 开发环境

- Java 21
- Docker（容器与烟雾测试）
- LibreOffice Writer/Calc（仅真实转换测试需要）

```bash
./gradlew clean check integrationTest bootJar

docker build -t rendoc:local .
scripts/container-smoke-test.sh rendoc:local
```

## Pull Request

- 从最新 `main` 创建干净分支。
- 使用 Conventional Commits，例如 `feat:`、`fix:`、`docs:`、`ci:`。
- 每个 PR 保持单一、可审查的主题；不要把 API、容器、发布流程和大规模重构堆在一起。
- 行为变更先添加失败测试，再实现并完成重构。
- 不提交 `.env`、Token、客户文档、真实文件名、专有字体或本地转换产物。
- 提交前执行 `git diff --check`、单元测试和适用的集成测试。

## 安全与隐私

转换器处理的文档应视为不可信且可能敏感。日志只能包含格式、字节数、耗时和匿名运行状态，不得记录正文、原始文件名、Token 或业务标识。临时文件在所有退出路径都必须清理。

安全漏洞请不要创建公开 Issue，按 [SECURITY.md](SECURITY.md) 使用私密报告渠道。
