package com.example.demo.system.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.demo.system.dto.CreateUserRequest;
import com.example.demo.system.dto.UserResponse;
import com.example.demo.system.entity.User;
import com.example.demo.system.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AuthService {
    private final UserMapper mapper;

    public AuthService(UserMapper mapper) {
        this.mapper = mapper;
    }

    public AuthResponse register(CreateUserRequest request) {
        String account = request.account().trim();
        if (mapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getAccount, account)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "账号已存在");
        }
        var user = new User();
        user.setName(request.name());
        user.setAccount(account);
        user.setPasswd(PasswordCodec.hash(request.passwd()));
        mapper.insert(user);
        return signedIn(user);
    }

    public AuthResponse login(LoginRequest request) {
        List<User> users = mapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getAccount, request.account().trim()).last("LIMIT 2"));
        if (users.size() != 1) throw invalidCredentials();

        User user = users.getFirst();
        if (!PasswordCodec.matches(request.passwd(), user.getPasswd())) {
            if (!PasswordCodec.matchesLegacy(request.passwd(), user.getPasswd())) throw invalidCredentials();
            int upgraded = mapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, user.getId())
                    .eq(User::getPasswd, user.getPasswd())
                    .set(User::getPasswd, PasswordCodec.hash(request.passwd())));
            if (upgraded != 1) throw invalidCredentials();
        }
        return signedIn(user);
    }

    public UserResponse currentUser() {
        int id = StpUtil.getLoginIdAsInt();
        User user = mapper.selectById(id);
        if (user == null) {
            StpUtil.logout();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效");
        }
        return toResponse(user);
    }

    private AuthResponse signedIn(User user) {
        StpUtil.login(user.getId());
        return new AuthResponse(StpUtil.getTokenName(), StpUtil.getTokenValue(), toResponse(user));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getAccount());
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
    }
}
