package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.atguigu.tingshu.album.mapper.AlbumAttributeValueMapper;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.AlbumAttributeValueService;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.cache.FgbCache;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.search.client.SearchFeignClient;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.AlbumCategoryVo;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Array;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class AlbumInfoServiceImpl extends ServiceImpl<AlbumInfoMapper, AlbumInfo> implements AlbumInfoService {

	@Autowired
	private AlbumInfoMapper albumInfoMapper;
@Autowired
private AlbumStatMapper albumStatMapper;
@Autowired
private AlbumAttributeValueService albumAttributeValueService;
@Autowired
private AlbumAttributeValueMapper albumAttributeValueMapper;
@Autowired
private SearchFeignClient searchFeignClient;
@Autowired
private RabbitService rabbitService;
	@Autowired
	private UserFeignClient userFeignClient;
	@Autowired
	private TrackInfoMapper trackInfoMapper;
@Transactional(rollbackFor = Exception.class)
	@Override
	public void saveAlbumInfo(AlbumInfoVo albumInfoVo, Long userId) {
     //1.业务校验，用validation框架校验
	//2.封装AlbumInfo对象
		AlbumInfo albumInfo = BeanUtil.copyProperties(albumInfoVo, AlbumInfo.class);
		albumInfo.setUserId(userId);
		albumInfo.setEstimatedTrackCount(55);
		albumInfo.setAlbumRichIntro("好影评");
		albumInfo.setStatus(SystemConstant.ALBUM_STATUS_PASS);
		albumInfoMapper.insert(albumInfo);
		Long AlbumInfoId =albumInfo.getId();
		//3.2.批量封装AlbumAttributeValue对象

		ArrayList<AlbumAttributeValue> albumAttributeValues = new ArrayList<>();
		for (int i = 0; i < albumInfo.getAlbumAttributeValueVoList().size(); i++) {
			AlbumAttributeValue albumAttributeValue = new AlbumAttributeValue();
			albumAttributeValue.setAlbumId(Long.valueOf(AlbumInfoId));
			albumAttributeValue.setAttributeId(albumInfo.getAlbumAttributeValueVoList().get(i).getAttributeId());
			albumAttributeValue.setValueId(albumInfo.getAlbumAttributeValueVoList().get(i).getValueId());
			albumAttributeValues.add(albumAttributeValue);
		}
        albumAttributeValueService.saveBatch(albumAttributeValues);

		//3.插入AlbumStat对象
		AlbumStat albumStat = new AlbumStat();
		albumStatMapper.insert(new AlbumStat(Long.valueOf(AlbumInfoId),SystemConstant.ALBUM_STAT_PLAY,0));
		albumStatMapper.insert(new AlbumStat(Long.valueOf(AlbumInfoId),SystemConstant.ALBUM_STAT_SUBSCRIBE,0));
		albumStatMapper.insert(new AlbumStat(Long.valueOf(AlbumInfoId),SystemConstant.ALBUM_STAT_BUY,0));
		albumStatMapper.insert(new AlbumStat(Long.valueOf(AlbumInfoId),SystemConstant.ALBUM_STAT_COMMENT,0));

		//4.todo 审核专辑的文本内容是否合规
	//5.插入专辑到全文搜索引擎（全文搜索引擎）
	rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_UPPER,AlbumInfoId);

	}

	@Override
	public Page<AlbumListVo> findUserAlbumPage(Page<AlbumListVo> pageParam, AlbumInfoQuery albumInfoQuery) {
      Page<AlbumListVo> iPage = albumInfoMapper.findUserAlbumPage(pageParam, albumInfoQuery);
		return iPage;
	}
	@Transactional(rollbackFor = Exception.class)
	@Override
	public void removeAlbumInfo(Long id) {
		//1.先删标签关联表AlbumAttributeValue
		//业务校验
		AlbumAttributeValue albumAttributeValue =
				albumAttributeValueMapper.selectOne(new LambdaQueryWrapper<AlbumAttributeValue>().eq(AlbumAttributeValue::getAlbumId, id));
		if (albumAttributeValue == null) {
			throw new IllegalArgumentException("专辑标签不存在");
		}
		albumAttributeValueService.remove(new LambdaQueryWrapper<AlbumAttributeValue>().eq(AlbumAttributeValue::getAlbumId, id));
		//2.删专辑状态表AlbumStat
		List<AlbumStat> albumStats = albumStatMapper.selectList(new LambdaQueryWrapper<AlbumStat>().eq(AlbumStat::getAlbumId, id));
		if (CollectionUtil.isEmpty(albumStats)) {
			throw new IllegalArgumentException("专辑状态不存在");
		}
		albumStatMapper.delete(new LambdaQueryWrapper<AlbumStat>().eq(AlbumStat::getAlbumId, id));
		//3.删专辑表AlbumInfo
		albumInfoMapper.deleteById(id);
		//5.TODO 基于MQ删除存在Elasticsearch（全文搜索引擎）中数据
		rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_LOWER, id);
	}

	@Override
	@FgbCache(prefix = RedisConstant.ALBUM_INFO_PREFIX)
	public AlbumInfo getAlbumInfo(Long id) {
		AlbumInfo albumInfo = albumInfoMapper.selectById(id);
		List<AlbumAttributeValue> albumAttributeValues =
				albumAttributeValueMapper.selectList(new LambdaQueryWrapper<AlbumAttributeValue>(AlbumAttributeValue.class).eq(AlbumAttributeValue::getAlbumId, id));
		albumInfo.setAlbumAttributeValueVoList(albumAttributeValues);
		return albumInfo;
	}

	@Override
	public void updateAlbumInfo(Long id, AlbumInfoVo albumInfoVo) {
		//1.更新专辑表AlbumInfo
		AlbumInfo albumInfo = BeanUtil.copyProperties(albumInfoVo, AlbumInfo.class);
		Long userId = AuthContextHolder.getUserId();
		albumInfo.setUserId(userId);
		albumInfo.setId(id);
		albumInfo.setEstimatedTrackCount(55);
		albumInfo.setAlbumRichIntro("好影评");
		albumInfo.setStatus(SystemConstant.ALBUM_STATUS_PASS);
		albumInfoMapper.updateById(albumInfo);

		//2.更新专辑标签关联表AlbumAttributeValue
		//2.1.删除旧标签
		albumAttributeValueService.remove(new LambdaQueryWrapper<AlbumAttributeValue>().eq(AlbumAttributeValue::getAlbumId, id));
		//2.2.插入新标签
		ArrayList<AlbumAttributeValue> albumAttributeValues = new ArrayList<>();
		for (int i = 0; i < albumInfo.getAlbumAttributeValueVoList().size(); i++) {
			AlbumAttributeValue albumAttributeValue = new AlbumAttributeValue();
			albumAttributeValue.setAlbumId(id);
			albumAttributeValue.setAttributeId(albumInfo.getAlbumAttributeValueVoList().get(i).getAttributeId());
			albumAttributeValue.setValueId(albumInfo.getAlbumAttributeValueVoList().get(i).getValueId());
			albumAttributeValues.add(albumAttributeValue);
		}
		albumAttributeValueService.saveBatch(albumAttributeValues);
		//3.TODO 再次对内容进行审核
		//4.更新全文搜索引擎（全文搜索引擎）
		rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_UPPER, id);
	}

	/**
	 * 获取当前用户全部专辑列表
	 * @param userId
	 * @return
	 */
	@Override
	public List<AlbumInfo> getUserAllAlbumList(Long userId) {
		//1.构建查询条件QueryWrapper对象
		LambdaQueryWrapper<AlbumInfo> queryWrapper = new LambdaQueryWrapper<>();
		//1.1 查询条件
		queryWrapper.eq(AlbumInfo::getUserId, userId);
		//1.2 排序
		queryWrapper.orderByDesc(AlbumInfo::getId);
		//1.3 限制记录数
		queryWrapper.last("LIMIT 200");
		//1.4 指定查询列
		queryWrapper.select(AlbumInfo::getId, AlbumInfo::getAlbumTitle);
		//2.执行列表查询
		return albumInfoMapper.selectList(queryWrapper);
	}


	/**
	 * 根据专辑ID查询专辑统计信息
	 * @param albumId
	 * @return
	 */
	@Override
	@FgbCache(prefix = RedisConstant.ALBUM_INFO_PREFIX+":stat:")
	public AlbumStatVo getAlbumStatVo(Long albumId) {
		return albumInfoMapper.getAlbumStatVo(albumId);
	}

	/**
	 * 基于当前用户未购买声音数量动态渲染分集购买列表
	 *
	 * @param userId  用户ID
	 * @param trackId 选择购买声音ID，作为起始标准计算未购买声音数量
	 * @return [{name:"本集",price:0.1,trackCount:1},{name:"后10集",price:1,trackCount:10}..,{name:"全集",price:3.8,trackCount:38}]
	 */

	@Override
	public List<Map<String, Object>> findUserTrackPaidList(Long userId, Long trackId) {
		//1.根据选择购买声音ID查询声音记录，得到声音序号，得到专辑ID
		TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
		Integer orderNum = trackInfo.getOrderNum();
		Long albumId = trackInfo.getAlbumId();
		//2.根据"起始声音"需要+专辑ID查询声音表得到“未购买声音列表”，这里可能包含已购买声音
		List<TrackInfo> waitBuyTrackList = trackInfoMapper.selectList(
				new LambdaQueryWrapper<TrackInfo>()
						.eq(TrackInfo::getAlbumId, albumId)
						.ge(TrackInfo::getOrderNum, orderNum)
						.select(TrackInfo::getId)
		);

		//3.远程调用用户服务，得到用户已购声音ID列表，如果有值将已购买声音ID排除掉
		List<Long> userPaidTrackIdList = userFeignClient.findUserPaidTrackList(albumId).getData();
		if (CollUtil.isNotEmpty(userPaidTrackIdList)) {
			waitBuyTrackList =
					waitBuyTrackList.stream()
							.filter(t -> !userPaidTrackIdList.contains(t.getId()))
							.collect(Collectors.toList());
		}

		//4.基于未购买声音数量，动态封装分集购买列表
		int size = waitBuyTrackList.size();
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		//4.0 查询专辑记录得到单集价格
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		BigDecimal price = albumInfo.getPrice();
		///4.1 创建"本集"分集购买对象
		Map<String, Object> currMap = new HashMap<>();
		currMap.put("name", "本集");
		currMap.put("price", price);
		currMap.put("trackCount", 1);
		list.add(currMap);
		//4.2 创建"后i*10集"分集购买对象
		for (int i = 10; i < 50; i += 10) {
			Map<String, Object> map = new HashMap<>();
			if (i < size) {
				map.put("name", "后" + i + "集");
				map.put("price", price.multiply(new BigDecimal(i)));
				map.put("trackCount", i);
				list.add(map);
			} else {
				map.put("name", "全集(后" + size + "集)");
				map.put("price", price.multiply(new BigDecimal(size)));
				map.put("trackCount", size);
				list.add(map);
				break;
			}
		}
		return list;
	}

}
