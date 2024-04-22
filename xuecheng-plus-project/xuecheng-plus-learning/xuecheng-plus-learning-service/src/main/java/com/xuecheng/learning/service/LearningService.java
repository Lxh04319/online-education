package com.xuecheng.learning.service;

import com.xuecheng.base.model.RestResponse;

/**
 * @author lxh11111
 * @version 1.0
 * @description 在线学习相关的接口
 */
public interface LearningService {

    /**
     * @description 获取教学视频
     */
    public RestResponse<String> getVideo(String userId, Long courseId, Long teachplanId, String mediaId);

}
