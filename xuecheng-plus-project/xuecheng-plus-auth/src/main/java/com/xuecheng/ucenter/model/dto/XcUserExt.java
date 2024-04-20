package com.xuecheng.ucenter.model.dto;

import com.xuecheng.ucenter.model.po.XcUser;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @description 用户扩展信息
 * @author lxh11111
 * @version 1.0
 */
@Data
public class XcUserExt extends XcUser {
    //用户权限
    List<String> permissions = new ArrayList<>();
}
