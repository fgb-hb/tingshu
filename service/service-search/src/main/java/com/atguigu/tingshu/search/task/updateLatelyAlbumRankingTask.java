package com.atguigu.tingshu.search.task;

import com.atguigu.tingshu.ServiceSearchApplication;
import com.atguigu.tingshu.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class updateLatelyAlbumRankingTask {
    @Autowired
    private SearchService searchService;
    private ServiceSearchApplication serviceSearchApplication;

    @Scheduled(cron = "0 0 * * * ?")
    public void run(){
    searchService.updateLatelyAlbumRanking();
    }
}
