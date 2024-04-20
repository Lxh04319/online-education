package com.xuecheng.orders.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.utils.IdWorkerUtils;
import com.xuecheng.base.utils.QRCodeUtil;
import com.xuecheng.messagesdk.model.po.MqMessage;
import com.xuecheng.messagesdk.service.MqMessageService;
import com.xuecheng.orders.config.AlipayConfig;
import com.xuecheng.orders.config.PayNotifyConfig;
import com.xuecheng.orders.mapper.XcOrdersGoodsMapper;
import com.xuecheng.orders.mapper.XcOrdersMapper;
import com.xuecheng.orders.mapper.XcPayRecordMapper;
import com.xuecheng.orders.model.dto.AddOrderDto;
import com.xuecheng.orders.model.dto.PayRecordDto;
import com.xuecheng.orders.model.dto.PayStatusDto;
import com.xuecheng.orders.model.po.XcOrders;
import com.xuecheng.orders.model.po.XcOrdersGoods;
import com.xuecheng.orders.model.po.XcPayRecord;
import com.xuecheng.orders.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author lxh11111
 * @version 1.0
 * @description 订单相关的接口
 */
@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    XcOrdersMapper ordersMapper;

    @Autowired
    XcOrdersGoodsMapper ordersGoodsMapper;

    @Autowired
    MqMessageService mqMessageService;

    @Autowired
    XcPayRecordMapper payRecordMapper;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    OrderServiceImpl currentProxy;

    @Value("${pay.qrcodeurl}")
    String qrcodeurl;

    @Value("${pay.alipay.APP_ID}")
    String APP_ID;
    @Value("${pay.alipay.APP_PRIVATE_KEY}")
    String APP_PRIVATE_KEY;

    @Value("${pay.alipay.ALIPAY_PUBLIC_KEY}")
    String ALIPAY_PUBLIC_KEY;

    @Transactional
    @Override
    public PayRecordDto createOrder(String userId, AddOrderDto addOrderDto) {
        //订单表
        XcOrders xcOrders=saveXcOrders(userId,addOrderDto);
        //插入支付
        XcPayRecord payRecord=createPayRecord(xcOrders);
        Long payno=payRecord.getPayNo();
        //二维码
        QRCodeUtil qrCodeUtil=new QRCodeUtil();
        //二维码url
        String url=String.format(qrcodeurl,payno);
        //二维码图片
        String qrcode=null;
        try{
            qrcode=qrCodeUtil.createQRCode(url,200,200);

        } catch (IOException e) {
            XueChengPlusException.cast("生成二维码失败");
        }
        PayRecordDto payRecordDto=new PayRecordDto();
        BeanUtils.copyProperties(payRecord,payRecordDto);
        return payRecordDto;
    }

    @Override
    public XcPayRecord getPayRecordByPayno(String payNo) {
        XcPayRecord xcPayRecord = payRecordMapper.selectOne(new LambdaQueryWrapper<XcPayRecord>().eq(XcPayRecord::getPayNo, payNo));
        return xcPayRecord;
    }

    @Override
    public PayRecordDto queryPayResult(String payNo) {
        //支付结果
        PayStatusDto payStatusDto=queryPayResultFromAlipay(payNo);
        //更新支付记录表和订单表
        currentProxy.saveAliPayStatus(payStatusDto);
        //返回支付记录信息
        XcPayRecord payRecord=getPayRecordByPayno(payNo);
        PayRecordDto payRecordDto = new PayRecordDto();
        BeanUtils.copyProperties(payRecord,payRecordDto);
        return payRecordDto;
    }

    /**
     * 请求支付宝查询支付结果
     * @param payNo 支付交易号
     * @return 支付结果
     */
    public PayStatusDto queryPayResultFromAlipay(String payNo){
        AlipayClient alipayClient=new DefaultAlipayClient(AlipayConfig.URL,APP_ID,APP_PRIVATE_KEY, AlipayConfig.FORMAT, AlipayConfig.CHARSET, ALIPAY_PUBLIC_KEY,AlipayConfig.SIGNTYPE);
        AlipayTradeQueryRequest request=new AlipayTradeQueryRequest();
        JSONObject content=new JSONObject();
        request.setBizContent(content.toString());
        //支付宝返回信息
        String body=null;
        try{
            AlipayTradeQueryResponse response=alipayClient.execute(request);
            if(!response.isSuccess()){
                XueChengPlusException.cast("查询支付结果失败");
            }
            body=response.getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
            XueChengPlusException.cast("请求支付查询支付结果异常");
        }
        //结果转成map 提取
        Map bodyMap = JSON.parseObject(body, Map.class);
        Map alipay_trade_query_response = (Map) bodyMap.get("alipay_trade_query_response");
        //提取
        String trade_no = (String) alipay_trade_query_response.get("trade_no");
        String trade_status = (String) alipay_trade_query_response.get("trade_status");
        String total_amount = (String) alipay_trade_query_response.get("total_amount");
        PayStatusDto payStatusDto = new PayStatusDto();
        payStatusDto.setOut_trade_no(payNo);
        payStatusDto.setTrade_no(trade_no);//支付宝的交易号
        payStatusDto.setTrade_status(trade_status);//交易状态
        payStatusDto.setApp_id(APP_ID);
        payStatusDto.setTotal_amount(total_amount);//总金额
        return payStatusDto;
    }

    /**
     * @description 保存支付宝支付结果
     * @param payStatusDto  支付结果信息 从支付宝查询到的信息
     * @return void
     */
    @Transactional
    public void saveAliPayStatus(PayStatusDto payStatusDto){
        //支付号
        String payNo=payStatusDto.getOut_trade_no();
        XcPayRecord payRecord=getPayRecordByPayno(payNo);
        if(payRecord==null){
            XueChengPlusException.cast("找不到相关的支付记录");
        }
        //订单id
        Long orderId= payRecord.getOrderId();
        XcOrders xcOrders=ordersMapper.selectById(orderId);
        if(xcOrders==null){
            XueChengPlusException.cast("找不到相关联的订单");
        }
        //支付状态
        String status= payRecord.getStatus();
        if(status.equals("601002")){
            return;
        }
        //支付成功
        String trade_status = payStatusDto.getTrade_status();//从支付宝查询到的支付结果
        if(trade_status.equals("TRADE_SUCCESS")){//支付宝返回的信息为支付成功
            //更新支付记录表的状态为支付成功
            payRecord.setStatus("601002");
            //支付宝的订单号
            payRecord.setOutPayNo(payStatusDto.getTrade_no());
            //第三方支付渠道编号
            payRecord.setOutPayChannel("Alipay");
            //支付成功时间
            payRecord.setPaySuccessTime(LocalDateTime.now());
            payRecordMapper.updateById(payRecord);
            //更新订单表的状态为支付成功
            xcOrders.setStatus("600002");//订单状态为交易成功
            ordersMapper.updateById(xcOrders);
            //将消息写到数据库
            MqMessage mqMessage = mqMessageService.addMessage("payresult_notify", xcOrders.getOutBusinessId(), xcOrders.getOrderType(), null);
            //发送消息
            notifyPayResult(mqMessage);
        }
    }

    @Override
    public void notifyPayResult(MqMessage message) {
        String jsonString=JSON.toJSONString(message);
        //持久化消息
        Message message1=MessageBuilder.withBody(jsonString.getBytes(StandardCharsets.UTF_8)).setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
        Long id=message.getId();
        //全局消息id
        CorrelationData correlationData = new CorrelationData(id.toString());
        //使用correlationData指定回调方法
        correlationData.getFuture().addCallback(result-> {
            if (result.isAck()) {
                //消息成功发送到了交换机
                log.debug("发送消息成功:{}", jsonString);
                //将消息从数据库表mq_message删除
                mqMessageService.completed(id);

            } else {
                //消息发送失败
                log.debug("发送消息失败:{}", jsonString);
            }
        },ex->{
            //发生异常了
            log.debug("发送消息异常:{}",jsonString);
        });
        //发送消息
        rabbitTemplate.convertAndSend(PayNotifyConfig.PAYNOTIFY_EXCHANGE_FANOUT,"",message1,correlationData);
    }

    /**
     * 保存支付记录
     * @param orders
     * @return
     */
    public XcPayRecord createPayRecord(XcOrders orders){
        Long orderId=orders.getId();
        XcOrders xcOrders=ordersMapper.selectById(orderId);
        if(xcOrders==null){
            XueChengPlusException.cast("订单不存在");
        }
        String status=xcOrders.getStatus();
        if(status.equals("601002")){
            XueChengPlusException.cast("此订单已支付");
        }
        //添加支付记录
        XcPayRecord xcPayRecord = new XcPayRecord();
        xcPayRecord.setPayNo(IdWorkerUtils.getInstance().nextId());//支付记录号，将来要传给支付宝
        xcPayRecord.setOrderId(orderId);
        xcPayRecord.setOrderName(xcOrders.getOrderName());
        xcPayRecord.setTotalPrice(xcOrders.getTotalPrice());
        xcPayRecord.setCurrency("CNY");
        xcPayRecord.setCreateDate(LocalDateTime.now());
        xcPayRecord.setStatus("601001");//未支付
        xcPayRecord.setUserId(xcOrders.getUserId());
        int insert=payRecordMapper.insert(xcPayRecord);
        if(insert<=0){
            XueChengPlusException.cast("插入支付记录失败");
        }
        return xcPayRecord;
    }

    /**
     * 保存订单信息
     * @param userId
     * @param addOrderDto
     * @return
     */
    public XcOrders saveXcOrders(String userId, AddOrderDto addOrderDto){
        XcOrders xcOrders=getOrderByBusinessId(addOrderDto.getOutBusinessId());
        if(xcOrders!=null){
            return xcOrders;
        }
        //订单主表
        xcOrders=new XcOrders();
        //雪花算法生成订单号
        xcOrders.setId(IdWorkerUtils.getInstance().nextId());
        xcOrders.setTotalPrice(addOrderDto.getTotalPrice());
        xcOrders.setCreateDate(LocalDateTime.now());
        xcOrders.setStatus("600001");//未支付
        xcOrders.setUserId(userId);
        xcOrders.setOrderType("60201");//订单类型
        xcOrders.setOrderName(addOrderDto.getOrderName());
        xcOrders.setOrderDescrip(addOrderDto.getOrderDescrip());
        xcOrders.setOrderDetail(addOrderDto.getOrderDetail());
        xcOrders.setOutBusinessId(addOrderDto.getOutBusinessId());//如果是选课这里记录选课表的id
        int insert = ordersMapper.insert(xcOrders);
        if(insert<=0){
            XueChengPlusException.cast("添加订单失败");
        }
        Long orderId=xcOrders.getId();
        //插入订单明细表 json转list
        String orderDetailJson = addOrderDto.getOrderDetail();
        List<XcOrdersGoods> xcOrdersGoods = JSON.parseArray(orderDetailJson, XcOrdersGoods.class);
        //遍历插入
        xcOrdersGoods.forEach(good->{
            good.setOrderId(orderId);
            int insertp=ordersGoodsMapper.insert(good);
        });
        return xcOrders;
    }


    /**
     * 根据业务id查询订单 ,业务id是选课记录表中的主键
     * @param businessId
     * @return
     */
    public XcOrders getOrderByBusinessId(String businessId) {
        XcOrders orders = ordersMapper.selectOne(new LambdaQueryWrapper<XcOrders>().eq(XcOrders::getOutBusinessId, businessId));
        return orders;
    }
}
