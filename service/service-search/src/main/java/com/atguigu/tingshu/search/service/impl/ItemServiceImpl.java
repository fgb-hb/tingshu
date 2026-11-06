package com.atguigu.tingshu.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.json.JsonData;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.search.service.ItemService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.el.lang.ELArithmetic;
import org.elasticsearch.client.RequestOptions;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ScriptType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

import static com.atguigu.tingshu.common.constant.SystemConstant.TRACK_STAT_COMMENT;
import static com.atguigu.tingshu.common.constant.SystemConstant.TRACK_STAT_PLAY;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class ItemServiceImpl implements ItemService {
@Autowired
private AlbumFeignClient albumFeignClient;
@Autowired
private UserFeignClient userFeignClient;
@Autowired
private ElasticsearchClient elasticsearchClient;
@Autowired
private RedissonClient redissonClient;
    private ThreadPoolExecutor threadPoolExecutor;

    @PostConstruct
    public void init() {
        threadPoolExecutor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() * 2 + 1,
                Runtime.getRuntime().availableProcessors() * 2 + 1,
                10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(200),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public Map<String, Object> getItem(Long albumId) {
        //从布隆过滤器中查询专辑是否存在
        //如果不存在，直接返回null
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConstant.ALBUM_BLOOM_FILTER);
        if (!bloomFilter.isExists()){
            return null;
               }
        boolean contains = bloomFilter.contains(albumId);
        if (!contains){
            throw new GuiguException(404, "专辑不存在");
        }
        //用异步编排模式，异步调用专辑feign接口查询专辑详情、属性、分类信息、用户信息
        //1.创建一个并发哈希映射，用于存储异步任务的结果
        ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();
        //1.通过专辑feign接口查询专辑详情
        CompletableFuture<AlbumInfo> albumInfoCompletableFuture = CompletableFuture.supplyAsync(() ->
                {
                    Result<AlbumInfo> albumInfo = albumFeignClient.getAlbumInfo(albumId);
                    AlbumInfo data = albumInfo.getData();
                    if (data==null){
                        throw new RuntimeException("专辑详情查询失败");
                    }
                    map.put("albumInfo", data);
                    return data;
                }
                , threadPoolExecutor);
        //2.通过专辑feign接口查询专辑属性
        CompletableFuture<Void> albumStatVoCompletableFuture = CompletableFuture.runAsync(() -> {
            Result<AlbumStatVo> albumStatVo = albumFeignClient.getAlbumStatVo(albumId);
            if (albumStatVo.getData() == null) {
                throw new RuntimeException("专辑属性查询失败");
            }
            map.put("albumStatVo", albumStatVo.getData());
        }, threadPoolExecutor);
        //3.通过专辑feign接口查询分类信息
        CompletableFuture<Void> categoryViewCompletableFuture = albumInfoCompletableFuture.thenAcceptAsync(albumInfo -> {
            Result<BaseCategoryView> categoryView = albumFeignClient.getCategoryView(albumInfo.getCategory3Id());
            if (categoryView.getData() == null) {
                throw new RuntimeException("分类信息查询失败");
            }
            map.put("baseCategoryView", categoryView.getData());
        }, threadPoolExecutor);
        //4.通过用户feign接口查询用户信息
        CompletableFuture<Void> announcerCompletableFuture = albumInfoCompletableFuture.thenAcceptAsync(albumInfo -> {
            Result<UserInfoVo> userInfo = userFeignClient.getUserInfoVo(albumInfo.getUserId());
            if (userInfo.getData() == null) {
                throw new RuntimeException("用户信息查询失败");
            }
            map.put("announcer", userInfo.getData());
        }, threadPoolExecutor);

        CompletableFuture.allOf(albumInfoCompletableFuture, albumStatVoCompletableFuture, categoryViewCompletableFuture, announcerCompletableFuture)
                .orTimeout(10, TimeUnit.SECONDS).join();

        return map;
    }

    @Override
    public void updateTrackStat(TrackStatMqVo mqVo) {
        //1.判断消息是否为空
        if (mqVo == null) {
            throw new RuntimeException("消息为空");
        }
        //2.修改专辑统计信息
        String scriptSource;
        if(TRACK_STAT_PLAY.equals(mqVo.getStatType())){
             scriptSource = """
                 ctx._source.playStatNum = (ctx._source.playStatNum == null ? 0 : ctx._source.playStatNum) + params.count
                """;
        }else if(TRACK_STAT_COMMENT.equals(mqVo.getStatType())){
             scriptSource = """
                 ctx._source.commentStatNum = (ctx._source.commentStatNum == null ? 0 : ctx._source.commentStatNum) + params.count
                """;
        }else {
            throw new RuntimeException("统计类型错误");
        }

        Map<String, JsonData> paramMap = Map.of(
                "count", JsonData.of(mqVo.getCount())
        );

        try {
            UpdateResponse<AlbumInfoIndex> painless =
                    elasticsearchClient.update(u -> u.script(s -> s.inline(i -> i.source(scriptSource).params(paramMap).lang("painless")))
                            .index("albuminfo").id(mqVo.getAlbumId().toString()), AlbumInfoIndex.class);
        } catch (IOException e) {
            log.error("更新专辑统计信息失败",e);
            throw new RuntimeException(e);
        }
    }
}
