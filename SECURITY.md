# 安全策略

## 支持范围

Rendoc 只为最新正式版本提供安全修复。部署方应持续更新镜像、LibreOffice 运行时和宿主机安全补丁。

## 私密报告漏洞

请使用 GitHub 仓库的 **Security → Report a vulnerability** 私密提交安全报告。不要在公开 Issue、Pull Request、日志或示例文档中披露漏洞利用细节、Token 或真实文件内容。

报告中请尽量包含：

- 受影响版本或镜像摘要；
- 最小化、脱敏的复现步骤；
- 预期影响与攻击前提；
- 建议的缓解方式（如有）。

维护者确认问题前不会要求你上传真实业务文档。

## 部署责任边界

- 将服务放在反向代理、私有网络或明确的访问控制之后。
- 使用高强度随机 Token，并通过 Secret/环境变量注入；不要写入镜像、Compose 文件或 Git。
- 只有 `/livez` 与 `/readyz` 公开；Actuator 管理端点仍要求 Bearer Token，并应通过网络策略进一步限制访问。
- `v*` Release tag 必须由 GitHub Ruleset 禁止更新和删除；迁移仓库时必须保留等价保护，发布工作流以此保证构建期间 tag 不会移动。
- 容器建议启用只读根文件系统、`tmpfs`、`cap_drop: [ALL]` 与 `no-new-privileges`。
- 仓库和公开镜像不提供专有字体；挂载额外字体前请确认授权。
