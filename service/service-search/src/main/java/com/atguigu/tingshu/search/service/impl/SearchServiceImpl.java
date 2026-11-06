package com.atguigu.tingshu.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.extra.pinyin.PinyinUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import co.elastic.clients.json.JsonData;
import com.alibaba.cloud.commons.lang.StringUtils;
import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.*;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.AttributeValueIndex;
import com.atguigu.tingshu.model.search.SuggestIndex;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.repository.AlbumInfoIndexRepository;
import com.atguigu.tingshu.search.repository.SuggestIndexRepository;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.javassist.bytecode.analysis.Executor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.query.Order;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


@Slf4j
@Service
@SuppressWarnings({"all"})
public class SearchServiceImpl implements SearchService {

    private ThreadPoolExecutor threadPoolExecutor;

    @PostConstruct
    public void init() {
        threadPoolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2 + 1,
                Runtime.getRuntime().availableProcessors() * 2 + 1,
                10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Autowired
    private AlbumFeignClient albumFeignClient;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private AlbumInfoIndexRepository albumInfoIndexRepository;
    @Autowired
    private ElasticsearchClient elasticsearchClient;
    @Autowired
    private SuggestIndexRepository suggestIndexRepository;
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Override
    public void upperAlbum(Long albumId) {
        AlbumInfoIndex albumInfoIndex = new AlbumInfoIndex();
        CompletableFuture<AlbumInfo> albumInfoCompletableFuture = CompletableFuture.supplyAsync(() ->
        {
            Result<AlbumInfo> albumInfo = albumFeignClient.getAlbumInfo(albumId);
            return albumInfo.getData();

        }, threadPoolExecutor).handle((res, ex) -> {
            if (ex != null) {
                throw new RuntimeException("获取专辑信息失败", ex);
            }
            return res;
        });

        CompletableFuture<BaseCategoryView> categoryViewCompletableFuture = albumInfoCompletableFuture.thenApplyAsync(albumInfo ->
        {
            Result<BaseCategoryView> categoryView = albumFeignClient.getCategoryView(albumInfo.getCategory3Id());
            return categoryView.getData();
        }, threadPoolExecutor).handle((res, ex) -> {
            if (ex != null) {
                throw new RuntimeException("获取专辑分类信息失败", ex);
            }
            return res;
        });

        CompletableFuture<UserInfoVo> userInfoVoCompletableFuture = albumInfoCompletableFuture.thenApplyAsync(albumInfo -> {
           // System.out.println(albumInfo.getUserId()+"===============================");
            Result<UserInfoVo> userInfoVo = userFeignClient.getUserInfoVo(albumInfo.getUserId());
           // System.out.println(userInfoVo.getData()+"===========================");
            return userInfoVo.getData();
        }, threadPoolExecutor).handle((res, ex) -> {
            if (ex != null) {
                throw new RuntimeException("获取用户信息失败", ex);
            }
            return res;
        });

        CompletableFuture.allOf(albumInfoCompletableFuture, categoryViewCompletableFuture, userInfoVoCompletableFuture).
                orTimeout(10, TimeUnit.SECONDS).join();

        AlbumInfo albumInfo = albumInfoCompletableFuture.join();
        if (albumInfo == null) {
            log.error("获取专辑信息失败");
            throw new RuntimeException("获取专辑信息失败");
        }
        albumInfoIndex.setId(albumId);
        albumInfoIndex.setAlbumTitle(albumInfo.getAlbumTitle());
        albumInfoIndex.setAlbumIntro(albumInfo.getAlbumIntro());
        albumInfoIndex.setCoverUrl(albumInfo.getCoverUrl());
        albumInfoIndex.setIncludeTrackCount(albumInfo.getIncludeTrackCount());
        albumInfoIndex.setIsFinished(albumInfo.getIsFinished());
        albumInfoIndex.setPayType(albumInfo.getPayType());
        albumInfoIndex.setCreateTime(albumInfo.getCreateTime());
        albumInfoIndex.setHotScore(Double.valueOf(albumInfo.getQualityScore()));
        ArrayList<AttributeValueIndex> collection = new ArrayList<>();
        for (AlbumAttributeValue albumAttributeValue : albumInfo.getAlbumAttributeValueVoList()) {
            AttributeValueIndex attributeValueIndex = BeanUtil.copyProperties(albumAttributeValue, AttributeValueIndex.class);
            collection.add(attributeValueIndex);
        }
        albumInfoIndex.setAttributeValueIndexList(collection);

        BaseCategoryView categoryView = categoryViewCompletableFuture.join();
        if (categoryView == null) {
            log.error("获取专辑分类信息失败");
            throw new RuntimeException("获取专辑分类信息失败");
        }
        albumInfoIndex.setCategory1Id(categoryView.getCategory1Id());
        albumInfoIndex.setCategory2Id(categoryView.getCategory2Id());
        albumInfoIndex.setCategory3Id(categoryView.getCategory3Id());

        UserInfoVo userInfoVo = userInfoVoCompletableFuture.join();
        if (userInfoVo == null) {
            log.error("获取用户信息失败");
            throw new RuntimeException("获取用户信息失败");
        }
        albumInfoIndex.setAnnouncerName(userInfoVo.getNickname());

        // 上架专辑到ES
        albumInfoIndexRepository.save(albumInfoIndex);
        //3.将专辑标题存入提词索引库
        this.saveSuggestIndex(albumInfoIndex);
        //4.将专辑ID存入布隆过滤器
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConstant.ALBUM_BLOOM_FILTER);
        if (bloomFilter.isExists()){
            bloomFilter.add(albumId);
        }


    }

