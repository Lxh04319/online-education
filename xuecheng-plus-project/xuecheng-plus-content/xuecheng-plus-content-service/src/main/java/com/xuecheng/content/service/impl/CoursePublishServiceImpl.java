package com.xuecheng.content.service.impl;

import com.alibaba.fastjson.JSON;
import com.xuecheng.base.exception.CommonError;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.mapper.CourseBaseMapper;
import com.xuecheng.content.mapper.CourseMarketMapper;
import com.xuecheng.content.mapper.CoursePublishMapper;
import com.xuecheng.content.mapper.CoursePublishPreMapper;
import com.xuecheng.content.model.dto.CourseBaseInfoDto;
import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.CourseBase;
import com.xuecheng.content.model.po.CourseMarket;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.content.model.po.CoursePublishPre;
import com.xuecheng.content.service.CourseBaseInfoService;
import com.xuecheng.content.service.CoursePublishService;
import com.xuecheng.content.service.TeachplanService;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Mr.M
 * @version 1.0
 * @description 课程发布相关接口实现
 * @date 2023/2/21 10:04
 */
@Slf4j
@Service
public class CoursePublishServiceImpl implements CoursePublishService {

    @Autowired
    CourseBaseInfoService courseBaseInfoService;

    @Autowired
    TeachplanService teachplanService;

    @Autowired
    CourseBaseMapper courseBaseMapper;

     @Autowired
    CourseMarketMapper courseMarketMapper;

     @Autowired
     CoursePublishPreMapper coursePublishPreMapper;

     @Autowired
     CoursePublishMapper coursePublishMapper;

    @Autowired
    MqMessageService mqMessageService;

    @Override
    public CoursePreviewDto getCoursePreviewInfo(Long courseId) {
        CoursePreviewDto coursePreviewDto=new CoursePreviewDto();
        //课程信息
        CourseBaseInfoDto courseBaseInfo=courseBaseInfoService.getCourseBaseInfo(courseId);
        coursePreviewDto.setCourseBase(courseBaseInfo);
        //课程计划
        List<TeachplanDto> teachplanDtos=teachplanService.findTeachplanTree(courseId);
        coursePreviewDto.setTeachplans(teachplanDtos);
        return coursePreviewDto;
    }

    @Override
    public void commitAudit(Long companyId, Long courseId) {
        CourseBaseInfoDto courseBaseInfoDto=courseBaseInfoService.getCourseBaseInfo(courseId);
        if(courseBaseInfoDto==null){
            XueChengPlusException.cast("等待审核");
        }
        String auditStatus=courseBaseInfoDto.getStatus();
        if(auditStatus.equals("202003")){
            XueChengPlusException.cast("已提交");
        }
        String pic=courseBaseInfoDto.getPic();
        if(StringUtils.isEmpty(pic)){
            XueChengPlusException.cast("上传图片");
        }
        //计划
        List<TeachplanDto> teachplanDtos=teachplanService.findTeachplanTree(courseId);
        if(teachplanDtos==null||teachplanDtos.size()==0){
            XueChengPlusException.cast("请写课程计划");
        }
        //插入到预发布表
        CoursePublishPre coursePublishPre=new CoursePublishPre();
        BeanUtils.copyProperties(courseBaseInfoDto,coursePublishPre);
        //设置id
        coursePublishPre.setCompanyId(companyId);
        //营销信息
        CourseMarket courseMarket=courseMarketMapper.selectById(courseId);
        //转json
        String courseMarketJson=JSON.toJSONString(courseMarket);
        coursePublishPre.setTeachplan(courseMarketJson);
        //计划信息
        String teachplanJson=JSON.toJSONString(teachplanDtos);
        coursePublishPre.setTeachplan(teachplanJson);
        //状态改提交
        coursePublishPre.setStatus("202003");
        //提交时间
        coursePublishPre.setCreateDate(LocalDateTime.now());
        //查询预发布 有则更新 没有就插入
        CoursePublishPre coursePublishPre1=coursePublishPreMapper.selectById(courseId);
        if(coursePublishPre1==null){
            coursePublishPreMapper.insert(coursePublishPre);
        }else{
            coursePublishPreMapper.updateById(coursePublishPre);
        }
    }

    @Override
    public void publish(Long companyId, Long courseId) {
        //查询预发布表
        CoursePublishPre coursePublishPre=coursePublishPreMapper.selectById(courseId);
        if(coursePublishPre==null){
            XueChengPlusException.cast("未审核");
        }
        //状态
        String status= coursePublishPre.getStatus();
        //是否通过
        if(!status.equals("202004")){
            XueChengPlusException.cast("审核不通过");
        }
        //写入
        CoursePublish coursePublish=new CoursePublish();
        BeanUtils.copyProperties(coursePublishPre,coursePublish);
        //先查询
        CoursePublish coursePublish1=coursePublishMapper.selectById(courseId);
        if(coursePublish1==null){
            coursePublishMapper.insert(coursePublish);
        }else{
            coursePublishMapper.updateById(coursePublish);
        }
        //消息表写入数据
        saveCoursePublishMessage(courseId);
        //预发布表删除
        coursePublishPreMapper.deleteById(courseId);
    }

    //保存消息表记录
    private void saveCoursePublishMessage(Long courseId){
        MqMessage mqMessage=mqMessageService.addMessage("course_publish",String.valueOf(courseId),null,null);
        if(mqMessage==null){
            XueChengPlusException.cast(CommonError.UNKOWN_ERROR);
        }
    }
}
