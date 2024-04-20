package com.xuecheng.content.feignclient;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author lxh11111
 * @version 1.0
 * @description TODO
 */
@FeignClient(value = "search",fallbackFactory = SearchServiceClientFallbackFactory.class)
public interface SearchServiceClient {

 @PostMapping("/search/index/course")
 public Boolean add(@RequestBody CourseIndex courseIndex);
}
