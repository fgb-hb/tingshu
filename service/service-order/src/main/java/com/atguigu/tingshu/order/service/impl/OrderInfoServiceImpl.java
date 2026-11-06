package com.atguigu.tingshu.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.account.AccountFeignClient;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.order.OrderDerate;
import com.atguigu.tingshu.model.order.OrderDetail;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.order.helper.SignHelper;
import com.atguigu.tingshu.order.mapper.OrderDerateMapper;
import com.atguigu.tingshu.order.mapper.OrderDetailMapper;
import com.atguigu.tingshu.order.mapper.OrderInfoMapper;
import com.atguigu.tingshu.order.service.OrderDetailService;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.account.AccountDeductVo;
import com.atguigu.tingshu.vo.order.OrderDerateVo;
import com.atguigu.tingshu.vo.order.OrderDetailVo;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zaxxer.hikari.pool.ProxyConnection;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.redisson.RedissonScript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private AlbumFeignClient albumFeignClient;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private OrderDerateMapper orderDerateMapper;
    @Autowired
    private OrderDetailService orderDetailService;
    @Autowired
    private AccountFeignClient accountFeignClient;

    /**
     * 三种商品（VIP会员、专辑、声音）订单结算,渲染订单结算页面
     *
     * @param tradeVo (购买项目类型、购买项目ID、声音数量)
     * @return 订单VO信息
     */
    @Override
    public OrderInfoVo trade(TradeVo tradeVo) {
        if (tradeVo == null) {
            return null;
        }
        System.out.println("进入订单结算===========================================tradeVo" + tradeVo);
        //1.像创建一个订单
        OrderInfoVo orderInfoVo = new OrderInfoVo();
        //1.2 声明封装订单明细集合
        List<OrderDetailVo> orderDetailVoList = new ArrayList<>();
        //1.3 声明封装订单减免明细集合
        List<OrderDerateVo> orderDerateVoList = new ArrayList<>();
        //订单金额
        BigDecimal orderAmount = new BigDecimal("0.00");
        //减免金额
        BigDecimal derateAmount = new BigDecimal("0.00");
        //原始金额
        BigDecimal originAmount = new BigDecimal("0.00");
        Long userId = AuthContextHolder.getUserId();
        //2.判断商品类型
        String itemType = tradeVo.getItemType();
        if (SystemConstant.ORDER_ITEM_TYPE_VIP.equals(itemType)){
            //2.1Vip会员订单结算
            //2.2调用远程服务获取要购买的VIP套餐
            VipServiceConfig vipServiceConfig = userFeignClient.getVipServiceConfig(tradeVo.getItemId()).getData();
            if (vipServiceConfig == null){
                throw new IllegalArgumentException("VIP套餐不存在");
            }
            //2.3计算订单金额和减免金额,封装到订单详情
        originAmount=vipServiceConfig.getPrice();
        orderAmount=vipServiceConfig.getDiscountPrice();
        if (originAmount.compareTo(orderAmount)==1){
            derateAmount=originAmount.subtract(orderAmount);
            //2.4封装订单减免明细
            OrderDerateVo orderDerateVo = new OrderDerateVo();
            orderDerateVo.setDerateType(SystemConstant.ORDER_DERATE_VIP_SERVICE_DISCOUNT);
            orderDerateVo.setDerateAmount(derateAmount);
            orderDerateVo.setRemarks("套餐限时减免：" + derateAmount);
            orderDerateVoList.add(orderDerateVo);
        }
        //2.5封装订单明细
            OrderDetailVo orderDetailVo = new OrderDetailVo();
            orderDetailVo.setItemId(tradeVo.getItemId());
            orderDetailVo.setItemName("会员套餐：" + vipServiceConfig.getName());
            orderDetailVo.setItemUrl(vipServiceConfig.getImageUrl());
            orderDetailVo.setItemPrice(originAmount);
            orderDetailVoList.add(orderDetailVo);

        }else if (SystemConstant.ORDER_ITEM_TYPE_ALBUM.equals(itemType)) {
            //3. 处理购买项目类型：专辑
            //3.1 远程调用用户服务，判断用户是否已购买过专辑，如果已买则响应已购买
            Long albumId = tradeVo.getItemId();
            Boolean flag = userFeignClient.isPaidAlbum(albumId).getData();
            if (flag) {
                throw new GuiguException(500, "用户已购买专辑");
            }

            //3.2 远程调用专辑服务，获取专辑价格以及相关折扣(普通、VIP折扣)
            AlbumInfo albumInfo = albumFeignClient.getAlbumInfo(albumId).getData();
            Assert.notNull(albumInfo, "专辑{}不存在", albumId);
            BigDecimal price = albumInfo.getPrice();
            BigDecimal discount = albumInfo.getDiscount();
            BigDecimal vipDiscount = albumInfo.getVipDiscount();

            //3.3 远程调用用户服务，判断当前用户是否是会员
            Boolean isVIP = false;

            UserInfoVo userInfoVo = userFeignClient.getUserInfoVo(userId).getData();
            Assert.notNull(userInfoVo, "用户{}不存在", userId);
            if (userInfoVo.getIsVip().intValue() == 1 && userInfoVo.getVipExpireTime().after(new Date())) {
                isVIP = true;
            }

            //3.4 基于用户角色专辑折扣计算专辑价格(原价、订单价、减免价格)
            originAmount = price;
            orderAmount = originAmount;
            if (!isVIP && discount.doubleValue() != -1) {
                //专辑价格 普通用户折扣  订单价格=原价*折扣
                orderAmount =
                        originAmount.multiply(discount).divide(new BigDecimal("10"), 2, RoundingMode.HALF_UP);
            }
            if (isVIP && vipDiscount.doubleValue() != -1) {
                //专辑价格 VIP会员折扣  订单价格=原价*折扣
                orderAmount =
                        originAmount.multiply(vipDiscount).divide(new BigDecimal("10"), 2, RoundingMode.HALF_UP);
            }
            //计算专辑价格减免金额 = 原价-订单价格
            derateAmount = originAmount.subtract(orderAmount);
            //3.5 将专辑信息作为订单明细，封装成订单明细VO对象，加入到订单明细列表
            OrderDetailVo orderDetailVo = new OrderDetailVo();
            orderDetailVo.setItemId(albumId);
            orderDetailVo.setItemName("专辑：" + albumInfo.getAlbumTitle());
            orderDetailVo.setItemUrl(albumInfo.getCoverUrl());
            orderDetailVo.setItemPrice(originAmount);
            orderDetailVoList.add(orderDetailVo);

            //3.6 如有优惠金额，封装订单减免vo对象，加入到订单减免列表
            if (originAmount.compareTo(orderAmount) == 1) {
                OrderDerateVo orderDerateVo = new OrderDerateVo();
                orderDerateVo.setDerateType(SystemConstant.ORDER_DERATE_ALBUM_DISCOUNT);
                orderDerateVo.setDerateAmount(derateAmount);
                orderDerateVo.setRemarks("专辑折扣减免：" + derateAmount);
                orderDerateVoList.add(orderDerateVo);
            }
        } else if (SystemConstant.ORDER_ITEM_TYPE_TRACK.equals(itemType)) {
            //4.处理购买项目类型：声音
            //4.1 远程调用专辑服务获取待购买声音列表
            Long trackId = tradeVo.getItemId();
            List<TrackInfo> trackInfoList = albumFeignClient.findWaitBuyTrackInfoList(trackId, tradeVo.getTrackCount()).getData();
            Assert.notNull(trackInfoList, "没有需要结算声音");
            //4.2 远程调用专辑服务获取声音单价-声音不支持折扣
            AlbumInfo albumInfo = albumFeignClient.getAlbumInfo(trackInfoList.get(0).getAlbumId()).getData();
            Assert.notNull(albumInfo, "专辑{}不存在", trackInfoList.get(0).getAlbumId());
            BigDecimal price = albumInfo.getPrice();

            //4.3 封装相关价格
            originAmount = price.multiply(new BigDecimal(trackInfoList.size()));
            orderAmount = originAmount;

            //4.4 将待购买声音作为订单明细，封装成订单明细VO对象，加入到订单明细列表
            orderDetailVoList = trackInfoList
                    .stream()
                    .map(trackInfo -> {
                        OrderDetailVo orderDetailVo = new OrderDetailVo();
                        orderDetailVo.setItemId(trackInfo.getId());
                        orderDetailVo.setItemName("声音：" + trackInfo.getTrackTitle());
                        orderDetailVo.setItemUrl(trackInfo.getCoverUrl());
                        orderDetailVo.setItemPrice(price);
                        return orderDetailVo;
                    }).collect(Collectors.toList());

        }
        //5.封装订单VO信息共有的信息，价格赋值、商品明细列表赋值、订单减免明细列表赋值、流水号机制、签名机制
        //5.1 封装第一类信息：三个价格（原价、订单价、减免价）
        orderInfoVo.setOriginalAmount(originAmount);
        orderInfoVo.setOrderAmount(orderAmount);
        orderInfoVo.setDerateAmount(derateAmount);

        //5.2 封装第二类信息：商品明细列表、优惠列表
        orderInfoVo.setOrderDetailVoList(orderDetailVoList);
        orderInfoVo.setOrderDerateVoList(orderDerateVoList);

        //5.3 封装第三类信息：购买项目类型、流水号机制（本次订单流水号）、签名机制（时间戳+签名值）
        //5.3.1 购买项目类型
        orderInfoVo.setItemType(itemType);
        //5.3.2 流水号机制第一步：生成本次订单流水号，解决回退、重复点击提交造成订单重复提交问题
        //5.3.2.1. 构建当前用户本次订单流水号Key
        String tradeKey = RedisConstant.ORDER_TRADE_NO_PREFIX + userId;
        //5.3.2.2. 构建当前用户本次订单流水号UUID值
        String tradeNo = IdUtil.randomUUID();
        //5.3.2.3. 写入Redis有效时间5分钟
        redisTemplate.opsForValue().set(tradeKey, tradeNo, 5, TimeUnit.MINUTES);
        orderInfoVo.setTradeNo(tradeNo);

        //5.3.3 签名机制
        //5.3.3.1. 生成本次订单时间戳
        long timeMillis = System.currentTimeMillis();
        orderInfoVo.setTimestamp(timeMillis);
        //5.3.3.2 将订单VO转为Map，生成本次订单签名值 TODO 由于付款方式属性payWay为null不需要参与签名
        Map<String, Object> orderInfoMap = BeanUtil.beanToMap(orderInfoVo, false, true);
        String sign = SignHelper.getSign(orderInfoMap);
        //5.3.3.3. 封装Vo中签名值
        orderInfoVo.setSign(sign);
        //6.响应订单vo，客户端完成订单页面渲染
        return orderInfoVo;
    }
    /**
     * 提交订单,支持付款方式（微信、余额）
     *
     * @param orderInfoVo 订单vo
     * @return {orderNo:"订单唯一编号"}
     */
    @Override
   @GlobalTransactional(rollbackFor = Exception.class)
    public Map<String, String> submitOrder(Long userId, OrderInfoVo orderInfoVo) {
//        Long userId = AuthContextHolder.getUserId();
        //1.业务校验1，验证流水号，防止订单重复提交 采用Lua脚本判断删除原子性
        String tradeKey = RedisConstant.ORDER_TRADE_NO_PREFIX + userId;
        //KEYS[1]=流水号Key ARGV[1]=用户提交流水号值
        String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1]\n" +
                "then\n" +
                "    return redis.call(\"del\",KEYS[1])\n" +
                "else\n" +
                "    return 0\n" +
                "end";
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>(script, Boolean.class);
        Boolean flag = (Boolean) redisTemplate.execute(redisScript, Arrays.asList(tradeKey), orderInfoVo.getTradeNo());
        if (!flag) {
            throw new GuiguException(500, "验证流水号失败");
        }

        //2.业务校验2，验证签名,防止订单确认页中订单数据（商品、金额、数量）被篡改
        //2.1 将参与签名订单VO转为Map，TODO 加签时付款方式属性payWay未参与签名
        Map<String, Object> orderInfoMap = BeanUtil.beanToMap(orderInfoVo);
        //2.2 手动删除掉签名属性payWay
        orderInfoMap.remove("payWay");
        //2.3 验签
        SignHelper.checkSign(orderInfoMap);

        //TODO 后续核心业务处理
        //3. 保存订单，返回订单对象 此时订单支付状态：未支付
        OrderInfo orderInfo = this.saveOrderInfo(userId, orderInfoVo);

        //支付方式：1101-微信 1102-支付宝 1103-账户余额
        String payWay = orderInfoVo.getPayWay();
        //4.TODO 处理付款方式为余额
        if (SystemConstant.ORDER_PAY_ACCOUNT.equals(payWay)) {
            //4.1 远程调用"账户服务"，检查扣减余额
            //4.1.1 构建扣减账户余额VO对象AccountDeductVo
            AccountDeductVo accountDeductVo = new AccountDeductVo();
            accountDeductVo.setOrderNo(orderInfo.getOrderNo());
            accountDeductVo.setUserId(orderInfo.getUserId());
            accountDeductVo.setAmount(orderInfo.getOrderAmount());
            accountDeductVo.setContent(orderInfo.getOrderTitle());

            //4.1.2 远程调用
            Result result = accountFeignClient.checkAndDeduct(accountDeductVo);
            //4.1.3 判断远程调用响应业务状态码，如果不是200抛出异常，触发全局事务回滚
            if (result.getCode().intValue() != 200) {
                //4.2 扣减余额失败，业务终止，回滚全局事务
                throw new GuiguException(result.getCode(), result.getMessage());
            }
            //4.3 扣减余额成功，订单状态改为已支付
            orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_PAID);
            orderInfoMapper.updateById(orderInfo);

            //4.4 远程调用"用户服务"，虚拟物品发货（增加购买记录）
            //4.4.1 构建虚拟物品发货VO对象UserPaidRecordVo
            UserPaidRecordVo userPaidRecordVo = new UserPaidRecordVo();
            userPaidRecordVo.setOrderNo(orderInfo.getOrderNo());
            userPaidRecordVo.setUserId(orderInfo.getUserId());
            userPaidRecordVo.setItemType(orderInfo.getItemType());
            List<OrderDetailVo> orderDetailVoList = orderInfoVo.getOrderDetailVoList();
            if (CollUtil.isNotEmpty(orderDetailVoList)) {
                List<Long> itemIdList = orderDetailVoList
                        .stream()
                        .map(OrderDetailVo::getItemId)
                        .collect(Collectors.toList());
                userPaidRecordVo.setItemIdList(itemIdList);
                //4.4.2 远程调用
                result = userFeignClient.savePaidRecord(userPaidRecordVo);
                if (result.getCode().intValue() != 200) {
                    //4.2 虚拟物品发货失败，业务终止，回滚全局事务
                    throw new GuiguException(result.getCode(), result.getMessage());
                }
            }
        }
        //5.处理付款方式为微信
        if (SystemConstant.ORDER_PAY_WAY_WEIXIN.equals(payWay)) {
            //5.1 发送延迟关单消息，自动关闭超时未支付订单
            int delayTime = 60 * 15;
            rabbitService.sendDealyMessage(MqConst.EXCHANGE_CANCEL_ORDER, MqConst.ROUTING_CANCEL_ORDER, orderInfo.getId(), delayTime);
        }
        //x.封装响应结果 如果是微信支付，根据响应订单编号对接微信支付；如果是余额支付成功，返回订单编号，查询订单详情
        Map<String, String> map = new HashMap<>();
        map.put("orderNo", orderInfo.getOrderNo());
        return map;
    }
    /**
        * 保存订单相关信息(订单、订单明细、优惠明细)
 *
         * @param orderInfoVo
 * @param userId
 * @return
         */
    @Override
    public OrderInfo saveOrderInfo(Long userId, OrderInfoVo orderInfoVo) {
        //1.封装订单对象,保存订单
        OrderInfo orderInfo = BeanUtil.copyProperties(orderInfoVo, OrderInfo.class);
        //1.1 设置用户ID
        orderInfo.setUserId(userId);
        //1.2 设置订单标题
        List<OrderDetailVo> orderDetailVoList = orderInfoVo.getOrderDetailVoList();
        if (CollUtil.isNotEmpty(orderDetailVoList)) {
            OrderDetailVo orderDetailVo = orderDetailVoList.get(0);
            orderInfo.setOrderTitle(orderDetailVo.getItemName());
        }
        //1.3 设置订单编号 确保全局唯一且趋势递增 生成策略=当日+雪花算法
        String orderNo = DateUtil.today().replaceAll("-", "") + IdUtil.getSnowflakeNextId();
        orderInfo.setOrderNo(orderNo);
        //1.4 设置订单状态 订单状态：0901-未支付 0902-已支付 0903-已取消
        orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_UNPAID);
        //1.5 保存订单
        orderInfoMapper.insert(orderInfo);
        Long orderId = orderInfo.getId();

        //2.封装订单明细对象,保存订单明细
        if (CollUtil.isNotEmpty(orderDetailVoList)) {
            List<OrderDetail> orderDetailList = orderDetailVoList
                    .stream()
                    .map(orderDetailVo -> {
                        OrderDetail orderDetail = BeanUtil.copyProperties(orderDetailVo, OrderDetail.class);
                        //将订单明细关联订单ID
                        orderDetail.setOrderId(orderId);
                        return orderDetail;
                    }).collect(Collectors.toList());
            orderDetailService.saveBatch(orderDetailList);
        }
        //3.封装订单优惠对象,保存订单优惠明细
        List<OrderDerateVo> orderDerateVoList = orderInfoVo.getOrderDerateVoList();
        if (CollUtil.isNotEmpty(orderDerateVoList)) {
            orderDerateVoList.stream().forEach(orderDerateVo -> {
                OrderDerate orderDerate = BeanUtil.copyProperties(orderDerateVo, OrderDerate.class);
                orderDerate.setOrderId(orderId);
                orderDerateMapper.insert(orderDerate);
            });
        }
        return orderInfo;
    }

    /**
     * 查询并且根据状态取消订单
     *
     * @param orderId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
//        //1.根据订单ID查询订单信息
//        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
//        //2.判读支付状态 ：如果是未支付，则取消订单
//        if (SystemConstant.ORDER_STATUS_UNPAID.equals(orderInfo.getOrderStatus())) {
//            //2.1 设置订单状态为已取消
//            orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_CANCEL);
//            //2.2 更新订单信息
//            orderInfoMapper.updateById(orderInfo);
//        }
        LambdaUpdateWrapper<OrderInfo> orderInfoLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        orderInfoLambdaUpdateWrapper.
                eq(OrderInfo::getId, orderId).
                eq(OrderInfo::getOrderStatus, SystemConstant.ORDER_STATUS_UNPAID)
                .set(OrderInfo::getOrderStatus, SystemConstant.ORDER_STATUS_CANCEL);
        orderInfoMapper.update(null, orderInfoLambdaUpdateWrapper);

    }

    /**
     * 根据订单号查询订单详情
     *
     * @param orderNo 订单号
     * @return 订单信息
     */
    @Override
    public OrderInfo getOrderInfo(String orderNo) {
        //1.根据订单号查询订单信息
        OrderInfo orderInfo = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo));
        if (orderInfo == null) {
            throw new GuiguException(500, "订单不存在");
        }
        //2.根据订单ID查询订单明细列表
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, orderInfo.getId()));
        if (CollUtil.isNotEmpty(orderDetailList)) {
            orderInfo.setOrderDetailList(orderDetailList);
        }
        //3.根据订单ID查询订单减免明细列表
        List<OrderDerate> orderDerateList = orderDerateMapper.selectList(
                new LambdaQueryWrapper<OrderDerate>().eq(OrderDerate::getOrderId, orderInfo.getId()));
        if (CollUtil.isNotEmpty(orderDerateList)) {
            orderInfo.setOrderDerateList(orderDerateList);
        }
        //4.封装订单状态名称、支付方式名称
        orderInfo.setOrderStatusName(getOrderStatusName(orderInfo.getOrderStatus()));
        orderInfo.setPayWayName(getPayWayName(orderInfo.getPayWay()));
        return orderInfo;
    }

    /**
     * 分页查询当前登录用户订单列表（包含订单商品明细）
     *
     * @param pageParam 分页参数
     * @param userId 用户ID
     * @return 分页订单信息
     */
    @Override
    public Page<OrderInfo> findUserPage(Page<OrderInfo> pageParam, Long userId) {
        //1.分页查询当前用户订单列表，按创建时间倒序
        Page<OrderInfo> orderInfoPage = orderInfoMapper.selectPage(pageParam,
                new LambdaQueryWrapper<OrderInfo>()
                        .eq(OrderInfo::getUserId, userId)
                        .orderByDesc(OrderInfo::getCreateTime));
        List<OrderInfo> orderInfoList = orderInfoPage.getRecords();
        if (CollUtil.isEmpty(orderInfoList)) {
            return orderInfoPage;
        }
        //2.收集订单ID列表
        List<Long> orderIdList = orderInfoList.stream()
                .map(OrderInfo::getId)
                .collect(Collectors.toList());
        //3.批量查询订单明细，并按订单ID分组
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().in(OrderDetail::getOrderId, orderIdList));
        Map<Long, List<OrderDetail>> orderDetailMap = orderDetailList.stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));
        //4.将订单明细封装到对应订单
        orderInfoList.forEach(orderInfo ->
                orderInfo.setOrderDetailList(orderDetailMap.get(orderInfo.getId())));
        return orderInfoPage;
    }

    /**
     * 根据订单状态编码获取状态名称
     *
     * @param orderStatus 订单状态编码
     * @return 状态名称
     */
    private String getOrderStatusName(String orderStatus) {
        if (SystemConstant.ORDER_STATUS_UNPAID.equals(orderStatus)) {
            return "未支付";
        }
        if (SystemConstant.ORDER_STATUS_PAID.equals(orderStatus)) {
            return "已支付";
        }
        return "已取消";
    }

    /**
     * 根据支付方式编码获取支付方式名称
     *
     * @param payWay 支付方式编码
     * @return 支付方式名称
     */
    private String getPayWayName(String payWay) {
        if (SystemConstant.ORDER_PAY_WAY_WEIXIN.equals(payWay)) {
            return "微信";
        }
        if (SystemConstant.ORDER_PAY_WAY_ALIPAY.equals(payWay)) {
            return "支付宝";
        }
        return "账户余额";
    }


    /**
     * 用户支付成功后，修改订单状态：已支付
     *
     * @param orderNo
     */
    @Override
    public void orderPaySuccess(String orderNo) {
        //1.根据订单号查询订单信息
        OrderInfo orderInfo = orderInfoMapper.selectOne(
                new LambdaQueryWrapper<OrderInfo>().eq(OrderInfo::getOrderNo, orderNo));
        if (orderInfo == null) {
            throw new GuiguException(500, "订单不存在");
        }
        //2.修改订单状态：已支付
        orderInfoMapper.update(null, new LambdaUpdateWrapper<OrderInfo>()
                .eq(OrderInfo::getId, orderInfo.getId())
                .set(OrderInfo::getOrderStatus, SystemConstant.ORDER_STATUS_UNPAID)
                .set(OrderInfo::getOrderStatus, SystemConstant.ORDER_STATUS_PAID)
                .set(OrderInfo::getUpdateTime, new Date()));
        //3.发放权益
        UserPaidRecordVo userPaidRecordVo = new UserPaidRecordVo();
        userPaidRecordVo.setOrderNo(orderInfo.getOrderNo());
        userPaidRecordVo.setUserId(orderInfo.getUserId());
        userPaidRecordVo.setItemType(orderInfo.getItemType());
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        if (CollUtil.isNotEmpty(orderDetailList)) {
            List<Long> itemIdList = orderDetailList
                    .stream()
                    .map(OrderDetail::getItemId)
                    .collect(Collectors.toList());
            userPaidRecordVo.setItemIdList(itemIdList);
            //4.4.2 远程调用
          Result  result = userFeignClient.savePaidRecord(userPaidRecordVo);
            if (result.getCode().intValue() != 200) {
                //4.2 虚拟物品发货失败，业务终止，回滚全局事务
                throw new GuiguException(result.getCode(), result.getMessage());
            }
        }
    }
}
