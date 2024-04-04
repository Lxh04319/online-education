package com.xuecheng.base.exception;

import java.io.Serializable;

/**
 * @author lxh11111
 * @version 1.0
 */
public class RestErrorResponse implements Serializable {

 private String errMessage;

 public RestErrorResponse(String errMessage){
  this.errMessage= errMessage;
 }

 public String getErrMessage() {
  return errMessage;
 }

 public void setErrMessage(String errMessage) {
  this.errMessage = errMessage;
 }
}
