# CA证书管理系统

信息安全专业课程设计，两人协作，采用Vue前端与Spring Boot后端分离的结构。

后端按业务模块分包，模块内使用Controller → Service接口 → Service实现类 → Mapper分层；Mapper使用Spring JDBC的`JdbcTemplate`访问SQLite。

实现统一用户申请流程：用户提交本地生成的公钥和证明材料，管理员审核，CA签发、查询、验证和吊销证书。不区分个人和组织。X.509 v3是本项目采用的证书格式，任务书未指定证书标准。

## 当前进度

后端已实现注册、登录、权限、申请与加密材料保存、审核、证书签发、下载、系统内验证和吊销。前端已完成全部页面与接口接入：登录注册、工作台、新建申请（含浏览器端密钥生成与私钥加密保存）、申请列表与详情、我的证书与证书详情、管理员审核、管理员证书管理、证书验证。

页面与接口的对照关系见[前端页面与接口对照分析](docs/前端页面与接口对照分析.md)。

## 目录

```text
ca-system/
├── frontend/                 # 前端负责人：页面、交互、浏览器密钥处理
│   └── src/
│       ├── api/              # 后端接口调用（http 拦截器、auth、applications、certificates）
│       ├── assets/           # 样式与静态资源
│       ├── components/       # 共用组件（状态标签、分页、材料列表、密钥面板、验证结果）
│       ├── router/           # 页面路由与登录/角色守卫
│       ├── utils/            # 浏览器密钥处理、会话状态、下载与格式化
│       └── views/            # 用户端与管理员端页面
├── backend/                  # 后端负责人：接口、数据、证书与权限
│   ├── .mvn/wrapper/         # Maven启动配置
│   ├── src/main/java/io/github/fkwhao/casystem/
│   │   ├── certificateapplication/ # 申请、证明材料和管理员审核
│   │   ├── auth/             # 注册、登录、角色和CSRF
│   │   ├── certificate/      # CA、证书签发、验证和吊销
│   │   ├── common/           # 统一响应、异常处理、加密和审计
│   │   └── health/           # 健康检查接口
│   └── src/main/resources/
├── docs/                     # 分工、接口、数据库和协作说明
└── .gitignore
```

### 前端页面

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/login`、`/register` | 登录、注册 | 公开页面；登录后按角色跳转 |
| `/` | 工作台 | 申请与证书概览、后端连通性、根CA下载、快捷操作 |
| `/applications`、`/applications/new`、`/applications/:id` | 我的申请 | 列表、新建（密钥生成+材料上传）、详情 |
| `/certificates`、`/certificates/:serial` | 我的证书 | 列表、详情、下载、提交吊销申请 |
| `/verify` | 证书验证 | 上传 PEM/DER 证书并分项展示结果 |
| `/admin/applications` | 审核管理（管理员） | 按状态筛选、查看材料、通过/驳回 |
| `/admin/certificates` | 证书管理（管理员） | 全部证书、吊销申请、执行吊销 |

前端隐藏菜单与路由守卫只用于改善体验，实际权限由后端判断。

## 开发环境

- Node.js 20.19+或22.12+；推荐两人统一使用Node.js 24。
- npm，与Node.js一同安装；首次安装依赖后生成并提交`package-lock.json`，后续保留该文件。
- JDK 17，确认`java -version`及`JAVA_HOME`指向同一个JDK。
- Maven已提供Wrapper脚本，固定使用3.9.11，首次运行需要联网下载构建工具和依赖。
- 浏览器推荐使用较新的Chrome或Edge。后续浏览器密钥操作需要localhost或HTTPS环境。

各业务模块按需包含`controller/`、`service/`、`service/impl/`、`mapper/`、`dto/`、`vo/`和`entity/`。依赖注入以构造器方式进行，使用Lombok的`@RequiredArgsConstructor`简化常规构造器；导入Maven后请在IDE中启用注解处理。

后端依赖版本固定在`backend/pom.xml`，前端依赖固定在`frontend/package.json`与锁文件中。

## 启动前端

在仓库根目录打开终端：

```powershell
cd frontend
npm install
npm run dev
```

首次成功安装后会生成`package-lock.json`，将其提交到仓库；此后其他成员可使用`npm ci`按锁文件安装。当前前端仅提供源码，尚未完成依赖安装和构建验证。

访问<http://localhost:5173>。开发服务器将`/api`请求代理到`http://127.0.0.1:8080`，前端代码统一调用`/api/...`。

## 配置和启动后端

首次使用前准备三个环境变量：

| 变量 | 用途 |
| --- | --- |
| `CA_STORAGE_KEY` | 固定的32字节随机密钥，经Base64编码；保护申请人信息和证明材料 |
| `CA_KEYSTORE_PASSWORD` | 至少12字符；保护系统生成的CA PKCS#12密钥库 |
| `CA_ADMIN_PASSWORD` | 首次启动时创建管理员账号所用的密码，至少12字符，UTF-8不超过72字节 |

