package com.atguigu.tingshu.user.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.util.MongoUtil;
import com.atguigu.tingshu.model.user.UserListenProcess;
import com.atguigu.tingshu.user.service.UserListenProcessService;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@SuppressWarnings({"all"})
public class UserListenProcessServiceImpl implements UserListenProcessService {

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private RedisTemplate redisTemplate;

	@Autowired
	private RabbitService rabbitService;

	/**
	 * 从MongoDB查询，根据用户ID+声音ID查询上次播放秒数
	 *
	 * @param userId
	 * @param trackId
	 * @return
	 */
	@Override
	public BigDecimal getTrackBreakSecond(Long userId, Long trackId) {
		//1.根据用户ID确定操作集合名称 形式为：固定前缀_用户ID=userListenProcess_userId
		String collectionName = this.getCollectionName(userId);
		//2.查询条件：用户ID+声音ID
		Query query = new Query();
		query.addCriteria(Criteria.where("userId").is(userId).and("trackId").is(trackId));
		//3.执行查询MongoDB
		UserListenProcess listenProcess = mongoTemplate.findOne(query, UserListenProcess.class, collectionName);
		if (listenProcess != null) {
			return listenProcess.getBreakSecond();
		}
		return BigDecimal.valueOf(0);
	}

	/**
	 * 更新声音播放进度
	 *
	 * @param userListenProcessVo
	 * @return
	 */
	@Override
	public void updateUserListenProcess(Long userId, UserListenProcessVo userListenProcessVo) {
		//1.获取用户播放进度集合名称
		String collectionName = this.getCollectionName(userId);

		//2.根据用户ID+声音ID查询用户播放进度
		Query query = new Query(
				Criteria.where("userId").is(userId)
						.and("trackId").is(userListenProcessVo.getTrackId())
		);
		UserListenProcess userListenProcess = mongoTemplate.findOne(query, UserListenProcess.class, collectionName);

		//3.如果播放进度不存在，构建播放进度，保存到MongoDB
		//设置播放秒数小数位，舍入模式
		userListenProcessVo.setBreakSecond(userListenProcessVo.getBreakSecond().setScale(0, RoundingMode.DOWN));
		if (userListenProcess == null) {
			userListenProcess = new UserListenProcess();
			userListenProcess.setUserId(userId);
			userListenProcess.setAlbumId(userListenProcessVo.getAlbumId());
			userListenProcess.setTrackId(userListenProcessVo.getTrackId());
			userListenProcess.setBreakSecond(userListenProcessVo.getBreakSecond());
			userListenProcess.setCreateTime(new Date());
			userListenProcess.setUpdateTime(new Date());
			//mongoTemplate.save(userListenProcess, collectionName);
		} else {
			//4.如果播放进度存在，更新播放秒+更新时间
			userListenProcess.setBreakSecond(userListenProcessVo.getBreakSecond());
			userListenProcess.setUpdateTime(new Date());
			//mongoTemplate.save(userListenProcess, collectionName);
		}
		mongoTemplate.save(userListenProcess, collectionName);

		//5.TODO 基于MQ可靠性消息，更新声音/专辑(播放量)在MySQL以及ES中统计数值
		//5.1 当天内判断当前用户是否为第一次收听更新统计数值 核心：生产者生产消息幂等性
		//5.1.1 关键点找出业务标识作为RedisKey：前缀:用户ID_专辑ID_声音ID
		String redisKey
				= RedisConstant.USER_TRACK_REPEAT_STAT_PREFIX + userId + "_" + userListenProcessVo.getAlbumId() + "_" + userListenProcessVo.getTrackId();
		//5.1.2 采用Redis提供set k v ex nx  命令，设置RedisKey，设置过期时间，写入成功则发送MQ消息，更新统计数值；写入失败则忽略
		Date date = new Date();
		long ttl = DateUtil.endOfDay(date).getTime() - date.getTime();
		Boolean flag = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", ttl, TimeUnit.MILLISECONDS);
		//5.2 发送MQ消息，广播消息（通知"专辑服务"更新DB，通知"搜索服务"更新ES）
		if (flag) {
			//5.2.1 构建MQ消息VO对象 包含：专辑ID、声音ID、统计类型、增量数值、businessNo-UUID（用于消费者端幂等性处理），TODO VO必须序列化接口
			TrackStatMqVo mqVo = new TrackStatMqVo();
			mqVo.setBusinessNo(IdUtil.fastUUID());
			mqVo.setAlbumId(userListenProcessVo.getAlbumId());
			mqVo.setTrackId(userListenProcessVo.getTrackId());
			mqVo.setStatType(SystemConstant.TRACK_STAT_PLAY);
			mqVo.setCount(1);
			//5.2.2 发送MQ消息
			rabbitService.sendMessage(MqConst.EXCHANGE_TRACK, MqConst.ROUTING_TRACK_STAT_UPDATE, mqVo);
		}
	}
	/**
	 * 获取当前用户最近播放声音
	 *
	 * @return {albumId:1,trackId:12}
	 */
	@Override
	public Map<String, Long> getLatelyTrack(Long userId) {
		//1.查询当前用户某个声音播放进度
		//1.1 构建查询条件
		Query query = new Query();
		query.addCriteria(Criteria.where("userId").is(userId));
		query.with(PageRequest.of(0, 1, Sort.Direction.DESC, "updateTime"));
		//1.2 执行查询：注意每个用户都有自己播放进度集合
		String collectionName = this.getCollectionName(userId);
		UserListenProcess listenProcess = mongoTemplate.findOne(query, UserListenProcess.class, collectionName);
		if (listenProcess != null) {
			Map<String, Long> map = new HashMap<>();
			map.put("albumId", listenProcess.getAlbumId());
			map.put("trackId", listenProcess.getTrackId());
			return map;
		}
		return null;
	}


	@Override
	public String getCollectionName(Long userId) {
		return "userListenProcess_" + userId;
	}

}
