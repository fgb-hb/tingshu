package com.atguigu.tingshu.search.api;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "搜索专辑管理")
@RestController
@RequestMapping("api/search")
@SuppressWarnings({"all"})
public class SearchApiController {

    @Autowired
    private SearchService searchService;
    /**
     * 专辑上架
     *
     * @param albumId 专辑ID
     * @return
     */
    @Operation(summary = "专辑上架")
    @GetMapping("/albumInfo/upperAlbum/{albumId}")
    public Result upperAlbum(@PathVariable Long albumId) {
        searchService.upperAlbum(albumId);
        return Result.ok();
    }

    /**
     * 专辑下架
     *
     * @param albumId 专辑ID
     * @return
     */
    @Operation(summary = "专辑下架")
    @GetMapping("/albumInfo/lowerAlbum/{albumId}")
    public Result lowerAlbum(@PathVariable Long albumId) {
        searchService.lowerAlbum(albumId);
        return Result.ok();
    }
    /**
     * 站内搜索
     * @param albumIndexQuery
     * @return
     */
    @Operation(summary = "站内搜索")
    @PostMapping("/albumInfo")
    public Result<AlbumSearchResponseVo> search(@RequestBody AlbumIndexQuery albumIndexQuery){
        AlbumSearchResponseVo vo  = searchService.search(albumIndexQuery);
        return Result.ok(vo);
    }

    /**
     * 查询1级分类下置顶3级分类热度TOP6专辑
     * @param category1Id
     * @return [{"baseCategory3":{三级分类对象},list:[专辑列表]},,{其他6个置顶分类热门专辑Map}]
     */
    @Operation(summary = "查询1级分类下置顶3级分类热度TOP6专辑")
    @GetMapping("/albumInfo/channel/{category1Id}")
    public Result<List<Map<String, Object>>> channel(@PathVariable Long category1Id) {
        List<Map<String, Object>> list = searchService.channel(category1Id);
        return Result.ok(list);
    }

    /**
     * 根据用户已录入字符查询提词索引库进行自动补全关键字
     * @param keyword
     * @return
     */
    @Operation(summary = "根据用户已录入字符查询提词索引库进行自动补全关键字")
    @GetMapping("/albumInfo/completeSuggest/{keyword}")
    public Result<List<String>> completeSuggest(@PathVariable String keyword){
        List<String> list = searchService.completeSuggest(keyword);
        return Result.ok(list);
    }

    /**
     * 更新Redis小时榜
     * @return
     */
    @Operation(summary = "更新Redis小时榜")
    @GetMapping("/albumInfo/updateLatelyAlbumRanking")
    public Result updateLatelyAlbumRanking() {
        searchService.updateLatelyAlbumRanking();
        return Result.ok();
    }

    /**
     * 获取不同分类下不同排行维度TOP20
     * @param category1Id
     * @param dimension
     * @return
     */
    @Operation(summary = "获取不同分类下不同排行维度TOP20")
    @GetMapping("/albumInfo/findRankingList/{category1Id}/{dimension}")
    public Result<List<AlbumInfoIndexVo>> getRankingList(@PathVariable Long category1Id, @PathVariable String dimension) {
        List<AlbumInfoIndexVo> list = searchService.getRankingList(category1Id, dimension);
        return Result.ok(list);
    }

}

