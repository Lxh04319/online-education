package com.xuecheng.orders.model.dto;

import com.xuecheng.orders.model.po.XcPayRecord;
import lombok.Data;
import lombok.ToString;

/**
 * @author lxh11111
 * @version 1.0
 * @description 支付记录dto
 */
@Data
@ToString
public class PayRecordDto extends XcPayRecord {

    //二维码
    private String qrcode;

}