    @Override
    public void lowerAlbum(Long albumId) {
        albumInfoIndexRepository.deleteById(albumId);
        suggestIndexRepository.deleteById(albumId.toString());
    }

    @Override
    public AlbumSearchResponseVo search(AlbumIndexQuery albumIndexQuery) {
        try {
            //一、构建完整检索请求对象：包含完整DSL语句请求体参数
            SearchRequest searchRequest = this.buildDSL(albumIndexQuery);
            System.err.println("本次检索DSL：");
            System.out.println(searchRequest);
            //二、执行检索
            SearchResponse<AlbumInfoIndex> searchResponse = elasticsearchClient.search(searchRequest, AlbumInfoIndex.class);
            //三、解析ES响应结果
            return this.parseResult(searchResponse, albumIndexQuery);
        } catch (IOException e) {
            log.error("执行检索失败", e);
            throw new RuntimeException(e);
        }
    }

    private static final String INDEX_NAME = "albuminfo";

    /**
     * 整体看采用传统方式，局部采用Lambda简化开发
     * 基于查询条件封装ES检索DSL语句
     *
     * @param albumIndexQuery 查询条件
     * @return
     */
    @Override
    public SearchRequest buildDSL(AlbumIndexQuery albumIndexQuery){
        //1.创建检索构建器对象 封装 请求地址中：index 索引库名称
        SearchRequest.Builder builder = new SearchRequest.Builder();
        builder.index(INDEX_NAME);
        //2.设置查询条件：请求体参数query
        String keyword = albumIndexQuery.getKeyword();
        //2.0 创建bool查询条件：用于封装所有查询条件
        BoolQuery.Builder allConditionBoolQueryBuilder = new BoolQuery.Builder();
        //2.1 条件1：关键字 采用must 有算分机制按相关性返回
        if (StringUtils.isNotBlank(keyword)) {
            allConditionBoolQueryBuilder.must(m -> m.match(m1 -> m1.field("albumTitle").query(keyword)));
        }
        //2.2 条件2：一二三级分类ID 采用filter 有缓存机制
        //2.2.1. 封装1级分类ID查询条件
        Long category1Id = albumIndexQuery.getCategory1Id();
        if (category1Id != null) {
            allConditionBoolQueryBuilder.filter(f -> f.term(t -> t.field("category1Id").value(category1Id)));
        }
        //2.2.2. 封装2级分类ID查询条件
        Long category2Id = albumIndexQuery.getCategory2Id();
        if (category2Id != null) {
            allConditionBoolQueryBuilder.filter(f -> f.term(t -> t.field("category2Id").value(category2Id)));
        }
        //2.2.3. 封装3级分类ID查询条件
        Long category3Id = albumIndexQuery.getCategory3Id();
        if (category3Id != null) {
            allConditionBoolQueryBuilder.filter(f -> f.term(t -> t.field("category3Id").value(category3Id)));
        }
        //2.3 条件3：多组标签List集合 每组标签形式=标签ID：标签值ID
        List<String> attributeList = albumIndexQuery.getAttributeList();
        //2.4 每遍历一组标签过滤条件，设置一个Nested查询
        if (CollUtil.isNotEmpty(attributeList)) {
            for (String s : attributeList) {
                String[] split = s.split(":");
                if (split != null && split.length == 2) {
                    allConditionBoolQueryBuilder
                            .filter(f -> f.nested(
                                    n -> n.path("attributeValueIndexList")
                                            .query(q -> q.bool(b -> b.must(m -> m.term(t -> t.field("attributeValueIndexList.attributeId").value(split[0])))
                                                    .must(m -> m.term(t -> t.field("attributeValueIndexList.valueId").value(split[1])))
                                            ))
                            ));
                }
            }
        }

        builder.query(allConditionBoolQueryBuilder.build()._toQuery());
        //builder.query();
        //3.设置分页：请求体参数from size
        Integer pageNo = albumIndexQuery.getPageNo();
        Integer pageSize = albumIndexQuery.getPageSize();
        int from = (pageNo - 1) * pageSize;
        builder.from(from).size(pageSize);

        //4.设置排序：请求体参数sort
        //4.1 前后端约定 如果需要排序提交参数order值=排序字段（1,2,3）：排序方式（asc,desc）
        String order = albumIndexQuery.getOrder();
        if (StringUtils.isNotBlank(order)) {
            //4.2 获取排序字段以及排序方式
            String[] split = order.split(":");
            if (split != null && split.length == 2) {
                String sortField = "";
                switch (split[0]) {
                    case "1":
                        sortField = "hotScore";
                        break;
                    case "2":
                        sortField = "playStatNum";
                        break;
                    case "3":
                        sortField = "createTime";
                        break;
                }
                String direction = split[1];
                //4.3 动态设置排序
                SortOrder sortOrder = "asc".equals(direction) ? SortOrder.Asc : SortOrder.Desc;
                String finalSortField = sortField;
                builder.sort(s -> s.field(f -> f.field(finalSortField).order(sortOrder)));
            }
        }

        //5.设置高亮：请求体参数highlight 前提条件:用户采用关键字全文查询text字段
        if (StringUtils.isNotBlank(keyword)) {
            builder.highlight(h -> h.fields("albumTitle", f -> f.preTags("<font style='color:red'>").postTags("</font>")));
        }

        //6.设置响应字段：请求体参数_source
        builder.source(s -> s.filter(f -> f.excludes("attributeValueIndexList",
                "hotScore",
                "commentStatNum",
                "buyStatNum",
                "subscribeStatNum",
                "announcerName")));

        //7.基于构建器对象返回实际检索对象
        return builder.build();
    }

