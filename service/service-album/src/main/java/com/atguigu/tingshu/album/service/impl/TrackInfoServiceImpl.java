package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.*;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.qcloud.vod.common.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.atguigu.tingshu.common.constant.SystemConstant.*;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class TrackInfoServiceImpl extends ServiceImpl<TrackInfoMapper, TrackInfo> implements TrackInfoService {

	@Autowired
	private TrackInfoMapper trackInfoMapper;
@Autowired
private AlbumInfoMapper albumInfoMapper;
	@Autowired
	private AlbumStatMapper albumStatMapper;

@Autowired
private VodService vodService;
@Autowired
private AuditService auditService;
@Autowired
private TrackStatMapper trackStatMapper;
@Autowired
private UserFeignClient userFeignClient;
	/**
	 * 保存声音
	 *
	 * @param trackInfoVo
	 * @param userId
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveTrackInfo(TrackInfoVo trackInfoVo, Long userId) {
	  //1.封装TrackInfo对象
		TrackInfo trackInfo = BeanUtil.copyProperties(trackInfoVo, TrackInfo.class);
		trackInfo.setUserId(userId);
	//查询专辑表，获取影评对应专辑的当前总声音数
		AlbumInfo albumInfo = albumInfoMapper.selectById(trackInfo.getAlbumId());
		trackInfo.setOrderNum(albumInfo.getIncludeTrackCount() + 1);
    //设置音频的封面
     if (StringUtils.isBlank(trackInfoVo.getCoverUrl())){
		  trackInfo.setCoverUrl(albumInfo.getCoverUrl());
	 }
	 //设置音频相关参数（时长，类型，音频文件大小）
		TrackMediaInfoVo trackMediaInfoVo = vodService.getTrackMediaInfo(trackInfo.getMediaFileId());
	 if (trackMediaInfoVo == null){
		  throw new RuntimeException("查询点播平台文件信息失败");
	 }
		trackInfo.setMediaDuration(BigDecimal.valueOf(trackMediaInfoVo.getDuration()));
		trackInfo.setMediaSize(trackMediaInfoVo.getSize());
		trackInfo.setMediaType(trackMediaInfoVo.getType());
		//设置音频声音来源
        trackInfo.setSource(SystemConstant.TRACK_SOURCE_USER);
//	//2审核文本
//    String text = trackInfoVo.getTrackTitle()+trackInfoVo.getTrackIntro();
//		String s = auditService.auditText(text);
//		if (s==null){
//		  throw new RuntimeException("审核文本失败");
//		}
//		if ("Block".equals(s)){
//			trackInfo.setStatus(SystemConstant.TRACK_STATUS_NO_PASS);
//		} else if ("Review".equals(s)) {
//			trackInfo.setStatus(SystemConstant.TRACK_STATUS_MANUAL);
//		}else if ("Pass".equals(s)) {
//	//审核音频
//			String taskId = auditService.startReviewTask(trackInfoVo.getMediaFileId());
//			if (StringUtils.isBlank(taskId)){
//			 throw new RuntimeException("审核音频失败");
//			}
//			trackInfo.setReviewTaskId(taskId);
//			trackInfo.setStatus(SystemConstant.TRACK_STATUS_REVIEWING);
//		}
		trackInfo.setReviewTaskId("1111111111111111111111111");
			trackInfo.setStatus(SystemConstant.TRACK_STATUS_MANUAL);
    //3.保存声音
		trackInfoMapper.insert(trackInfo);
    //4.更新专辑表，增加当前总声音数
		albumInfo.setIncludeTrackCount(albumInfo.getIncludeTrackCount() + 1);
		albumInfoMapper.updateById(albumInfo);
    //5.保存音频的stat表
		Long trackId = trackInfo.getId();
		trackStatMapper.insert(new TrackStat(trackId, TRACK_STAT_PLAY, 0));
		trackStatMapper.insert(new TrackStat(trackId, SystemConstant.TRACK_STAT_COLLECT, 0));
		trackStatMapper.insert(new TrackStat(trackId, SystemConstant.TRACK_STAT_PRAISE, 0));
		trackStatMapper.insert(new TrackStat(trackId, SystemConstant.TRACK_STAT_COMMENT, 0));
	}

	/**
	 * 条件分页查询当前用户声音列表（包含声音统计信息）
	 *
	 * @param pageInfo 分页对象
	 * @param trackInfoQuery 查询条件（用户ID，关键字，审核状态）
	 * @return
	 */
	@Override
	public Page<TrackListVo> getUserTrackPage(Page<TrackListVo> pageInfo, TrackInfoQuery trackInfoQuery) {
		return trackInfoMapper.getUserTrackPage(pageInfo, trackInfoQuery);
	}

	/**
	 * 修改声音信息
	 *
	 * @param id
	 * @param trackInfo
	 * @return
	 */
	@Override
	public void updateTrackInfo(TrackInfo newTrackInfo) {
		//1.根据ID查询声音信息
		TrackInfo oldTrackInfo = trackInfoMapper.selectById(newTrackInfo.getId());
		if (oldTrackInfo == null){
			throw new RuntimeException("声音不存在");
		}
		//审核文本
//		String text = newTrackInfo.getTrackTitle()+newTrackInfo.getTrackIntro();
//		String s1 = auditService.auditText(text);
//		if (s1==null){
//			throw new RuntimeException("审核文本失败");
//		}
//		if ("Block".equals(s1)){
//			oldTrackInfo.setStatus(SystemConstant.TRACK_STATUS_NO_PASS);
//		} else if ("Review".equals(s1)) {
//			oldTrackInfo.setStatus(SystemConstant.TRACK_STATUS_MANUAL);
//		}else if ("Pass".equals(s1)) {
//			oldTrackInfo.setStatus(SystemConstant.TRACK_STATUS_PASS);
//		}
		if (StringUtils.isNotBlank(newTrackInfo.getTrackTitle())){
			oldTrackInfo.setTrackTitle(newTrackInfo.getTrackTitle());
		}
		if (StringUtils.isNotBlank(newTrackInfo.getTrackIntro())){
			oldTrackInfo.setTrackIntro(newTrackInfo.getTrackIntro());
		}
		//2.判断是否修改了音频文件
		if (!oldTrackInfo.getMediaFileId().equals(newTrackInfo.getMediaFileId())){
			//审核音频
//			String taskId = auditService.startReviewTask(newTrackInfo.getMediaFileId());
//			if (StringUtils.isBlank(taskId)){
//				throw new RuntimeException("审核音频失败");
//			}
//			oldTrackInfo.setReviewTaskId(taskId);
//			oldTrackInfo.setStatus(SystemConstant.TRACK_STATUS_REVIEWING);
			//设置音频相关参数（时长，类型，音频文件大小）
			TrackMediaInfoVo trackMediaInfoVo = vodService.getTrackMediaInfo(newTrackInfo.getMediaFileId());
			if (trackMediaInfoVo == null){
				throw new RuntimeException("查询点播平台文件信息失败");
			}
			oldTrackInfo.setMediaDuration(BigDecimal.valueOf(trackMediaInfoVo.getDuration()));
			oldTrackInfo.setMediaSize(trackMediaInfoVo.getSize());
			oldTrackInfo.setMediaType(trackMediaInfoVo.getType());
			//从点播平台删除旧的音频文件
			vodService.deleteMedia(oldTrackInfo.getMediaFileId());
		}
        //3.判断是否修改了封面
		if (!StringUtils.equals(oldTrackInfo.getCoverUrl(), newTrackInfo.getCoverUrl())){
			oldTrackInfo.setCoverUrl(newTrackInfo.getCoverUrl());
		}
		//4.更新声音信息
		trackInfoMapper.updateById(oldTrackInfo);
	}
	/**
	 * 删除声音记录
	 *
	 * @param id
	 * @return
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeTrackInfo(Long id) {
		//1.根据ID删除音频Sata
      trackStatMapper.deleteById(id);

		//2.根据ID更新专辑表中的声音数
		//根据ID查询声音信息，获取专辑ID
		TrackInfo trackInfo = trackInfoMapper.selectById(id);
		AlbumInfo albumInfo = albumInfoMapper.selectById(trackInfo.getAlbumId());
		albumInfo.setIncludeTrackCount(albumInfo.getIncludeTrackCount() - 1);
		albumInfoMapper.updateById(albumInfo);


		//3.根据ID删除声音信息
		trackInfoMapper.update(null,new LambdaUpdateWrapper<TrackInfo>().
				eq(TrackInfo::getAlbumId, trackInfo.getAlbumId()).
				gt(TrackInfo::getOrderNum, trackInfo.getOrderNum()).
				setSql("order_num = order_num - 1")
				);
		trackInfoMapper.deleteById(id);


		//4.删除点播平台音频文件
		vodService.deleteMedia(trackInfo.getMediaFileId());
		}


	/**
	 * 需求：用户未登录，可以给用户展示声音列表；用户已登录，可以给用户展示声音列表，并动态渲染付费标识
	 * 分页查询专辑下声音列表（动态渲染付费标识）
	 *
	 * @param pageInfo MP分页对象
	 * @param albumId  专辑ID
	 * @param userId   用户ID
	 * @return
	 */
	@Override
	public IPage<AlbumTrackListVo> findAlbumTrackPage(IPage<AlbumTrackListVo> pageInfo, Long albumId, Long userId) {
		//1.分页获取声音列表（包含统计数值） isShowPaidMark默认为false
		pageInfo = trackInfoMapper.findAlbumTrackPage(pageInfo, albumId);

		//动态渲染付费标识
		//2.根据专辑ID查询专辑信息，得到专辑付费类型以及免费试听的集数
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		Assert.notNull(albumInfo, "专辑{}不存在", albumId);
		//2.1 付费类型：0101-免费、0102-vip免费、0103-付费
		String payType = albumInfo.getPayType();
		//2.2 免费实体集数
		Integer tracksForFree = albumInfo.getTracksForFree();
		//3.如果用户未登录 情况一：且专辑付费类型是：VIP免费或付费，将除试听以外其他声音付费标识设置true
		if (userId == null) {
			if (ALBUM_PAY_TYPE_VIPFREE.equals(payType) || ALBUM_PAY_TYPE_REQUIRE.equals(payType)) {
				pageInfo.getRecords()
						.stream()
						.filter(t -> t.getOrderNum() > tracksForFree)
						.forEach(t -> t.setIsShowPaidMark(true));
			}
		} else {
			//4. 如果用户已登录 满足以下两种情况需要进一步获取当前页声音购买状态
			//4.1 远程调用用户服务获取用户身份，判断是否为会员（有效期内会员）
			UserInfoVo userInfoVo = userFeignClient.getUserInfoVo(userId).getData();
			Assert.notNull(userInfoVo, "用户{}不存在", userId);
			Boolean isVIP = false;
			if (userInfoVo.getIsVip().intValue() == 1 && userInfoVo.getVipExpireTime().after(new Date())) {
				isVIP = true;
			}

			//4.2 情况二：如果专辑 付费类型：VIP免费 且 当前用户是普通用户，满足进一步查询当前页声音购买状态
			Boolean isNeedCheckPayStatus = false;
			if (!isVIP && ALBUM_PAY_TYPE_VIPFREE.equals(payType)) {
				isNeedCheckPayStatus = true;
			}
			//4.3 情况三：如果专辑 付费类型：付费，无论什么用户，满足进一步查询当前页声音购买状态
			if (ALBUM_PAY_TYPE_REQUIRE.equals(payType)) {
				isNeedCheckPayStatus = true;
			}

			//4.4 如果满足情况二或情况三，远程调用用户服务，得到当前页除试听部分每个声音购买状态
			if (isNeedCheckPayStatus) {
				//4.4.1 获取当前页中除试听以外其他声音ID列表
				List<Long> needCheckPayStatusTrackIdList = pageInfo
						.getRecords()
						.stream()
						.filter(t -> t.getOrderNum() > tracksForFree)
						.map(AlbumTrackListVo::getTrackId)
						.collect(Collectors.toList());

				//4.4.2 远程调用用户服务，获取当前页中声音购买状态Map<Long-专辑ID,Integer-购买状态>
				Map<Long, Integer> payStatusMap =
						userFeignClient.userIsPaidTrack(userId, albumId, needCheckPayStatusTrackIdList).getData();

				//4.5 根据响应声音购买状态，动态修改付费标识。购买状态为0的声音付费标识isShowPaidMark全部设置：true
				pageInfo
						.getRecords()
						.stream()
						.filter(t -> t.getOrderNum() > tracksForFree)
						.forEach(t -> t.setIsShowPaidMark(payStatusMap.get(t.getTrackId()) == 0));
			}
		}
		return pageInfo;
	}


	/**
	 * 更新DB中声音/专辑统计数值
	 *
	 * @param mqVo
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateTrackStat(TrackStatMqVo mqVo) {
		//1.更新声音统计数值
		trackStatMapper.update(
				null,
				new LambdaUpdateWrapper<TrackStat>()
						.eq(TrackStat::getTrackId, mqVo.getTrackId())
						.eq(TrackStat::getStatType, mqVo.getStatType())
						.setSql("stat_num = stat_num + " + mqVo.getCount())
		);

		//2.如果是"播放"or"评论"还需要更新专辑统计数值
		if (TRACK_STAT_PLAY.equals(mqVo.getStatType())) {
			//更新专辑统计表播放量
			albumStatMapper.update(
					null,
					new LambdaUpdateWrapper<AlbumStat>()
							.eq(AlbumStat::getAlbumId, mqVo.getAlbumId())
							.eq(AlbumStat::getStatType, ALBUM_STAT_PLAY)
							.setSql("stat_num = stat_num + " + mqVo.getCount())
			);
		}
		if (TRACK_STAT_COMMENT.equals(mqVo.getStatType())) {
			//更新专辑统计表评论量
			albumStatMapper.update(
					null,
					new LambdaUpdateWrapper<AlbumStat>()
							.eq(AlbumStat::getAlbumId, mqVo.getAlbumId())
							.eq(AlbumStat::getStatType, ALBUM_STAT_COMMENT)
							.setSql("stat_num = stat_num + " + mqVo.getCount())
			);
		}
	}

	@Override
	public TrackStatVo getTrackStatVo(Long trackId) {
		if (trackId == null) {
			return null;
		}
		TrackStatVo trackStatVo = trackStatMapper.getTrackStatVo(trackId);
		return trackStatVo;
	}

	/**
	 * 查询用户未购买声音列表
	 *
	 * @param trackId    选择购买声音ID，作为起始标准计算未购买声音数量
	 * @param trackCount 限制查询未购买声音数量
	 * @return
	 */
	@Override
	public List<TrackInfo> findWaitBuyTrackInfoList(Long userId, Long trackId, Integer trackCount) {
		//1.根据声音ID查询到“起始”待购声音记录-得到专辑ID及序号
		TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
		Long albumId = trackInfo.getAlbumId();
		Integer orderNum = trackInfo.getOrderNum();

		//2.远程调用用户服务获取已购买声音ID列表
		List<Long> userPaidTrackList = userFeignClient.findUserPaidTrackList(albumId).getData();

		//3.构建查询条件查询未购买声音列表
		LambdaQueryWrapper<TrackInfo> queryWrapper = new LambdaQueryWrapper<TrackInfo>()
				.eq(TrackInfo::getAlbumId, albumId)
				.ge(TrackInfo::getOrderNum, orderNum)
				.orderByAsc(TrackInfo::getOrderNum)
				.select(TrackInfo::getId, TrackInfo::getTrackTitle, TrackInfo::getCoverUrl, TrackInfo::getAlbumId)
				.last("limit " + trackCount);
		if (CollUtil.isNotEmpty(userPaidTrackList)) {
			queryWrapper.notIn(TrackInfo::getId, userPaidTrackList);
		}
		List<TrackInfo> trackInfoList = trackInfoMapper.selectList(queryWrapper);
		return trackInfoList;
	}

}