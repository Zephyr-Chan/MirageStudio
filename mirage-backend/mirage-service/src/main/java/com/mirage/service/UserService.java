package com.mirage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mirage.common.exception.BusinessException;
import com.mirage.common.exception.BizExceptionEnum;
import com.mirage.common.util.SnowflakeId;
import com.mirage.dao.entity.User;
import com.mirage.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务: 注册(BCrypt) / 登录验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final SnowflakeId snowflakeId;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     *
     * @param username 用户名
     * @param password 明文密码
     * @param email    邮箱(可空)
     * @return 新建用户(密码哈希已脱敏)
     */
    public User register(String username, String password, String email) {
        // 校验用户名唯一
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(BizExceptionEnum.USERNAME_EXISTS);
        }

        User user = new User();
        user.setId(snowflakeId.nextId());
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);

        log.info("用户注册成功: userId={}, username={}", user.getId(), username);
        // 返回时清除密码哈希
        user.setPasswordHash(null);
        return user;
    }

    /**
     * 登录验证
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 校验通过的用户实体
     */
    public User login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(BizExceptionEnum.USER_NOT_FOUND);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(BizExceptionEnum.USER_DISABLED);
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(BizExceptionEnum.PASSWORD_WRONG);
        }
        log.info("用户登录成功: userId={}, username={}", user.getId(), username);
        return user;
    }

    /**
     * 根据 ID 查询用户
     */
    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(BizExceptionEnum.USER_NOT_FOUND);
        }
        return user;
    }
}