    /**
     * 解析ES响应结果
     *
     * @param searchResponse  ES响应对象
     * @param albumIndexQuery 查询条件
     * @return
     */
    @Override
    public AlbumSearchResponseVo parseResult(SearchResponse<AlbumInfoIndex> searchResponse, AlbumIndexQuery albumIndexQuery) {
        //1.创建响应结果对象
        AlbumSearchResponseVo vo = new AlbumSearchResponseVo();
        //2.封装VO中分页四项信息
        Integer pageNo = albumIndexQuery.getPageNo();
        Integer pageSize = albumIndexQuery.getPageSize();
        //2.1 获取ES结果中总记录数
        HitsMetadata<AlbumInfoIndex> hits = searchResponse.hits();
        long total = hits.total().value();
        //2.2 计算总页数  页大小=10  总计：101
        long totalPages = total % pageSize == 0 ? total / pageSize : total / pageSize + 1;
        vo.setPageNo(pageNo);
        vo.setPageSize(pageSize);
        vo.setTotal(total);
        vo.setTotalPages(totalPages);

        //3.封装Vo中专辑列表
        List<Hit<AlbumInfoIndex>> hitsList = hits.hits();
        if (CollUtil.isNotEmpty(hitsList)) {
            List<AlbumInfoIndexVo> infoIndexVoList = hitsList
                    .stream()
                    .map(hit -> {
                        //3.1 获取命中文档原数据
                        AlbumInfoIndex albumInfoIndex = hit.source();
                        //3.2 处理高亮片段
                        Map<String, List<String>> highlight = hit.highlight();
                        if (CollUtil.isNotEmpty(highlight)) {
                            String albumTitleHightLight = highlight.get("albumTitle").get(0);
                            albumInfoIndex.setAlbumTitle(albumTitleHightLight);
                        }
                        //3.3响应VO
                        return BeanUtil.copyProperties(albumInfoIndex, AlbumInfoIndexVo.class);
                    }).collect(Collectors.toList());
            vo.setList(infoIndexVoList);
        }
        //x.响应结果VO对象
        return vo;
    }