管理员用户名默认`admin`，可通过`CA_ADMIN_USERNAME`指定。系统没有默认密码，也不会在日志中输出密码；已有管理员不会因环境变量改变而自动修改密码。普通用户通过注册接口创建。

仅首次生成存储密钥时，可在PowerShell执行：

```powershell
$taskKeyBytes = [byte[]]::new(32)
$taskRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $taskRng.GetBytes($taskKeyBytes) } finally { $taskRng.Dispose() }
[Convert]::ToBase64String($taskKeyBytes)
```

将生成的值及两种口令安全保存。每次启动使用同一个`CA_STORAGE_KEY`和CA密钥库口令；密钥和密码不要提交到仓库。若存储密钥丢失，加密信息无法恢复；若已有签发记录时`ca-root.p12`丢失，系统会拒绝生成新根CA。

另开一个终端，在仓库根目录执行：

```powershell
cd backend
$env:CA_STORAGE_KEY = '<保存的32字节Base64密钥>'
$env:CA_KEYSTORE_PASSWORD = '<CA密钥库口令>'
$env:CA_ADMIN_PASSWORD = '<管理员初始口令>'
.\mvnw.cmd spring-boot:run
```

macOS或Linux可使用`sh ./mvnw spring-boot:run`。后端监听`8080`端口，健康检查地址为<http://localhost:8080/api/health>。也可在前端首页点击“检查连接”验证代理是否连通。

后端默认在当前工作目录的`data/`保存SQLite数据库和CA密钥库，该目录已加入忽略规则；也可通过`CA_DATA_DIR`设置数据目录。启动时自动建立数据库表。证明材料作为加密数据保存在SQLite中。健康接口只检查服务进程，不检查数据库业务功能。

调用接口前先获取CSRF令牌；登录后重新获取令牌。完整请求字段、Cookie与接口示例见[接口约定](docs/接口约定.md)。

如终端仍使用Java 8或11，可在当前终端设置`JAVA_HOME`和`PATH`指向已安装的JDK 17，或在IDE项目设置中选择JDK 17。项目不修改电脑的全局Java配置。

### IDEA导入异常

若重新加载Maven时出现`Guice/MissingImplementation`、`ArtifactHandlerManager`或`Unable to create injector`，先检查IDEA使用的Maven版本。这类信息来自Maven导入器初始化，导入失败后可能同时出现Spring依赖无法解析、语言级别回退以及`record`无法识别。

1. 在“设置 → 构建、执行、部署 → 构建工具 → Maven”中，将Maven主目录选择为本项目的Wrapper（3.9.11），或内置Maven 3（本机为3.9.11）。
2. 在Maven的导入和运行设置中选用JDK 17；项目SDK与模块语言级别也应采用17。
3. 应用设置，重新加载所有Maven项目，等待依赖导入和索引完成。
4. 如果仍出现同一初始化异常，重启IDEA后再导入，并检查Maven导入日志。

普通的“类或方法从未使用”提示不等于编译失败，Spring控制器由框架发现和调用。应先处理Maven导入错误，再检查剩余的代码诊断。

## 构建

```powershell
# 在frontend目录执行
npm run build

# 在backend目录执行
.\mvnw.cmd package
```

后端集成测试可在`backend/`运行`.\mvnw.cmd test`。测试使用临时数据库和独立测试密钥，不读取或修改实际数据目录。

前端产物位于`frontend/dist/`，后端产物位于`backend/target/`。当前产物分别运行；最终前端资源整合进后端的打包步骤在交付阶段补充。

## 分工和协作

- 前端负责人维护`frontend/`，负责用户端与管理员端页面、浏览器密钥生成和加密保存私钥。
- 后端负责人维护`backend/`，负责接口、权限、数据库、材料、CA密钥保管、证书签发和吊销。
- 接口、状态和数据字段先在`docs/`共同确认，再分别实现。
- 每个功能使用短期分支，例如`feat/application-form`或`feat/certificate-issue`，通过Pull Request合并到`main`。
- 私钥、口令、本地数据库和真实身份材料不提交到仓库。后续如需公开CA证书示例，单独确认示例文件可公开后再添加。

本项目用于课程演示：身份材料由管理员人工审核，未实现申请人私钥持有证明、CRL或OCSP发布。系统内验证会查询本地吊销记录；外部客户端不会自动知道吊销状态。

## 文档

- [两人分工方案](docs/分工方案.md)
- [接口约定](docs/接口约定.md)
- [OpenAPI接口定义（可导入Apifox）](docs/openapi.yaml)
- [Apifox导入与联调](docs/Apifox使用说明.md)
- [数据库设计](docs/数据库设计.md)
- [协作约定](docs/协作约定.md)
