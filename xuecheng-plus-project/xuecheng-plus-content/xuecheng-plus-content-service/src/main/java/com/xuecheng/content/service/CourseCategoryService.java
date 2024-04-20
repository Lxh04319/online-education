package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.CourseCategoryTreeDto;

import java.util.List;

/**
 * @author lxh11111
 * @version 1.0
 * @description TODO
 */
public interface CourseCategoryService {
 /**
  * 课程分类树形结构查询
  * @return
  */
 public List<CourseCategoryTreeDto> queryTreeNodes(String id);
}
