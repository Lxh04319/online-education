package com.xuecheng.ucenter.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.api.R;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 */
@Slf4j
@Component
public class UserServiceImpl implements UserDetailsService {
    @Autowired
    XcUserMapper xcUserMapper;

    @Autowired
    ApplicationContext applicationContext;

    //传入的请求认证的参数就是AuthParamsDto
    @Override
    public UserDetails loadUserByUsername(String s) throws UsernameNotFoundException {
        //json转对象
        AuthParamsDto authParamsDto=null;
        try{
            authParamsDto=JSON.parseObject(s,AuthParamsDto.class);
        }catch(Exception e){
            throw new RuntimeException("参数不正确");
        }
        //认证类型
        String authType=authParamsDto.getAuthType();
        //根据认证类型取bean
        String beanName=authType+"_authservice";
        AuthService authService=applicationContext.getBean(beanName,AuthService.class);
        //调用execute方法
        XcUserExt xcUserExt=authService.execute(authParamsDto);
        //封装为userdetails
        UserDetails userDetails=getUserPrincipal(xcUserExt);
        return userDetails;
    }

    /**
     * @description 查询用户信息
     * @param xcUser  用户id，主键
     * @return com.xuecheng.ucenter.model.po.XcUser 用户信息
     * @author Mr.M
     */
    public UserDetails getUserPrincipal(XcUserExt xcUser){
        String password=xcUser.getPassword();
        //权限
        String[] authorities={"test"};
        xcUser.setPassword(null);
        //转json
        String userJson=JSON.toJSONString(xcUser);
        UserDetails userDetails=User.withUsername(userJson).password(password).authorities(authorities).build();
        return userDetails;
    }
}
