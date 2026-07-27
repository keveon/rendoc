# Rendoc

> 自托管的 Office 文档转 PDF API，面向服务间调用与私有部署。

Rendoc 将应用服务、LibreOffice 运行时和容器发布流程维护在同一个仓库中，提供受限、可观测的 DOCX/XLSX 转 PDF 能力。它不抓取远程 URL，不接收服务器文件路径，也不在日志中记录文档内容或原始文件名。

## 能力边界

| 输入 | `Content-Type` | 输出 |
| --- | --- | --- |
| DOCX | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | PDF |
| XLSX | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | PDF |

也可以使用 `application/octet-stream` 上传上述两类文件，但仍须通过 `source` 显式声明输入格式。当前不支持 DOC、XLS、PPT、远程 URL 或任意输出格式。

默认安全边界：

- `Authorization: Bearer <token>` 中的 Token 为 16–512 个非空白字符，且必须由运行时环境提供。
- 单个请求最多 20 MiB；`Content-Length` 与 chunked 请求都会执行同一上限。
- 默认最多同时执行 2 个转换，容量占满时立即返回 `429`。
- 每次请求使用独立临时目录，成功、失败和响应传输结束后都会清理。
- 容器以 UID/GID `10001` 运行，可配合只读根文件系统、`tmpfs`、能力移除和 `no-new-privileges`。

## 快速开始

```bash
cp .env.example .env
# 编辑 .env，设置一个随机且足够长的 RENDOC_API_TOKEN
docker compose up --build -d
```

转换 DOCX：

```bash
curl --fail --show-error \
  -X POST 'http://127.0.0.1:8080/api/v1/convert?source=docx&target=pdf' \
  -H "Authorization: Bearer ${RENDOC_API_TOKEN}" \
  -H 'Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document' \
  --data-binary @document.docx \
  --output document.pdf
```

转换 XLSX 时将 `source` 改为 `xlsx`，并使用对应的 SpreadsheetML `Content-Type`。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `RENDOC_API_TOKEN` | 无 | 必填；API Bearer Token，至少 16 个字符 |
| `RENDOC_MAX_INPUT_BYTES` | `20971520` | 单次输入上限；范围为 1 B–100 MiB |
| `RENDOC_MAX_IN_FLIGHT` | `2` | 同时执行的转换数，允许范围 1–32 |
| `RENDOC_WORK_DIR` | `/var/lib/rendoc/work` | 请求级临时目录 |
| `RENDOC_LIBREOFFICE_HOME` | 自动探测 | LibreOffice 安装目录；容器内通常为 `/usr/lib/libreoffice` |
| `RENDOC_LIBREOFFICE_WORK_DIR` | `/var/lib/rendoc/libreoffice` | LibreOffice/JODConverter 工作目录 |
| `RENDOC_LIBREOFFICE_PORT` | `2002` | LibreOffice 本地监听端口 |
| `RENDOC_MAX_TASKS_PER_PROCESS` | `20` | 单个 LibreOffice 进程处理任务数上限 |
| `RENDOC_PROCESS_TIMEOUT` | `120s` | LibreOffice 进程启动/停止超时 |
| `RENDOC_TASK_QUEUE_TIMEOUT` | `30s` | 转换排队超时 |
| `RENDOC_TASK_EXECUTION_TIMEOUT` | `120s` | 单次转换执行超时 |
| `RENDOC_PORT` | `8080` | Compose 暴露到 `127.0.0.1` 的宿主机端口 |
| `RENDOC_IMAGE` | `ghcr.io/keveon/rendoc:latest` | Compose 镜像引用；生产部署建议固定 GHCR 正式版本或摘要 |

## 字体

项目镜像只安装可再分发字体：Noto CJK、Liberation、Carlito 与 Caladea。仓库和镜像不分发微软字体或其他专有字体。

如部署方拥有额外字体授权，可只读挂载到容器：

```yaml
services:
  rendoc:
    volumes:
      - ./fonts:/usr/local/share/fonts/custom:ro
```

容器启动时会刷新 Fontconfig 缓存。文档排版仍取决于字体可用性、LibreOffice 版本及文档本身。

## 健康检查与监控

- `GET /livez`：应用进程存活探针。
- `GET /readyz`：应用与 LibreOffice 就绪探针。
- `GET /actuator/health`：聚合健康状态，不展示内部细节；需要 Bearer Token。
- `GET /actuator/prometheus`：Prometheus 指标；需要 Bearer Token。

只有 `/livez` 与 `/readyz` 公开，其余路由默认鉴权。生产环境还应通过反向代理或网络策略限制管理端点访问。

## 错误契约

API 错误使用 `application/problem+json`，并提供稳定的 `code` 字段。常见状态包括：

| HTTP | `code` | 含义 |
| --- | --- | --- |
| `401` | `authentication_required` | Token 缺失或无效 |
| `413` | `payload_too_large` | 输入超过上限 |
| `415` | `unsupported_format` / `unsupported_media_type` | 格式或媒体类型不受支持 |
| `422` | `invalid_document` | 文档为空、损坏或未生成有效 PDF |
| `429` | `capacity_exceeded` | 转换容量已满，可按 `Retry-After` 重试 |
| `503` | `renderer_unavailable` | LibreOffice 暂不可用 |
| `504` | `conversion_timeout` | 排队或转换超时 |

## 发布

GitHub Release 将正式镜像发布到 `ghcr.io/keveon/rendoc`。Release tag 必须使用 `v` 前缀的 SemVer（如 `v1.2.3`）；镜像生成 `1.2.3`、`1.2`、`1` 和稳定版 `latest` 等标签。预发布版本只生成完整版本标签，`0.x` 稳定版本不生成 major 标签。普通 commit 和 Pull Request 只运行检查及容器构建，不发布镜像，也不会自动改写项目版本。

Release workflow 面向 `linux/amd64`、`linux/arm64` 构建。

## 开发与验证

本地需要 Java 21。单元测试不依赖 LibreOffice：

```bash
./gradlew clean test
```

真实转换测试需要本机已安装 LibreOffice Writer 与 Calc：

```bash
./gradlew integrationTest
```

完整检查及容器验证：

```bash
./gradlew clean check integrationTest bootJar
docker build -t rendoc:local .
scripts/container-smoke-test.sh rendoc:local
```

详细协作约定见 [CONTRIBUTING.md](CONTRIBUTING.md)，安全问题请见 [SECURITY.md](SECURITY.md)，镜像内第三方许可说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可证

[MIT](LICENSE)
