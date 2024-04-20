package com.xuecheng.ucenter.service;

import com.xuecheng.ucenter.model.dto.AuthParamsDto;
import com.xuecheng.ucenter.model.dto.XcUserExt;

/**
 * @author lxh11111
 * @version 1.0
 * @description 统一的认证接口
 */
public interface AuthService {

 /**
  * @description 认证方法
  * @param authParamsDto 认证参数
  * @return com.xuecheng.ucenter.model.po.XcUser 用户信息
  */
 XcUserExt execute(AuthParamsDto authParamsDto);

}
