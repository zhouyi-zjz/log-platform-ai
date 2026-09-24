package com.zhouyi.admin.service;

import com.zhouyi.admin.repository.SysUserRepository;
import com.zhouyi.common.domain.model.LoginUser;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author zjz
 * @version 1.0
 * @description:
 * @date 2026/9/12
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final SysUserRepository userRepository;

    public DatabaseUserDetailsService(SysUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(LoginUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("用户名或密码错误"));
    }
}
