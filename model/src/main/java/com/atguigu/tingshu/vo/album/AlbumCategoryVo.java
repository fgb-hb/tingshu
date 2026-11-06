package com.atguigu.tingshu.vo.album;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.stereotype.Component;

@Data
@Schema(description = "专辑分类VO")
public class AlbumCategoryVo {
    @Schema(description = "专辑分类名称")
    private String albumTitle;
    @Schema(description = "专辑分类ID")
    private Long  albumCategoryId;
}
