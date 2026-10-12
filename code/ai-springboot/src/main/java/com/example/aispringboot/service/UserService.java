package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aispringboot.DTO.command.UserLoginCommandDTO;
import com.example.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.example.aispringboot.DTO.response.UserLoginResponseDTO;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.entity.User;
import com.example.aispringboot.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class UserService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 用户类型：1 普通用户，2 管理员 */
    private static final int USER_TYPE_NORMAL = 1;
    private static final int USER_TYPE_ADMIN = 2;
    /** 状态：0 禁用，1 正常 */
    private static final int STATUS_NORMAL = 1;

    @Resource
    private UserMapper userMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private JwtUtil jwtUtil;

    /**
     * 登录。
     *
     * <p>注意这里用 selectList + LIMIT 1 而非 selectOne：user.username 与 user.email
     * 在库中没有唯一约束，一旦出现重复行 selectOne 会抛 TooManyResultsException。
     */
    public Result<UserLoginResponseDTO> login(UserLoginCommandDTO commandDTO) {
        String account = commandDTO.getUsername().trim();
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or()
                .eq(User::getEmail, account)
                .last("LIMIT 1");
        List<User> users = userMapper.selectList(queryWrapper);
        if (users.isEmpty()) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), "用户不存在");
        }
        User user = users.get(0);

        if (!passwordEncoder.matches(commandDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "用户名或密码错误");
        }
        if (!Integer.valueOf(STATUS_NORMAL).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "账号已被禁用，请联系管理员");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUserType());
        return Result.ok(buildLoginResponse(user, token));
    }

    /**
     * 注册。
     *
     * <p>库中 username / email 均无唯一约束，唯一性只能在应用层查重。
     * 这里的检查存在极小的时间窗（并发同时注册同一用户名仍可能写重），
     * 彻底解决需要给表加唯一索引，但库是既有资产不宜改 schema，故维持现状。
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<UserLoginResponseDTO> register(UserRegisterCommandDTO commandDTO) {
        String username = commandDTO.getUsername().trim();
        String email = commandDTO.getEmail().trim();

        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_SAME.getCode(), "用户名已存在");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_SAME.getCode(), "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(commandDTO.getPassword()));
        // 注册入口不信任前端传入的 userType，固定为普通用户
        user.setUserType(USER_TYPE_NORMAL);
        user.setStatus(STATUS_NORMAL);
        // 前端只在非空时提交这两个字段，空字符串会污染查询条件
        user.setNickname(blankToNull(commandDTO.getNickname()));
        user.setPhone(blankToNull(commandDTO.getPhone()));
        userMapper.insert(user);

        // created_at / updated_at 由数据库默认值写入，MyBatis-Plus 的 insert 不会回填，
        // 需要回读一次才能把完整用户信息返回给前端
        User persisted = userMapper.selectById(user.getId());
        String token = jwtUtil.generateToken(user.getId(), user.getUserType());
        return Result.ok(buildLoginResponse(persisted, token));
    }

    /**
     * 登出。
     *
     * <p>JWT 无状态，服务端不保存会话，这里直接返回成功；
     * 若后续要求主动失效，需要引入 Redis 黑名单并在拦截器中查询。
     */
    public Result<Void> logout() {
        return Result.ok(null);
    }

    private UserLoginResponseDTO buildLoginResponse(User user, String token) {
        UserLoginResponseDTO dto = new UserLoginResponseDTO();
        dto.setToken(token);
        dto.setRoleType(Integer.valueOf(USER_TYPE_ADMIN).equals(user.getUserType()) ? "admin" : "user");

        UserLoginResponseDTO.UserDetailResponseDTO info = new UserLoginResponseDTO.UserDetailResponseDTO();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setEmail(user.getEmail());
        info.setPhone(user.getPhone());
        info.setNickname(user.getNickname());
        info.setAvatar(user.getAvatar());
        info.setGender(user.getGender());
        info.setGenderDisplayName(describeGender(user.getGender()));
        info.setBirthday(user.getBirthday() == null ? null : user.getBirthday().toString());
        info.setUserType(user.getUserType());
        info.setUserTypeDisplayName(Integer.valueOf(USER_TYPE_ADMIN).equals(user.getUserType()) ? "管理员" : "普通用户");
        info.setStatus(user.getStatus());
        info.setStatusDisplayName(Integer.valueOf(STATUS_NORMAL).equals(user.getStatus()) ? "正常" : "禁用");
        info.setDisplayName(user.getNickname() != null && !user.getNickname().isBlank()
                ? user.getNickname() : user.getUsername());
        info.setCreatedAt(format(user.getCreatedAt()));
        info.setUpdatedAt(format(user.getUpdatedAt()));
        dto.setUserInfo(info);
        return dto;
    }

    private static String describeGender(Integer gender) {
        if (gender == null) {
            return "未填写";
        }
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "保密";
        };
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : FMT.format(time);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
