# Apifox 导入与联调

后端接口定义保存在[openapi.yaml](openapi.yaml)，格式为 OpenAPI 3.0。接口说明、字段规则和返回示例见[接口约定](接口约定.md)。

## 导入接口

1. 在 Apifox 中创建团队项目时选择“导入项目”，或在已有项目中进入“项目设置 → 导入数据”。
2. 选择 OpenAPI/Swagger 格式，导入仓库里的 `docs/openapi.yaml`。
3. 确认接口列表及导入选项后完成导入。已有项目再次导入时，留意资源清理选项，避免误删团队已有接口。
4. 创建本地调试环境，将服务地址设置为 `http://127.0.0.1:8080`。仓库中的后端默认只监听本机；组员在自己的电脑上调试时，应先在自己的电脑启动后端。

Apifox 支持导入 OpenAPI 3.0 的 YAML 文件，步骤以[官方导入说明](https://docs.apifox.com/import-openapi-swagger)为准。本仓库文件经过 YAML 解析和引用检查，尚未在你的 Apifox 项目中实际导入。

## 首次启动

按[项目 README](../README.md)配置 JDK 17、`CA_STORAGE_KEY`、`CA_KEYSTORE_PASSWORD` 和 `CA_ADMIN_PASSWORD`，再启动后端。管理员默认用户名是 `admin`，密码由你在首次启动时配置。普通用户需要通过注册接口创建。

## 调试登录和业务接口

使用同一个 Apifox 环境和 Cookie 管理器，按顺序操作：

1. 调用 `GET /api/health`，确认后端已启动。
2. 调用 `GET /api/auth/csrf`，保留响应中的 `XSRF-TOKEN` Cookie，并复制响应体 `data.token`。
3. 调用 `POST /api/auth/register` 注册普通用户，添加请求头 `X-XSRF-TOKEN: <上一步的 token>`。
4. 调用 `POST /api/auth/login`，继续保留 Cookie 和 CSRF 请求头；登录成功后会建立 `JSESSIONID` 会话。
5. 再次调用 `GET /api/auth/csrf`，使用新的 Cookie 和令牌调用后续 POST 接口。
6. 调用 `GET /api/auth/me` 检查当前登录账号，再按“提交申请 → 管理员审核 → 下载并验证证书 → 吊销”联调。

调用管理员接口时，先用管理员账号完成同样的登录和令牌刷新步骤。不同账号的调试过程要注意当前 Cookie 对应哪个会话；退出或切换账号后重新获取令牌。Apifox 的 Cookie 管理方式见[官方说明](https://docs.apifox.com/response-and-cookie)。

`POST /api/applications` 使用 `multipart/form-data`，字段为 `applicantName`、`email`、`publicKeyPem` 和 `evidenceFiles`。其中材料可重复添加1至3个文件。公钥须由申请人在本地生成；**不要上传用户私钥或私钥口令**。

## 联调注意事项

- 调试本机 `127.0.0.1` 时，使用能够访问本机的 Apifox 客户端或本地代理；云端代理无法访问你的本机服务。[官方代理说明](https://docs.apifox.com/5830401m0)
- `401` 通常表示没有有效登录会话；`403` 可能是角色权限不足或 CSRF Cookie、请求头缺失。
- `503` 可能表示存储密钥、CA 密钥库口令或密钥库文件不可用，先检查后端配置。
- 接口定义变更时，两人先同步仓库里的 OpenAPI 文件和说明，再更新 Apifox 项目，保持文档与代码一致。
- 使用虚构或脱敏的测试材料，不要把真实个人信息、口令、CA 密钥或私钥保存到共享接口示例中。
