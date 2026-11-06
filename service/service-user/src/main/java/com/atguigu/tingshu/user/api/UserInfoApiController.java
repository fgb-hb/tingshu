package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.Fgb;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理接口")
@RestController
@RequestMapping("api/user")
@SuppressWarnings({"all"})
public class UserInfoApiController {

	@Autowired
	private UserInfoService userInfoService;
	/**
	 * 根据用户ID查询用户/主播基本信息
	 * @param userId
	 * @return
	 */
	@Operation(summary = "根据用户ID查询用户/主播基本信息")
	@GetMapping("/userInfo/getUserInfoVo/{userId}")
	public Result<UserInfoVo> getUserInfoVo(@PathVariable Long userId){
		UserInfoVo userInfoVo = userInfoService.getUserInfo(userId);
		return Result.ok(userInfoVo);
	}
	/**
	 * 提交需要检查购买状态声音ID列表，响应每个声音购买状态
	 * @param userId
	 * @param albumId
	 * @param needCheckPayStatusTrackIdList 待检查购买状态声音ID列表
	 * @return
	 */
	@Operation(summary = "提交需要检查购买状态声音ID列表，响应每个声音购买状态")
	@PostMapping("/userInfo/userIsPaidTrack/{userId}/{albumId}")
	public Result<Map<Long, Integer>> userIsPaidTrack(
			@PathVariable Long userId,
			@PathVariable Long albumId,
			@RequestBody List<Long> needCheckPayStatusTrackIdList
	) {
		Map<Long, Integer> map = userInfoService.userIsPaidTrack(userId, albumId, needCheckPayStatusTrackIdList);
		return Result.ok(map);
	}

	/**
	 * 判断当前用户是否购买过指定专辑
	 *
	 * @param albumId
	 * @return
	 */
	@Fgb
	@Operation(summary = "判断当前用户是否购买过指定专辑")
	@GetMapping("/userInfo/isPaidAlbum/{albumId}")
	public Result<Boolean> isPaidAlbum(@PathVariable Long albumId) {
		Long userId = AuthContextHolder.getUserId();
		Boolean isPaidAlbum = userInfoService.isPaidAlbum(userId, albumId);
		return Result.ok(isPaidAlbum);
	}

	/**
	 * 查询当前用户某个专辑下已购声音ID列表
	 * @param albumId
	 * @return
	 */
	@Fgb
	@Operation(summary = "查询当前用户某个专辑下已购声音ID列表")
	@GetMapping("/userInfo/findUserPaidTrackList/{albumId}")
	public Result<List<Long>> findUserPaidTrackList(@PathVariable Long albumId) {
		Long userId = AuthContextHolder.getUserId();
		List<Long> list = userInfoService.findUserPaidTrackList(userId, albumId);
		return Result.ok(list);
	}
	/**
	 * 用户支付成功后，虚拟物品发货
	 * @param userPaidRecordVo
	 * @return
	 */
	@Operation(summary = "用户支付成功后，虚拟物品发货")
	@PostMapping("/userInfo/savePaidRecord")
	public Result savePaidRecord(@RequestBody UserPaidRecordVo userPaidRecordVo) {
		userInfoService.savePaidRecord(userPaidRecordVo);
		return Result.ok();
	}

}

