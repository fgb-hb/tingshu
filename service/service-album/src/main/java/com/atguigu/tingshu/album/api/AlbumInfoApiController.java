package com.atguigu.tingshu.album.api;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.login.Fgb;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumCategoryVo;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.security.Signature;
import java.util.List;
import java.util.Map;

@Tag(name = "专辑管理")
@RestController
@RequestMapping("api/album")
@SuppressWarnings({"all"})
public class AlbumInfoApiController {

	@Autowired
	private AlbumInfoService albumInfoService;


	/**
	 * TODO 该接口登录才可以访问
	 * 内容创作者或者平台运营人员-保存专辑
	 *
	 * @param albumInfoVo 对象中属性需要进行合法验证，采用Validation框架进行校验
	 * @return
	 */
	@Operation(summary = "内容创作者或者平台运营人员-保存专辑")
	@PostMapping("/albumInfo/saveAlbumInfo")
	@Fgb(value = true)
	public Result saveAlbumInfo(@Validated @RequestBody AlbumInfoVo albumInfoVo) {
		//1.动态获取用户ID
		Long userId = AuthContextHolder.getUserId();
		//2.调用业务逻辑完成新增
		albumInfoService.saveAlbumInfo(albumInfoVo, userId);
		return Result.ok();
	}
	/**
	 * TODO 该接口登录才可以访问
	 * 分页条件查询当前登录用户发布专辑
	 *
	 * @param page
	 * @param limit
	 * @param albumInfoQuery
	 * @return
	 */
	@Operation(summary = "分页条件查询当前登录用户发布专辑")
	@PostMapping("/albumInfo/findUserAlbumPage/{page}/{limit}")
	@Fgb(value = true)
	public Result<Page<AlbumListVo>> findUserAlbumPage(@PathVariable Long page,
													   @PathVariable Long limit,
													   @RequestBody AlbumInfoQuery albumInfoQuery) {
		//1.获取当前用户登录ID
		Long userId = AuthContextHolder.getUserId();
		albumInfoQuery.setUserId(userId);
		//2.控制层封装分页参数：页码、页大小
		Page<AlbumListVo> pageParam = new Page<>(page, limit);
		//3.调用业务逻辑层完成分页查询 封装：总记录数，总页数，当前页数据
		pageParam = albumInfoService.findUserAlbumPage(pageParam, albumInfoQuery);
		return Result.ok(pageParam);
	}

	/**
	 * 根据专辑ID删除专辑
	 *
	 * @param id
	 * @return
	 */
	@Operation(summary = "根据专辑ID删除专辑")
	@DeleteMapping("/albumInfo/removeAlbumInfo/{id}")
	public Result removeAlbumInfo(@PathVariable Long id) {
		albumInfoService.removeAlbumInfo(id);
		return Result.ok();
	}

	/**
	 * 根据专辑ID查询专辑信息（包括专辑标签列表）
	 *
	 * @param id 专辑ID
	 * @return 专辑信息
	 */
	@Operation(summary = "根据专辑ID查询专辑信息（包括专辑标签列表）")
	@GetMapping("/albumInfo/getAlbumInfo/{id}")
	public Result<AlbumInfo> getAlbumInfo(@PathVariable Long id) {
		AlbumInfo albumInfo = albumInfoService.getAlbumInfo(id);
		return Result.ok(albumInfo);
	}

	/**
	 * 修改专辑信息
	 * @param id 专辑ID
	 * @param albumInfo 专辑修改后信息
	 * @return
	 */
	@Operation(summary = "更新专辑信息")
	@PutMapping("/albumInfo/updateAlbumInfo/{id}")
	public Result updateAlbumInfo(@PathVariable Long id, @Validated @RequestBody AlbumInfoVo albumInfoVo) {
		albumInfoService.updateAlbumInfo(id, albumInfoVo);
		return Result.ok();
	}

	/**
	 * TODO 该接口必须登录才能访问
	 * 获取当前用户全部专辑列表
	 * @return
	 */
	@Operation(summary = "获取当前用户全部专辑列表")
	@GetMapping("/albumInfo/findUserAllAlbumList")
	@Fgb(value = true)
	public Result<List<AlbumInfo>> getUserAllAlbumList(){
		//1.从ThreadLocal中获取当前登录用户ID
		Long userId = AuthContextHolder.getUserId();
		//2.调用业务逻辑获取专辑列表
		List<AlbumInfo> list  = albumInfoService.getUserAllAlbumList(userId);
		return Result.ok(list);
	}

	/**
	 * 根据专辑ID查询专辑统计信息
	 * @param albumId
	 * @return
	 */
	@Operation(summary = "根据专辑ID查询专辑统计信息")
	@GetMapping("/albumInfo/getAlbumStatVo/{albumId}")
	public Result<AlbumStatVo> getAlbumStatVo(@PathVariable Long albumId){
		AlbumStatVo albumStatVo = albumInfoService.getAlbumStatVo(albumId);
		return Result.ok(albumStatVo);
	}

	/**
	 * 基于当前选择购买起始声音得到分集购买列表
	 *
	 * @param trackId
	 * @return [{name:"本集",price:1,trackCount:1},{name:"后10集",price:10,trackCount:10}...{name:"全集/45集",price:45,trackCount:45}]
	 */
	@Fgb
	@Operation(summary = "基于当前选择购买起始声音得到分集购买列表")
	@GetMapping("/trackInfo/findUserTrackPaidList/{trackId}")
	public Result<List<Map<String, Object>>> getUserTrackPaidList(@PathVariable Long trackId) {
		//1.从ThreadLocal中获取当前登录用户ID
		Long userId = AuthContextHolder.getUserId();
		//2.调用业务逻辑获取分集购买列表
		List<Map<String, Object>> list = albumInfoService. findUserTrackPaidList(userId, trackId);
		return Result.ok(list);
	}

}