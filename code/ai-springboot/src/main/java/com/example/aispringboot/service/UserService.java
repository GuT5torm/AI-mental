package com.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aispringboot.DTO.command.UserAdminCreateCommandDTO;
import com.example.aispringboot.DTO.command.UserAdminPageQuery;
import com.example.aispringboot.DTO.command.UserAdminUpdateCommandDTO;
import com.example.aispringboot.DTO.command.UserLoginCommandDTO;
import com.example.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.example.aispringboot.DTO.response.UserAdminResponseDTO;
import com.example.aispringboot.DTO.response.UserLoginResponseDTO;
import com.example.aispringboot.common.AuthUtil;
import com.example.aispringboot.common.BusinessException;
import com.example.aispringboot.common.JwtUtil;
import com.example.aispringboot.common.RateLimiter;
import com.example.aispringboot.common.Result;
import com.example.aispringboot.common.ResultCode;
import com.example.aispringboot.entity.User;
import com.example.aispringboot.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class UserService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 用户类型：1 普通用户，2 管理员 */
    private static final int USER_TYPE_NORMAL = 1;
    private static final int USER_TYPE_ADMIN = 2;
    /** 状态：0 禁用，1 正常 */
    private static final int STATUS_NORMAL = 1;

    /** 同一账号 15 分钟内连续失败 5 次后锁定，防止密码爆破 */
    private static final int MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_LOCK_WINDOW = Duration.ofMinutes(15);
    /** 同一 IP 每小时最多 5 次注册，防止脚本批量刷号 */
    private static final int MAX_REGISTERS_PER_IP = 5;
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);

    @Resource
    private UserMapper userMapper;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private RateLimiter rateLimiter;

    /**
     * 登录。
     *
     * <p>注意这里用 selectList + LIMIT 1 而非 selectOne：user.username 与 user.email
     * 在库中没有唯一约束，一旦出现重复行 selectOne 会抛 TooManyResultsException。
     */
    public Result<UserLoginResponseDTO> login(UserLoginCommandDTO commandDTO) {
        String account = commandDTO.getUsername().trim();
        String failKey = "login:" + account.toLowerCase(Locale.ROOT);
        if (rateLimiter.isBlocked(failKey, MAX_LOGIN_FAILURES, LOGIN_LOCK_WINDOW)) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "登录尝试过于频繁，请15分钟后再试");
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, account)
                .or()
                .eq(User::getEmail, account)
                .last("LIMIT 1");
        List<User> users = userMapper.selectList(queryWrapper);
        // 账号不存在与密码错误返回完全一致的 code/msg，避免账号枚举；
        // 两者都计入失败次数，防止用不存在账号探测接口行为差异
        if (users.isEmpty()) {
            rateLimiter.recordFailure(failKey, MAX_LOGIN_FAILURES, LOGIN_LOCK_WINDOW);
            throw new BusinessException(ResultCode.ERROR.getCode(), "用户名或密码错误");
        }
        User user = users.get(0);

        if (!passwordEncoder.matches(commandDTO.getPassword(), user.getPassword())) {
            rateLimiter.recordFailure(failKey, MAX_LOGIN_FAILURES, LOGIN_LOCK_WINDOW);
            throw new BusinessException(ResultCode.ERROR.getCode(), "用户名或密码错误");
        }
        if (!Integer.valueOf(STATUS_NORMAL).equals(user.getStatus())) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "账号已被禁用，请联系管理员");
        }
        rateLimiter.reset(failKey);

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
        // 按 IP 限流，防止脚本批量注册；/register 与 /add 两个入口都经过这里
        String registerKey = "register:" + currentIp();
        if (!rateLimiter.tryAcquire(registerKey, MAX_REGISTERS_PER_IP, REGISTER_WINDOW)) {
            throw new BusinessException(ResultCode.ERROR.getCode(), "注册过于频繁，请稍后再试");
        }

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

    /** 当前请求来源 IP；非 HTTP 上下文（如单测直调）返回 unknown，限流退化为全局限流 */
    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return "unknown";
    }

    // ==================== 后台用户管理 ====================

    /** 后台新增用户的默认初始密码，与重置密码一致 */
    private static final String DEFAULT_ADMIN_PASSWORD = "Abc@123456";

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 后台直接新增用户：用户名/邮箱唯一，密码留空时使用默认初始密码。
     */
    @Transactional(rollbackFor = Exception.class)
    public UserAdminResponseDTO adminCreate(UserAdminCreateCommandDTO command) {
        AuthUtil.requireAdmin();
        String username = command.getUsername().trim();
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_SAME.getCode(), "用户名已存在");
        }
        String email = trimToNull(command.getEmail());
        if (email != null
                && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_SAME.getCode(), "邮箱已被注册");
        }

        User user = new User();
        user.setUsername(username);
        String password = command.getPassword() == null || command.getPassword().isBlank()
                ? DEFAULT_ADMIN_PASSWORD : command.getPassword().trim();
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(trimToNull(command.getNickname()));
        user.setEmail(email);
        user.setPhone(trimToNull(command.getPhone()));
        user.setGender(command.getGender() == null ? 0 : command.getGender());
        user.setUserType(command.getUserType() == null ? USER_TYPE_NORMAL : command.getUserType());
        user.setStatus(STATUS_NORMAL);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("管理员新增用户 {}（角色={}）", username, user.getUserType());
        return toAdminResponse(user);
    }

    /**
     * 后台分页查询用户，关键词模糊匹配用户名/昵称/邮箱。
     */
    public IPage<UserAdminResponseDTO> adminPage(UserAdminPageQuery query) {
        AuthUtil.requireAdmin();

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getNickname, kw)
                    .or().like(User::getEmail, kw));
        }
        if (query.getStatus() != null) {
            wrapper.eq(User::getStatus, query.getStatus());
        }
        wrapper.orderByAsc(User::getId);

        IPage<User> page = userMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSizeValue()), wrapper);
        return page.convert(this::toAdminResponse);
    }

    /**
     * 启用/禁用用户。不能操作自己的账号，避免管理员把自己锁在门外。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        AuthUtil.requireAdmin();
        if (id.equals(AuthUtil.requireUserId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能修改自己的状态");
        }
        User user = requireUser(id);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    /**
     * 后台编辑用户资料：昵称/邮箱/电话/性别/角色/真实模型开关。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateByAdmin(Long id, UserAdminUpdateCommandDTO command) {
        AuthUtil.requireAdmin();
        User user = requireUser(id);

        if (command.getNickname() != null) {
            user.setNickname(command.getNickname().isBlank() ? null : command.getNickname().trim());
        }
        if (command.getEmail() != null) {
            user.setEmail(command.getEmail().isBlank() ? null : command.getEmail().trim());
        }
        if (command.getPhone() != null) {
            user.setPhone(command.getPhone().isBlank() ? null : command.getPhone().trim());
        }
        if (command.getGender() != null) {
            user.setGender(command.getGender());
        }
        if (command.getUserType() != null) {
            if (id.equals(AuthUtil.requireUserId()) && command.getUserType() != USER_TYPE_ADMIN) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "不能降级自己的角色");
            }
            user.setUserType(command.getUserType());
        }
        if (command.getAiRealEnabled() != null) {
            user.setAiRealEnabled(command.getAiRealEnabled());
        }
        userMapper.updateById(user);
    }

    /**
     * 重置密码为固定初始值（Abc@123456），与注册校验规则一致。
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id) {
        AuthUtil.requireAdmin();
        User user = requireUser(id);
        user.setPassword(passwordEncoder.encode("Abc@123456"));
        userMapper.updateById(user);
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户不存在");
        }
        return user;
    }

    private UserAdminResponseDTO toAdminResponse(User user) {
        UserAdminResponseDTO dto = new UserAdminResponseDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAvatar(user.getAvatar());
        dto.setGender(user.getGender());
        dto.setUserType(user.getUserType());
        dto.setStatus(user.getStatus());
        dto.setAiRealEnabled(user.getAiRealEnabled() == null ? 1 : user.getAiRealEnabled());
        dto.setCreatedAt(user.getCreatedAt());
        return dto;
    }
}
