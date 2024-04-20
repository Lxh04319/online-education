package com.xuecheng.content.service.jobhandler;

import com.sun.org.apache.xpath.internal.operations.Bool;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.feignclient.CourseIndex;
import com.xuecheng.content.feignclient.SearchServiceClient;
import com.xuecheng.content.mapper.CoursePublishMapper;
import com.xuecheng.content.model.po.CoursePublish;
import com.xuecheng.content.service.CoursePublishService;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MessageProcessAbstract;
import com.xuecheng.messagesdk.service.MqMessageService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author lxh11111
 * @version 1.0
 * @description 课程发布的任务类
 */
@Slf4j
@Component
public class CoursePublishTask extends MessageProcessAbstract {

    @Autowired
    CoursePublishService coursePublishService;

    @Autowired
    SearchServiceClient searchServiceClient;

    @Autowired
    CoursePublishMapper coursePublishMapper;
    //任务调度入口
    @XxlJob("CoursePublishJobHandler")
    public void coursePublishJobHandler() throws Exception {
        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();//执行器的序号，从0开始
        int shardTotal = XxlJobHelper.getShardTotal();//执行器总数
        //调用抽象类的方法执行任务
        process(shardIndex,shardTotal, "course_publish",30,60);
    }

    //执行课程发布任务的逻辑,如果此方法抛出异常说明任务执行失败
    @Override
    public boolean execute(MqMessage mqMessage) {
        //mq拿到id
        Long courseId=Long.parseLong(mqMessage.getBusinessKey1());
        //静态化上传到minio
        generateCourseHtml(mqMessage,courseId);
        //es写入索引
        saveCourseIndex(mqMessage,courseId);
        //redis写入缓存
        //完成
        return true;
    }
    //生成课程静态化页面并上传至minio
    private void generateCourseHtml(MqMessage mqMessage,long courseId){
        Long taskId=mqMessage.getId();
        MqMessageService mqMessageService=this.getMqMessageService();
        //幂等性处理
        int stageOne=mqMessageService.getStageOne(taskId);
        if(stageOne>0){
            //已经完成
            return;
        }
        //静态化
        File file = coursePublishService.generateCourseHtml(courseId);
        if(file == null){
            XueChengPlusException.cast("生成的静态页面为空");
        }
        // 将html上传到minio
        coursePublishService.uploadCourseHtml(courseId,file);
        //完成
        mqMessageService.completedStageOne(taskId);
    }

    //保存课程索引信息 第二个阶段任务
    private void saveCourseIndex(MqMessage mqMessage,long courseId){
        Long taskId=mqMessage.getId();
        MqMessageService mqMessageService=this.getMqMessageService();
        int stageTwo=mqMessageService.getStageTwo(taskId);
        if(stageTwo>0){
            return;
        }
        //添加索引
        CoursePublish coursePublish=coursePublishMapper.selectById(courseId);
        CourseIndex courseIndex=new CourseIndex();
        BeanUtils.copyProperties(coursePublish,courseIndex);
        //远程feign
        Boolean add =searchServiceClient.add(courseIndex);
        if(!add){
            XueChengPlusException.cast("失败");
        }
        //完成
        mqMessageService.completedStageTwo(taskId);
    }
}
