package com.atguigu.tingshu.user.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.jwt.JWTUtil;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.cache.FgbCache;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.entity.GuiguCorrelationData;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.*;
import com.atguigu.tingshu.user.mapper.*;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

	@Autowired
	private UserInfoMapper userInfoMapper;
    @Autowired
	private WxMaService wxMaService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RabbitService rabbitService;
    @Autowired
    private UserPaidAlbumMapper userPaidAlbumMapper;
    @Autowired
    private UserPaidTrackMapper userPaidTrackMapper;
    @Autowired
    private AlbumFeignClient albumFeignClient;

    @Autowired
    private UserVipServiceMapper userVipServiceMapper;

    @Autowired
    private VipServiceConfigMapper vipServiceConfigMapper;
	@Override
	public Map<String, String> wxLogin(String code) {
        try {
            //调用微信微信接口，获取openid
            WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
            //获取openid
            String openid = sessionInfo.getOpenid();
            //根据openid查询用户信息
            UserInfo userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getWxOpenId,openid));
            if (userInfo == null) {
                //用户不存在，创建用户
                userInfo = new UserInfo();
                userInfo.setWxOpenId(openid);
                userInfo.setNickname(IdUtil.nanoId());
                userInfo.setAvatarUrl("http://192.168.200.6:9000/tingshu/2026/10/09/383b094b93474a3d928b79ced6071e73.png");
                userInfoMapper.insert(userInfo);
                // 异步初始化用户的账号信息
                HashMap<String, Object> map = new HashMap<>();
                map.put("user_id",userInfo.getId());
                map.put("title","初始化用户账号赠送的积分");
                map.put("amount",new BigDecimal(10000.00));
                map.put("order_no",UUID.randomUUID().toString().replaceAll("-",""));
                //发送消息到队列
                rabbitService.sendMessage(MqConst.EXCHANGE_USER, MqConst.ROUTING_USER_REGISTER, map);
            }
            //将用户信息存入redis
            UserInfoVo userInfoVo = BeanUtil.copyProperties(userInfo, UserInfoVo.class);
            String token = UUID.randomUUID().toString().replaceAll("-","");
            String key= RedisConstant.USER_LOGIN_KEY_PREFIX+token;
            redisTemplate.opsForValue().set(key, userInfoVo, 7, TimeUnit.DAYS);
            //将用户信息返回给前端
            return Map.of("token",token);
        } catch (WxErrorException e) {
            log.error("调用微信微信接口，获取openid失败", code, e);
            throw new RuntimeException(e);
        }

	}

    @Override
    @FgbCache(prefix = "user:")
    public UserInfoVo getUserInfo(Long userId) {
        UserInfo userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getId, userId));
        if (userInfo == null) {
            return null;
        }
        UserInfoVo userInfoVo = BeanUtil.copyProperties(userInfo, UserInfoVo.class);
               return userInfoVo;
    }

    @Override
    public void updateUser(UserInfoVo userInfoVo) {
        //1.获取用户ID
        Long userId = AuthContextHolder.getUserId();
        //2.更新用户昵称跟头像
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setAvatarUrl(userInfoVo.getAvatarUrl());
        userInfo.setNickname(userInfoVo.getNickname());
        userInfoMapper.updateById(userInfo);
    }

    /**
     * 提交需要检查购买状态声音ID列表，响应每个声音购买状态
     *
     * @param userId                        用户ID
     * @param albumId                       专辑ID
     * @param needCheckPayStatusTrackIdList 待检查购买状态声音ID列表
     * @return
     */
    @Override
    public Map<Long, Integer> userIsPaidTrack(Long userId, Long albumId, List<Long> needCheckPayStatusTrackIdList) {
        Map<Long, Integer> map = new HashMap<>();
        //1.根据用户ID+专辑ID查询专辑购买记录
        Long count = userPaidAlbumMapper.selectCount(
                new LambdaQueryWrapper<UserPaidAlbum>()
                        .eq(UserPaidAlbum::getUserId, userId)
                        .eq(UserPaidAlbum::getAlbumId, albumId)
        );

        //2. 如果已购买专辑，将所有待检查购买状态声音 购买状态设置为 1 响应
        if (count > 0) {
            for (Long trackId : needCheckPayStatusTrackIdList) {
                map.put(trackId, 1);
            }
            return map;
        }

        //3. 根据用户ID+专辑ID查询已购声音记录
        List<UserPaidTrack> userPaidTrackList = userPaidTrackMapper.selectList(
                new LambdaQueryWrapper<UserPaidTrack>()
                        .eq(UserPaidTrack::getUserId, userId)
                        .eq(UserPaidTrack::getAlbumId, albumId)
                        .select(UserPaidTrack::getTrackId)
        );

        //4. 如果不存再已购声音，将所有待检查购买状态声音 购买状态设置为 0 响应
        if (CollUtil.isEmpty(userPaidTrackList)) {
            for (Long trackId : needCheckPayStatusTrackIdList) {
                map.put(trackId, 0);
            }
            return map;
        }


        //5.如果存在已购声音，将提交检查声音ID列表中，已购声购买状态设置为：1。未购买设置为0
        List<Long> userPaidTrackIdList = userPaidTrackList.stream().map(UserPaidTrack::getTrackId).collect(Collectors.toList());
        for (Long trackId : needCheckPayStatusTrackIdList) {
            if (userPaidTrackIdList.contains(trackId)) {
                map.put(trackId, 1);
            } else {
                map.put(trackId, 0);
            }
        }
        return map;
    }

    @Override
    public Boolean isPaidAlbum(Long userId, Long albumId) {
        UserPaidAlbum userPaidAlbum = userPaidAlbumMapper.selectOne(new LambdaQueryWrapper<UserPaidAlbum>().
                eq(UserPaidAlbum::getAlbumId,albumId)
                .eq(UserPaidAlbum::getUserId,userId));
        if (userPaidAlbum == null) {
            return false;
        }
        return true;
    }

    /**
     * 查询当前用户某个专辑下已购声音ID列表
     *
     * @param albumId
     * @return
     */
    @Override
    public List<Long> findUserPaidTrackList(Long userId, Long albumId) {
        List<UserPaidTrack> userPaidTrackList = userPaidTrackMapper.selectList(
                new LambdaQueryWrapper<UserPaidTrack>()
                        .eq(UserPaidTrack::getUserId, userId)
                        .eq(UserPaidTrack::getAlbumId, albumId)
                        .select(UserPaidTrack::getTrackId)
        );
        if (CollUtil.isNotEmpty(userPaidTrackList)) {
            List<Long> userPaidTrackIdList = userPaidTrackList.stream()
                    .map(UserPaidTrack::getTrackId)
                    .collect(Collectors.toList());
            return userPaidTrackIdList;
        }
        return null;
    }
    /**
     * 用户支付成功后，虚拟物品发货
     *
     * @param userPaidRecordVo
     * @return
     */
    /**
     * 用户支付成功后，虚拟物品发货
     *
     * @param userPaidRecordVo
     * @return
     */
    @Override
    public void savePaidRecord(UserPaidRecordVo userPaidRecordVo) {
        //TODO 策略模式+工厂模式优化
        //付款项目类型: 1001-专辑 1002-声音 1003-vip会员
        String itemType = userPaidRecordVo.getItemType();
        //1.处理虚拟物品"专辑"发货逻辑
        if (SystemConstant.ORDER_ITEM_TYPE_ALBUM.equals(itemType)) {
            //1.1 根据订单编号查询已购专辑记录，如果存在，则返回
            Long count = userPaidAlbumMapper.selectCount(
                    new LambdaQueryWrapper<UserPaidAlbum>()
                            .eq(UserPaidAlbum::getOrderNo, userPaidRecordVo.getOrderNo())
            );
            if (count > 0) {
                log.info("已存在该订单:{}的已购专辑记录", userPaidRecordVo.getOrderNo());
                return;
            }
            //1.2 如果不存在，则构建专辑已购记录完成保存
            UserPaidAlbum userPaidAlbum = new UserPaidAlbum();
            userPaidAlbum.setOrderNo(userPaidRecordVo.getOrderNo());
            userPaidAlbum.setUserId(userPaidRecordVo.getUserId());
            userPaidAlbum.setAlbumId(userPaidRecordVo.getItemIdList().get(0));
            userPaidAlbumMapper.insert(userPaidAlbum);
        } else if (SystemConstant.ORDER_ITEM_TYPE_TRACK.equals(itemType)) {
            //2. 处理虚拟物品"声音"发货逻辑
            //2.1 根据订单编号查询已购声音记录，如果存在，则返回
            Long count = userPaidTrackMapper.selectCount(
                    new LambdaQueryWrapper<UserPaidTrack>()
                            .eq(UserPaidTrack::getOrderNo, userPaidRecordVo.getOrderNo())
            );
            if (count > 0) {
                log.info("已存在该订单:{}的已购声音记录", userPaidRecordVo.getOrderNo());
                return;
            }
            //2.2 如果不存在，则构建声音已购记录完成保存
            TrackInfo trackInfo = albumFeignClient.getTrackInfo(userPaidRecordVo.getItemIdList().get(0)).getData();
            List<Long> trackIdList = userPaidRecordVo.getItemIdList();
            for (Long trackId : trackIdList) {
                UserPaidTrack userPaidTrack = new UserPaidTrack();
                userPaidTrack.setOrderNo(userPaidRecordVo.getOrderNo());
                userPaidTrack.setUserId(userPaidRecordVo.getUserId());
                userPaidTrack.setAlbumId(trackInfo.getAlbumId());
                userPaidTrack.setTrackId(trackId);
                userPaidTrackMapper.insert(userPaidTrack);
            }
        } else if (SystemConstant.ORDER_ITEM_TYPE_VIP.equals(itemType)) {
            //3. 处理虚拟物品"会员"发货逻辑
            //3.1 根据订单编号查询已购会员记录，如果存在，则返回
            Long count = userVipServiceMapper.selectCount(
                    new LambdaQueryWrapper<UserVipService>()
                            .eq(UserVipService::getOrderNo, userPaidRecordVo.getOrderNo())
            );
            if (count > 0) {
                return;
            }
            //3.2 判断当前用户是否为会员
            Boolean isVIP = false;
            UserInfoVo userInfoVo = this.getUserInfo(userPaidRecordVo.getUserId());
            Date now = new Date();
            if (userInfoVo.getIsVip().intValue() == 1 && userInfoVo.getVipExpireTime().after(now)) {
                isVIP = true;
            }
            //3.3 新增会员购买记录
            UserVipService userVipService = new UserVipService();
            userVipService.setOrderNo(userPaidRecordVo.getOrderNo());
            userVipService.setUserId(userPaidRecordVo.getUserId());
            //3.3.1 获取会员套餐信息得到服务月数
            VipServiceConfig vipServiceConfig = vipServiceConfigMapper.selectById(userPaidRecordVo.getItemIdList().get(0));
            Integer serviceMonth = vipServiceConfig.getServiceMonth();
            //3.3.2 计算本次会员购买记录生效时间
            //3.3.3 计算本次会员购买记录失效时间
            if (!isVIP) {
                //普通用户，生效时间为当前时间 失效时间=当前时间+购买会员套餐月数
                userVipService.setStartTime(now);
                DateTime expireTime = DateUtil.offsetMonth(now, serviceMonth);
                userVipService.setExpireTime(expireTime);
            } else {
                //会员用户，生效时间为=会员的到期时间+1 失效时间=会员的到期时间+购买会员套餐月数
                Date vipExpireTime = userInfoVo.getVipExpireTime();  //6.14
                userVipService.setStartTime(DateUtil.offsetDay(vipExpireTime, 1)); //6.15
                userVipService.setExpireTime(DateUtil.offsetMonth(userVipService.getStartTime(), serviceMonth)); //7.15
            }
            userVipServiceMapper.insert(userVipService);
            //3.4 更新用户会员标识以及更新会员过期时间
            userInfoVo.setIsVip(1);
            userInfoVo.setVipExpireTime(userVipService.getExpireTime());
            UserInfo userInfo = BeanUtil.copyProperties(userInfoVo, UserInfo.class);
            userInfoMapper.updateById(userInfo);
        }
    }
}
