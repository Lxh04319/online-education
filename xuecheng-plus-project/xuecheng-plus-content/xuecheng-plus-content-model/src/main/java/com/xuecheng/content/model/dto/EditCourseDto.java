package com.xuecheng.content.model.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author lxh11111
 * @version 1.0
 * @description TODO
 */
@Data
public class EditCourseDto extends AddCourseDto {

 @ApiModelProperty(value = "课程id", required = true)
 private Long id;

}
