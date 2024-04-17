package com.xuecheng.content.feignclient;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @author Mr.M
 * @version 1.0
 * @description TODO
 */
public class MediaServiceClientFallback implements MediaServiceClient{
 @Override
 public String upload(MultipartFile filedata, String objectName) throws IOException {

  return null;
 }
}
