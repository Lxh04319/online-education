package com.xuecheng.content.service.impl;

import com.alibaba.fastjson.JSON;
import com.xuecheng.base.exception.CommonError;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.content.config.MultipartSupportConfig;
import com.xuecheng.content.feignclient.MediaServiceClient;
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
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
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

    @Autowired
    MediaServiceClient mediaServiceClient;

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

    //生成静态文件
    @Override
    public File generateCourseHtml(Long courseId) {

        Configuration configuration = new Configuration(Configuration.getVersion());
        //最终的静态文件
        File htmlFile = null;
        try {
            //拿到classpath路径
            String classpath = this.getClass().getResource("/").getPath();
            //指定模板的目录
            configuration.setDirectoryForTemplateLoading(new File(classpath+"/templates/"));
            //指定编码
            configuration.setDefaultEncoding("utf-8");

            //得到模板
            Template template = configuration.getTemplate("course_template.ftl");
            //准备数据
            CoursePreviewDto coursePreviewInfo = this.getCoursePreviewInfo(courseId);
            HashMap<String, Object> map = new HashMap<>();
            map.put("model",coursePreviewInfo);

            //Template template 模板, Object model 数据
            String html = FreeMarkerTemplateUtils.processTemplateIntoString(template, map);
            //输入流
            InputStream inputStream = IOUtils.toInputStream(html, "utf-8");
            htmlFile = File.createTempFile("coursepublish",".html");
            //输出文件
            FileOutputStream outputStream = new FileOutputStream(htmlFile);
            //使用流将html写入文件
            IOUtils.copy(inputStream,outputStream);
        }catch (Exception ex){
            log.error("页面静态化出现问题,课程id:{}",courseId,ex);
            ex.printStackTrace();
        }
        return htmlFile;
    }

    //html上传至minio
    @Override
    public void uploadCourseHtml(Long courseId, File file) {
        try {
            //将file转成MultipartFile
            MultipartFile multipartFile = MultipartSupportConfig.getMultipartFile(file);
            //远程调用得到返回值
            String upload = mediaServiceClient.upload(multipartFile, "course/"+courseId+".html");
            if(upload==null){
                log.debug("远程调用走降级逻辑得到上传的结果为null,课程id:{}",courseId);
                XueChengPlusException.cast("上传静态文件过程中存在异常");
            }
        }catch (Exception ex){
            ex.printStackTrace();
            XueChengPlusException.cast("上传静态文件过程中存在异常");
        }
    }
}
