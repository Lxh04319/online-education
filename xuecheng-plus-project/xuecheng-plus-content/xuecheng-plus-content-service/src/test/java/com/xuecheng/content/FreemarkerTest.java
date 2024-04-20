package com.xuecheng.content;

import com.xuecheng.content.model.dto.CoursePreviewDto;
import com.xuecheng.content.service.CoursePublishService;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

import java.io.*;
import java.util.HashMap;

/**
 * @author lxh11111
 * @version 1.0
 * @description 测试freemarker页面静态化方法
 */
@SpringBootTest
public class FreemarkerTest {

    @Autowired
    CoursePublishService coursePublishService;

    @Test
    public void testGenerateHtmlByTemplate() throws IOException, TemplateException {
        Configuration configuration=new Configuration(Configuration.getVersion());
        //路径
        String classpath=this.getClass().getResource("/").getPath();
        //指定目录
        configuration.setDirectoryForTemplateLoading(new File(classpath+"/templates"));
        //指定编码
        configuration.setDefaultEncoding("utf-8");
        //得到模板
        Template template=configuration.getTemplate("course_template.ftl");
        //准备数据
        CoursePreviewDto coursePreviewDto=coursePublishService.getCoursePreviewInfo(120L);
        HashMap<String,Object> map=new HashMap<>();
        map.put("model",coursePreviewDto);
        String html=FreeMarkerTemplateUtils.processTemplateIntoString(template,map);
        //输入流
        InputStream inputStream=IOUtils.toInputStream(html,"utf-8");
        //输出
        FileOutputStream fileOutputStream=new FileOutputStream(new File("D:\\javacode\\120.html"));
        //写入
        IOUtils.copy(inputStream,fileOutputStream);
    }
}