    /**
     * 查询1级分类下置顶3级分类热度TOP6专辑
     *
     * @param category1Id
     * @return [{"baseCategory3":{三级分类对象},list:[专辑列表]},,{其他6个置顶分类热门专辑Map}]
     */
    @Override
    public List<Map<String, Object>> channel(Long category1Id) {
        System.out.println("=========================================="+category1Id);
        try {
            //1.根据1级分类ID获取置顶三级分类ID
            //1.1 远程调用远程调用专辑微服务获取置顶3级分类
            List<BaseCategory3> baseCategory3List = albumFeignClient.findTopBaseCategory3(category1Id).getData();
            Assert.notNull(baseCategory3List, "一级分类{}置顶三级分类不存在", category1Id);
            System.out.println("==============================baseCategory3List"+baseCategory3List);

            //1.2 获取置顶三级分类ID列表
            List<FieldValue> fieldValueList = baseCategory3List
                    .stream()
                    .map(c3 -> FieldValue.of(c3.getId()))
                    .collect(Collectors.toList());

            //1.3 为了封装结果Map中分类对象，将三级分类List转为Map<3级分类ID，3级分类对象>
            Map<Long, BaseCategory3> category3Map = baseCategory3List
                    .stream()
                    .collect(Collectors.toMap(BaseCategory3::getId, baseCategory3 -> baseCategory3));

            //2. 调用ES进行检索：置顶分类热门专辑
            SearchResponse<AlbumInfoIndex> searchResponse = elasticsearchClient.search(
                    s -> s.index(INDEX_NAME)
                            .size(0)
                            .query(q -> q.terms(t -> t.field("category3Id").terms(t1 -> t1.value(fieldValueList))))
                            .aggregations(
                                    "c3_agg", a -> a.terms(t -> t.field("category3Id").size(7))
                                            .aggregations("top6", a1 -> a1.topHits(
                                                    t -> t.size(6)
                                                            .sort(s1 -> s1.field(f -> f.field("hotScore").order(SortOrder.Desc)))
                                                            .source(s1 -> s1.filter(f -> f.excludes("attributeValueIndexList",
                                                                    "hotScore",
                                                                    "commentStatNum",
                                                                    "buyStatNum",
                                                                    "subscribeStatNum",
                                                                    "announcerName")))
                                            ))
                            )
                    , AlbumInfoIndex.class);

            //3.解析聚合结果，获取置顶分类包含热度TOP6专辑列表 将7个置顶分类热门专辑Map封装到List集合中
            List<LongTermsBucket> longTermsBuckets = searchResponse.aggregations().get("c3_agg").lterms().buckets().array();
            //3.1 遍历三级分类聚合结果，每遍历一次就封装一个置顶分类热门专辑Map对象
            if (CollUtil.isNotEmpty(longTermsBuckets)) {
                List<Map<String, Object>> topCategory3MapList = longTermsBuckets
                        .stream()
                        .map(bucket -> {
                            //3.1.1 创建Map封装置顶分类热门专辑
                            Map<String, Object> map = new HashMap<>();
                            //3.1.2 获取聚合结果中三级分类ID
                            long topCategory3Id = bucket.key();
                            //3.1.3 获取top6专辑列表
                            //获取三级分类聚合内子聚合"top6"
                            List<Hit<JsonData>> top6HitsList = bucket.aggregations().get("top6")
                                    .topHits().hits().hits();
                            if (CollUtil.isNotEmpty(top6HitsList)) {
                                //遍历Hit集合 将集合泛型从Hit转为AlbumInfoIndex类型
                                List<AlbumInfoIndex> topAlbumList = top6HitsList.stream()
                                        .map(hit -> {
                                            //获取专辑JSON字符串
                                            String albumInfoIndexJSONStr = hit.source().toString();
                                            //将专辑JSON字符串转为AlbumInfoIndex对象
                                            AlbumInfoIndex albumInfoIndex = JSON.parseObject(albumInfoIndexJSONStr, AlbumInfoIndex.class);
                                            return albumInfoIndex;
                                        }).collect(Collectors.toList());
                                //3.2 封装置顶分类热门专辑Map
                                map.put("baseCategory3", category3Map.get(topCategory3Id));
                                map.put("list", topAlbumList);
                            }
                            return map;
                        }).collect(Collectors.toList());
                return topCategory3MapList;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    /**
     * 将专辑标题存入提词索引库
     *
     * @param albumInfoIndex
     */
    @Override
    public void saveSuggestIndex(AlbumInfoIndex albumInfoIndex) {
        //1.构建索引库文档对象
        SuggestIndex suggestIndex = new SuggestIndex();
        suggestIndex.setId(albumInfoIndex.getId().toString());
        String albumTitle = albumInfoIndex.getAlbumTitle();
        suggestIndex.setTitle(albumTitle);
        suggestIndex.setKeyword(new Completion(new String[]{albumTitle}));
        //1.1 将汉字转为汉语拼音 jing dian liu sheng ji
        String albumTitlePinyin = PinyinUtil.getPinyin(albumTitle, "");
        suggestIndex.setKeywordPinyin(new Completion(new String[]{albumTitlePinyin}));
        //1.1 将汉字转为汉语拼音首字母
        String albumTitleFirstLetter = PinyinUtil.getFirstLetter(albumTitle, "");
        suggestIndex.setKeywordSequence(new Completion(new String[]{albumTitleFirstLetter}));

        //2.存入提词文档记录到提词索引库
        suggestIndexRepository.save(suggestIndex);
    }

    private static final String SUGGEST_INDEX = "suggestinfo";

    /**
     * 根据用户已录入字符查询提词索引库进行自动补全关键字
     *
     * @param keyword
     * @return
     */
    @Override
    public List<String> completeSuggest(String keyword) {
        try {
            //1.发起自动补全请求
            SearchResponse<SuggestIndex> searchResponse = elasticsearchClient.search(s ->
                            s.index(SUGGEST_INDEX)
                                    .suggest(
                                            s1 -> s1.suggesters("letter-suggest", fs -> fs.prefix(keyword).completion(c -> c.field("keywordSequence").size(10).skipDuplicates(true)))
                                                    .suggesters("pinyin-suggest", s2 -> s2.prefix(keyword).completion(c -> c.field("keywordPinyin").size(10).skipDuplicates(true)))
                                                    .suggesters("keyword-suggest", s2 -> s2.prefix(keyword).completion(c -> c.field("keyword").size(10).skipDuplicates(true)))
                                    )
                    , SuggestIndex.class);
            //2.解析ES自动补全结果
            Set<String> hashSet = new HashSet<>();
            //2.1 解析建议结果-通过不同建议参数名获取汉字、拼音等提示词结果
            hashSet.addAll(this.parseSuggestResult(searchResponse, "letter-suggest"));
            hashSet.addAll(this.parseSuggestResult(searchResponse, "pinyin-suggest"));
            hashSet.addAll(this.parseSuggestResult(searchResponse, "keyword-suggest"));
            //2.2 如果解析建议提示词列表长度小于10，采用全文查询尝试补全到10个
            if (hashSet.size() < 10) {
                //2.2.1 根据用户录入字符进行全文检索
                SearchResponse<AlbumInfoIndex> matchSearchResponse = elasticsearchClient.search(
                        s -> s.index(INDEX_NAME)
                                .query(q -> q.match(m -> m.field("albumTitle").query(keyword)))
                                .size(10)
                        , AlbumInfoIndex.class
                );
                HitsMetadata<AlbumInfoIndex> hits = matchSearchResponse.hits();
                List<Hit<AlbumInfoIndex>> hitList = hits.hits();
                if (CollectionUtil.isNotEmpty(hitList)) {
                    for (Hit<AlbumInfoIndex> hit : hitList) {
                        AlbumInfoIndex source = hit.source();
                        //2.2.2 将检索到专辑标题内容加入到提词结果列表中
                        hashSet.add(source.getAlbumTitle());
                        if (hashSet.size() >= 10) {
                            break;
                        }
                    }
                }
            }
            if (hashSet.size() >= 10) {
                //如果提词结果列表中大于10截取前10个
                return new ArrayList<>(hashSet).subList(0, 10);
            } else {
                return new ArrayList<>(hashSet);
            }
        } catch (IOException e) {
            log.error("[搜索服务]关键字自动补全异常：{}", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 解析建议词结果
     *
     * @param searchResponse ES检索结果对象
     * @param suggestName    自定义建议词参数名称
     * @return
     */
    @Override
    public List<String> parseSuggestResult(SearchResponse<SuggestIndex> searchResponse, String suggestName) {
        //根据自定义建议词参数名称获取结果列表
        List<String> list = new ArrayList<>();
        List<Suggestion<SuggestIndex>> suggestionList = searchResponse.suggest().get(suggestName);
        if (CollectionUtil.isNotEmpty(suggestionList)) {
            //遍历得到建议对象
            for (Suggestion<SuggestIndex> suggestIndexSuggestion : suggestionList) {
                for (CompletionSuggestOption<SuggestIndex> option : suggestIndexSuggestion.completion().options()) {
                    SuggestIndex suggestIndex = option.source();
                    list.add(suggestIndex.getTitle());
                }
            }
        }
        return list;
    }

    @Override
    public void updateLatelyAlbumRanking() {
        try {
            Result<List<BaseCategory1>> allCategory1 = albumFeignClient.getAllCategory1();
            if (allCategory1.getData()==null){
                throw new RuntimeException("获取专辑分类失败");
            }
            List<BaseCategory1> baseCategory1List = allCategory1.getData();
            String[] rankingDimensionArray
                    = new String[]{"hotScore", "playStatNum", "subscribeStatNum", "buyStatNum", "commentStatNum"};
            //遍历分类列表
            for (BaseCategory1 baseCategory1 : baseCategory1List) {
            //遍历分类维度数组
            for (String rankingDimension : rankingDimensionArray) {
                //根据分类ID和分类维度进行ES检索
                SearchResponse<AlbumInfoIndex> response = elasticsearchClient.search(s -> s.index(INDEX_NAME)
                                .query(q -> q.term(t -> t.field("category1Id").value(baseCategory1.getId())))
                                .sort(s1 -> s1.field(f -> f.field(rankingDimension).order(SortOrder.Desc)))
                                .size(20)
                        , AlbumInfoIndex.class);
                //解析ES检索结果
                List<Hit<AlbumInfoIndex>> hits = response.hits().hits();
                if (CollectionUtil.isEmpty(hits)) {
                    throw new RuntimeException("获取专辑列表失败");
                }
                List<AlbumInfoIndex> collect = hits.stream().map(Hit::source).collect(Collectors.toList());
                //加入redis小时榜
                String key= RedisConstant.RANKING_KEY_PREFIX + baseCategory1.getId();
                redisTemplate.opsForHash().put(key,rankingDimension,collect);
            }
            }
        } catch (IOException e) {
            log.error("[搜索服务]更新Redis小时榜异常：{}", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AlbumInfoIndexVo> getRankingList(Long category1Id, String dimension) {
        String key= RedisConstant.RANKING_KEY_PREFIX + category1Id;
        List<AlbumInfoIndex> albumInfoIndexList =(List<AlbumInfoIndex>) redisTemplate.opsForHash().get(key, dimension);
        if (CollectionUtil.isEmpty(albumInfoIndexList)){
            throw new RuntimeException("获取专辑列表失败");
        }
        return albumInfoIndexList.stream().map(albumInfoIndex -> {
            return BeanUtil.copyProperties(albumInfoIndex,AlbumInfoIndexVo.class);
        }).collect(Collectors.toList());
    }

}

