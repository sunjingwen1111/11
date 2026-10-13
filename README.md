# demo

## MySQL 配置

项目使用 MyBatis-Plus 3.5.17（Spring Boot 4 Starter、分页插件）和 MySQL Connector/J，数据库操作通过 `UserMapper` 完成，无需手写 JDBC 查询。`system/entity/User.java` 对应 `user` 表，`system/config/MybatisPlusConfig.java` 配置每页 20 条所需的分页拦截器。

默认连接 `192.168.8.142:3306/demo`，用户名为 `root`。数据库须事先存在，应用不会自动创建库或表。

当前连接信息配置在 `src/main/resources/application.yaml` 中。项目也会加载根目录的 `application-local.yaml`，该文件已加入 `.gitignore`，可用于设置本地 `spring.datasource` 覆盖配置。IDEA 运行配置的工作目录应设为项目根目录。

其他环境可使用 Spring Boot 标准环境变量 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 覆盖连接信息。

```powershell
mvn spring-boot:run
```

服务默认监听 `8080` 端口。

## 登录与注册

打开 `http://localhost:8080/login.html` 登录，点击“注册用户”可以创建账号并直接进入用户管理；也可直接访问 `/login.html?mode=register`。退出登录按钮位于用户管理页右上角。原 `/` 消息页和 `/users.html` 用户页在未登录时会跳转登录页。

| 方法 | 路径 | 请求体 | 返回 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | `{"name":"张三","account":"zhangsan","passwd":"example-password"}` | 201；`tokenName`、`tokenValue`、`user` |
| POST | `/api/auth/login` | `{"account":"zhangsan","passwd":"example-password"}` | 200；`tokenName`、`tokenValue`、`user` |
| GET | `/api/auth/me` | 无 | 200；当前用户 |
| POST | `/api/auth/logout` | 无 | 204；注销当前 Token |

除注册和登录外，`/api/**` 均要求在请求头中携带 `satoken: <tokenValue>`；未登录返回 401。前端将 Token 保存在当前浏览器标签页的 `sessionStorage`，关闭标签页后需重新登录。Sa-Token 当前使用单进程内存存储，服务重启会清除登录态；多实例部署应配置共享存储。改密或删除用户会撤销该用户的登录态。旧版单次 MD5 密码在成功登录后会升级到当前三次 MD5 格式。

当前 `user` 表没有角色字段；任何新注册并登录的账号都能访问用户管理 API。正式使用前应增加角色与授权，并将密码存储方式升级为自适应哈希。

## 用户接口

浏览器访问 `http://localhost:8080/users.html` 可使用用户管理页面：查看分页列表、新增、编辑和删除用户。页面与 Spring Boot 同源部署，无需安装前端依赖；原消息演示页仍在 `/`，页面间可以互相切换。用户列表没有记录时显示空状态，不会创建示例用户。

代码位于 `src/main/java/com/example/demo/system`，使用现有 `demo.user` 表。

| 方法 | 路径 | 功能 | 成功状态码 |
| --- | --- | --- | --- |
| GET | `/api/users?page=1` | 用户分页列表，按 ID 倒序；默认第 1 页，每页 20 条 | 200 |
| GET | `/api/users/{id}` | 用户详情 | 200 |
| POST | `/api/users` | 新增用户 | 201 |
| PUT | `/api/users/{id}` | 修改用户 | 200 |
| DELETE | `/api/users/{id}` | 删除用户 | 204 |

新增请求示例（`Content-Type: application/json`）：

```json
{"name":"张三","account":"zhangsan","passwd":"example-password"}
```

修改请求示例：

```json
{"name":"李四","account":"lisi"}
```

`name` 可空；`account` 必填且不能全为空白；新增时 `passwd` 必填且不能全为空白。所有字段最长 100 字符。修改时须提供 `account`，省略 `name` 会设为 NULL；`passwd` 省略、为 NULL 或全为空白时保留原密码，提供有效值时更新密码。

密码接收明文，由后端按当前业务代码连续进行三次 UTF-8、小写 MD5 摘要计算；客户端不要提前进行 MD5。用户响应只包含 `id`、`name`、`account`，不包含密码摘要。MD5 仅用于兼容本教学表的约定，正式密码存储应改用自适应密码哈希。

列表返回 `{ "items": [], "page": 1, "pageSize": 20, "total": 0, "totalPages": 0 }`；页码从 1 开始，超出范围时 `items` 为空，`page` 保留请求页码，`totalPages` 表示实际总页数。详情、新增和修改返回用户对象；删除成功无响应体。页码小于 1、格式不正确等参数错误返回 400，用户不存在返回 404，账号重复返回 409，错误响应包含 `status` 和 `message`。

注册、创建及修改账号时会检查重复，但若数据库仍有历史重复账号，该账号将无法安全登录。请先人工确认并处理历史重复记录，再执行 `src/main/resources/db/user-account-unique.sql` 建立唯一索引，以阻止并发写入重复账号；部署时不自动删除或改写已有用户。

普通测试：`mvn test`。真实 MySQL 接口测试需显式开启，测试会新建临时用户并在结束时清理：

```powershell
$env:RUN_MYSQL_INTEGRATION_TESTS = 'true'
try { mvn test } finally { Remove-Item Env:\RUN_MYSQL_INTEGRATION_TESTS }
```
