package com.xuecheng.ucenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.ucenter.feignclient.CheckCodeClient;
import com.xuecheng.ucenter.mapper.XcUserMapper;
import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;
import com.xuecheng.ucenter.model.po.XcUser;
import com.xuecheng.ucenter.service.AuthService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * @author Mr.M
 * @version 1.0
 * @description 账号名密码方式
 * @date 2023/2/24 11:56
 */
@Service("password_authservice")
public class PasswordAuthServiceImpl implements AuthService {

 @Autowired
 XcUserMapper xcUserMapper;

 @Autowired
 PasswordEncoder passwordEncoder;


 @Override
 public XcUserExt execute(AuthParamsDto authParamsDto) {
  //账号
  String username=authParamsDto.getUsername();
  //输入验证码
  String checkcode=authParamsDto.getCheckcode();
  //验证key
  String checkcodekey=authParamsDto.getCheckcodekey();
  if(StringUtils.isEmpty(checkcodekey)||StringUtils.isEmpty(checkcodekey)){
   throw new RuntimeException("请输入验证码");
  }
  //验证码服务
  //查询数据库
  XcUser xcUser=xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getUsername,username));
  //不存在
  if(xcUser==null){
   throw new RuntimeException("不存在");
  }
  //密码是否正确
  String passworddb=xcUser.getPassword();
  //输入的密码
  String passworduser=authParamsDto.getPassword();
  boolean matches=passwordEncoder.matches(passworduser,passworddb);
  if(!matches){
   throw new RuntimeException("账号或密码错误");
  }
  XcUserExt xcUserExt=new XcUserExt();
  BeanUtils.copyProperties(xcUser,xcUserExt);
  return xcUserExt;
 }
}
