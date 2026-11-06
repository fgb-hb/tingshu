package com.atguigu.tingshu.search.service;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.SuggestIndex;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;

import java.util.List;
import java.util.Map;

public interface SearchService {


    /**
     * 专辑上架
     *
     * @param albumId 专辑ID
     */
    void upperAlbum(Long albumId);

    /**
     * 专辑下架
     *
     * @param albumId 专辑ID
     */
    void lowerAlbum(Long albumId);
    /**
     * 站内搜索
     * @param albumIndexQuery
     * @return
     */
    AlbumSearchResponseVo search(AlbumIndexQuery albumIndexQuery);

    /**
     * 基于查询条件封装ES检索DSL语句
     * @param albumIndexQuery 查询条件
     * @return
     */
    SearchRequest buildDSL(AlbumIndexQuery albumIndexQuery);

    /**
     * 解析ES响应结果
     * @param searchResponse
     * @param albumIndexQuery
     * @return
     */
    AlbumSearchResponseVo parseResult(SearchResponse<AlbumInfoIndex> searchResponse, AlbumIndexQuery albumIndexQuery);

    List<Map<String, Object>> channel(Long category1Id);

    void saveSuggestIndex(AlbumInfoIndex albumInfoIndex);

    /**
     * 根据用户已录入字符查询提词索引库进行自动补全关键字
     * @param keyword
     * @return
     */
    List<String> completeSuggest(String keyword);

    /**
     * 解析建议词结果
     * @param searchResponse
     * @param suggestName
     * @return
     */
    List<String> parseSuggestResult(SearchResponse<SuggestIndex> searchResponse, String suggestName);

    /**
     * 更新Redis小时榜
     */
    void updateLatelyAlbumRanking();

    /**
     * 获取不同分类下不同排行维度TOP20
     * @param category1Id
     * @param dimension
     * @return
     */
    List<AlbumInfoIndexVo> getRankingList(Long category1Id, String dimension);
}