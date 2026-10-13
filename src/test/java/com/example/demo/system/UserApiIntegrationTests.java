package com.example.demo.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.system.entity.User;
import com.example.demo.system.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 显式开启后访问配置中的真实 MySQL，只清理本测试创建的记录。 */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_INTEGRATION_TESTS", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiIntegrationTests {
    @Autowired Environment environment;
    @Autowired UserMapper mapper;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String sessionToken;
    private Integer sessionUserId;
    private String sessionAccount;

    @BeforeAll
    void registerSession() throws Exception {
        sessionAccount = "authTest" + UUID.randomUUID().toString().replace("-", "");
        var response = authRequest("POST", "/register", Map.of(
                "name", "测试登录", "account", sessionAccount, "passwd", "auth-secret"), null);
        assertEquals(201, response.statusCode(), response.body());
        var data = json.readTree(response.body());
        sessionToken = data.get("tokenValue").asText();
        sessionUserId = data.get("user").get("id").asInt();
        assertEquals("satoken", data.get("tokenName").asText());
    }

    @AfterAll
    void removeSessionUser() {
        if (sessionUserId != null) {
            mapper.delete(new LambdaQueryWrapper<User>()
                    .eq(User::getId, sessionUserId).eq(User::getAccount, sessionAccount));
        }
    }

    @Test
    void crudPersistsDataAndProtectsPasswords() throws Exception {
        String account = "crud_test_" + UUID.randomUUID().toString().replace("-", "");
        Integer id = null;
        try {
            var created = request("POST", "", Map.of("name", "测试用户", "account", account, "passwd", "hello"));
            assertEquals(201, created.statusCode(), created.body());
            JsonNode user = json.readTree(created.body());
            id = user.get("id").asInt();
            assertEquals("/api/users/" + id, created.headers().firstValue("Location").orElseThrow());
            assertEquals("测试用户", user.get("name").asText());
            assertFalse(user.has("passwd"));
            assertEquals("0dcd649d4ef5f787e39ddf48d8e625a5", password(id));

            var detail = request("GET", "/" + id, null);
            assertEquals(200, detail.statusCode());
            assertEquals(account, json.readTree(detail.body()).get("account").asText());
            var list = request("GET", "", null);
            assertEquals(200, list.statusCode());
            boolean found = false;
            JsonNode firstPage = json.readTree(list.body());
            assertEquals(1, firstPage.get("page").asInt());
            assertEquals(20, firstPage.get("pageSize").asInt());
            for (JsonNode item : firstPage.get("items")) {
                assertFalse(item.has("passwd"));
                if (item.get("id").asInt() == id) found = true;
            }
            assertTrue(found);

            // 不传密码、重复提交相同内容和传空密码，都保留已有密码。
            for (int attempt = 0; attempt < 2; attempt++) {
                var updated = request("PUT", "/" + id, Map.of("name", "更新用户", "account", account));
                assertEquals(200, updated.statusCode(), updated.body());
                assertEquals("更新用户", json.readTree(updated.body()).get("name").asText());
                assertEquals("0dcd649d4ef5f787e39ddf48d8e625a5", password(id));
            }
            var clearedName = request("PUT", "/" + id, Map.of("account", account));
            assertEquals(200, clearedName.statusCode(), clearedName.body());
            assertTrue(json.readTree(clearedName.body()).get("name").isNull());
            assertNull(mapper.selectById(id).getName());
            assertEquals("0dcd649d4ef5f787e39ddf48d8e625a5", password(id));
            assertEquals(200, request("PUT", "/" + id,
                    Map.of("name", "更新用户", "account", account, "passwd", "")).statusCode());
            assertEquals("0dcd649d4ef5f787e39ddf48d8e625a5", password(id));

            // 含引号的文本必须作为参数保存，不能改变 SQL 语义。
            String quotedName = "O'Reilly 测试";
            var changedPassword = request("PUT", "/" + id,
                    Map.of("name", quotedName, "account", account, "passwd", "world"));
            assertEquals(200, changedPassword.statusCode(), changedPassword.body());
            assertEquals("1283f82c7cffe9fa345c218314cb934a", password(id));
            assertEquals(quotedName, mapper.selectById(id).getName());

            assertEquals(400, request("PUT", "/" + id, Map.of("account", " ")).statusCode());
            assertEquals(quotedName, mapper.selectById(id).getName());
            var deleted = request("DELETE", "/" + id, null);
            assertEquals(204, deleted.statusCode());
            assertTrue(deleted.body().isEmpty());
            assertNull(mapper.selectById(id));
            assertEquals(404, request("GET", "/" + id, null).statusCode());
            assertEquals(404, request("PUT", "/" + id, Map.of("account", account)).statusCode());
            assertEquals(404, request("DELETE", "/" + id, null).statusCode());
        } finally {
            var cleanup = new LambdaQueryWrapper<User>().eq(User::getAccount, account);
            if (id != null) cleanup.eq(User::getId, id);
            mapper.delete(cleanup);
        }
    }

    @Test
    void rejectsMissingFieldsOversizedValuesAndMalformedIds() throws Exception {
        assertEquals(400, request("POST", "", Map.of("account", "", "passwd", "hello")).statusCode());
        assertEquals(400, request("POST", "", Map.of("account", "test")).statusCode());
        assertEquals(400, request("POST", "", Map.of("account", "test", "passwd", " ")).statusCode());
        String longPassword = "sensitive".repeat(12);
        var invalidPassword = request("POST", "", Map.of("account", "test", "passwd", longPassword));
        assertEquals(400, invalidPassword.statusCode());
        assertFalse(invalidPassword.body().contains(longPassword));
        assertTrue(invalidPassword.body().contains("passwd"));
        assertEquals(400, request("POST", "", Map.of("name", "x".repeat(101), "account", "test", "passwd", "hello")).statusCode());
        assertEquals(400, request("GET", "/not-an-id", null).statusCode());
    }

    @Test
    void registrationLoginAndLogoutRequireValidTokens() throws Exception {
        assertEquals(401, send("/api/users", "GET", null, null).statusCode());
        assertEquals(401, send("/api/messages", "POST", Map.of("message", "test"), null).statusCode());
        assertEquals(401, authRequest("GET", "/me", null, null).statusCode());
        assertEquals(400, authRequest("POST", "/register", Map.of("account", "test"), null).statusCode());
        assertEquals(409, authRequest("POST", "/register", Map.of(
                "account", sessionAccount, "passwd", "another-password"), null).statusCode());
        assertEquals(409, request("POST", "", Map.of(
                "account", sessionAccount, "passwd", "another-password")).statusCode());
        assertEquals(401, authRequest("POST", "/login", Map.of(
                "account", sessionAccount, "passwd", "wrong-password"), null).statusCode());

        var login = authRequest("POST", "/login", Map.of(
                "account", sessionAccount, "passwd", "auth-secret"), null);
        assertEquals(200, login.statusCode(), login.body());
        String extraToken = json.readTree(login.body()).get("tokenValue").asText();
        assertFalse(extraToken.isBlank());
        var me = authRequest("GET", "/me", null, extraToken);
        assertEquals(200, me.statusCode());
        assertEquals(sessionAccount, json.readTree(me.body()).get("account").asText());
        assertFalse(json.readTree(me.body()).has("passwd"));
        assertEquals(204, authRequest("POST", "/logout", null, extraToken).statusCode());
        assertEquals(401, authRequest("GET", "/me", null, extraToken).statusCode());
        assertEquals(200, authRequest("GET", "/me", null, sessionToken).statusCode());
    }

    @Test
    void olderSingleMd5AccountIsUpgradedAndPasswordChangeRevokesLogin() throws Exception {
        String account = "legacyAuth" + UUID.randomUUID().toString().replace("-", "");
        var legacy = new User();
        legacy.setAccount(account);
        legacy.setPasswd("5d41402abc4b2a76b9719d911017c592");
        mapper.insert(legacy);
        try {
            var login = authRequest("POST", "/login", Map.of("account", account, "passwd", "hello"), null);
            assertEquals(200, login.statusCode(), login.body());
            String legacyToken = json.readTree(login.body()).get("tokenValue").asText();
            assertEquals("0dcd649d4ef5f787e39ddf48d8e625a5", mapper.selectById(legacy.getId()).getPasswd());
            assertEquals(200, authRequest("GET", "/me", null, legacyToken).statusCode());

            assertEquals(200, request("PUT", "/" + legacy.getId(), Map.of(
                    "account", account, "passwd", "updated-secret")).statusCode());
            assertEquals(401, authRequest("GET", "/me", null, legacyToken).statusCode());
            assertEquals(401, authRequest("POST", "/login", Map.of(
                    "account", account, "passwd", "hello"), null).statusCode());
            assertEquals(200, authRequest("POST", "/login", Map.of(
                    "account", account, "passwd", "updated-secret"), null).statusCode());
        } finally {
            mapper.delete(new LambdaQueryWrapper<User>()
                    .eq(User::getId, legacy.getId()).eq(User::getAccount, account));
        }
    }

    @Test
    void paginatesTwentyUsersAndReturnsTotals() throws Exception {
        String accountPrefix = "pageTest" + UUID.randomUUID().toString().replace("-", "");
        long previousTotal = mapper.selectCount(null);
        try {
            for (int index = 0; index < 21; index++) {
                var user = new User();
                user.setName("pagination test");
                user.setAccount(accountPrefix + index);
                user.setPasswd("test hash");
                mapper.insert(user);
            }

            var insertedIds = mapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId)
                            .likeRight(User::getAccount, accountPrefix)
                            .orderByDesc(User::getId))
                    .stream().map(User::getId).toList();
            assertEquals(21, insertedIds.size());

            JsonNode first = json.readTree(request("GET", "", null).body());
            assertEquals(1, first.get("page").asInt());
            assertEquals(20, first.get("pageSize").asInt());
            assertEquals(previousTotal + 21, first.get("total").asLong());
            assertEquals((previousTotal + 40) / 20, first.get("totalPages").asLong());
            assertEquals(20, first.get("items").size());
            for (int index = 0; index < 20; index++) {
                assertEquals(insertedIds.get(index).intValue(), first.get("items").get(index).get("id").asInt());
                assertFalse(first.get("items").get(index).has("passwd"));
            }

            JsonNode second = json.readTree(request("GET", "?page=2", null).body());
            assertEquals(2, second.get("page").asInt());
            assertEquals(previousTotal + 21, second.get("total").asLong());
            assertEquals(insertedIds.get(20).intValue(), second.get("items").get(0).get("id").asInt());
            assertEquals(Math.min(20L, previousTotal + 1), second.get("items").size());

            JsonNode beyondLast = json.readTree(request("GET", "?page=100000", null).body());
            assertEquals(0, beyondLast.get("items").size());
            assertEquals(previousTotal + 21, beyondLast.get("total").asLong());
            assertEquals(400, request("GET", "?page=0", null).statusCode());
            assertEquals(400, request("GET", "?page=-1", null).statusCode());
            assertEquals(400, request("GET", "?page=abc", null).statusCode());
        } finally {
            mapper.delete(new LambdaQueryWrapper<User>().likeRight(User::getAccount, accountPrefix));
        }
    }

    private String password(int id) {
        return mapper.selectById(id).getPasswd();
    }

    private HttpResponse<String> request(String method, String path, Object body) throws Exception {
        return send("/api/users" + path, method, body, sessionToken);
    }

    private HttpResponse<String> authRequest(String method, String path, Object body, String token) throws Exception {
        return send("/api/auth" + path, method, body, token);
    }

    private HttpResponse<String> send(String path, String method, Object body, String token) throws Exception {
        String base = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port");
        var publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
        var builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .method(method, publisher);
        if (token != null) builder.header("satoken", token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
