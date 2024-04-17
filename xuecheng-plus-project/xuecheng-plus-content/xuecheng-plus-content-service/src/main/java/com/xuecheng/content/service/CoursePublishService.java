package com.xuecheng.content.service;

import com.xuecheng.content.model.dto.CoursePreviewDto;

import java.io.File;

/**
 * @author Mr.M
 * @version 1.0
 * @description 课程发布相关的接口
 * @date 2023/2/21 10:03
 */
public interface CoursePublishService {


 /**
  * @description 获取课程预览信息
  * @param courseId 课程id
  * @return com.xuecheng.content.model.dto.CoursePreviewDto
  * @author Mr.M
  */
 public CoursePreviewDto getCoursePreviewInfo(Long courseId);

 /**
  * @description 提交审核
  * @param courseId  课程id
  * @return void
  * @author Mr.M
  * @date 2022/9/18 10:31
  */
 public void commitAudit(Long companyId,Long courseId);

 /**
  * @description 课程发布接口
  * @param companyId 机构id
  * @param courseId 课程id
  * @return void
  * @author Mr.M
  */
 public void publish(Long companyId,Long courseId);

 /**
  * @description 课程静态化
  * @param courseId  课程id
  * @return File 静态化文件
  * @author Mr.M
  */
 public File generateCourseHtml(Long courseId);
 /**
  * @description 上传课程静态化页面
  * @param file  静态化文件
  * @return void
  * @author Mr.M
  */
 public void  uploadCourseHtml(Long courseId, File file);
}
