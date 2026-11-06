package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.mapper.*;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.cache.FgbCache;
import com.atguigu.tingshu.model.album.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@SuppressWarnings({"all"})
public class BaseCategoryServiceImpl extends ServiceImpl<BaseCategory1Mapper, BaseCategory1> implements BaseCategoryService {

	@Autowired
	private BaseCategory1Mapper baseCategory1Mapper;

	@Autowired
	private BaseCategory2Mapper baseCategory2Mapper;

	@Autowired
	private BaseCategory3Mapper baseCategory3Mapper;

    @Autowired
	private BaseCategoryViewMapper baseCategoryViewMapper;
	@Autowired
	private BaseAttributeMapper baseAttributeMapper;
	@Autowired
	private BaseAttributeValueMapper baseAttributeValueMapper;
	@Override
	public List<JSONObject> getBaseCategoryList() {
		//创建返回的集合
		ArrayList<JSONObject> jsonObjects = new ArrayList<>();
		//拿到base_category_view视图
		List<BaseCategoryView> baseCategoryViews = baseCategoryViewMapper.selectList(null);
		//1构建一级分类
		Map<Long, List<BaseCategoryView>> map1 =
				baseCategoryViews.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
		//1.2遍历一级分类，构建二级分类
		for (Map.Entry<Long,List<BaseCategoryView>> entry : map1.entrySet()) {
			JSONObject jsonObject1 = new JSONObject();
			jsonObject1.put("categoryId", entry.getKey().toString());
			jsonObject1.put("categoryName", entry.getValue().get(0).getCategory1Name());
			jsonObjects.add(jsonObject1);
		//1.3遍历二级分类，构建三级分类
			ArrayList<JSONObject> jsonObjects1 = new ArrayList<>();
			Map<Long, List<BaseCategoryView>> map2 =
					entry.getValue().stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
			for (Map.Entry<Long,List<BaseCategoryView>> entry2 : map2.entrySet()) {

				JSONObject jsonObject2 = new JSONObject();
				jsonObject2.put("categoryId", entry2.getKey().toString());
				jsonObject2.put("categoryName", entry2.getValue().get(0).getCategory2Name());
				jsonObjects1.add(jsonObject2);


		//1.4遍历三级分类
				ArrayList<JSONObject> jsonObjects2 = new ArrayList<>();
				Map<Long, List<BaseCategoryView>> map3 =
						entry2.getValue().stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory3Id));
				for (Map.Entry<Long,List<BaseCategoryView>> entry3 : map3.entrySet()) {
					JSONObject jsonObject3 = new JSONObject();
					jsonObject3.put("categoryId", entry3.getKey().toString());
					jsonObject3.put("categoryName", entry3.getValue().get(0).getCategory3Name());
					jsonObjects2.add(jsonObject3);

				}
				jsonObject2.put("categoryChild", jsonObjects2);
			}
			jsonObject1.put("categoryChild", jsonObjects1);
		}
		return jsonObjects;
	}

	@Override
	@FgbCache(prefix = "category:baseAttribute:")
	public List<BaseAttribute> getAttributesByCategory1Id(Long category1Id) {
		if (category1Id==null){
			throw new IllegalArgumentException("一级分类id不能为空");
		}
		//1根据一级分类id查询属性
		List<BaseAttribute> baseAttributes = baseAttributeMapper.selectList(new LambdaQueryWrapper<BaseAttribute>()
				.eq(BaseAttribute::getCategory1Id, category1Id));
		if (baseAttributes==null&&baseAttributes.isEmpty()){
			throw new IllegalArgumentException("一级分类下没有属性");
		}
		for (BaseAttribute baseAttribute : baseAttributes) {
			List<BaseAttributeValue> baseAttributeValues = baseAttributeValueMapper.selectList(new LambdaQueryWrapper<BaseAttributeValue>()
					.eq(BaseAttributeValue::getAttributeId, baseAttribute.getId()));
			baseAttribute.setAttributeValueList(baseAttributeValues);
		}
		return baseAttributes;
	}

	@Override
	@FgbCache(prefix = "category:categoryview:")
	public BaseCategoryView getCategoryView(Long category3Id) {
		return baseCategoryViewMapper.selectById(category3Id);

	}

	@Override
	@FgbCache(prefix = "category:topcategory3:")
	public List<BaseCategory3> findTopBaseCategory3(Long category1Id) {
		List<BaseCategory2> baseCategory2s = baseCategory2Mapper.selectList(new LambdaQueryWrapper<BaseCategory2>()
				.eq(BaseCategory2::getCategory1Id, category1Id));
		System.out.println("==========================baseCategory2s"+baseCategory2s);

		if (baseCategory2s==null&&baseCategory2s.isEmpty()){
			throw new IllegalArgumentException("一级分类下没有二级分类");
		}
		List<Long> collect = baseCategory2s.stream().map(BaseCategory2::getId).collect(Collectors.toList());
		return baseCategory3Mapper.selectList(new LambdaQueryWrapper<BaseCategory3>()
				.in(BaseCategory3::getCategory2Id,collect)
				.orderByAsc(BaseCategory3::getOrderNum)
						.select(BaseCategory3::getId,BaseCategory3::getName).
				last("limit 7"));
	}

	@Override
	@FgbCache(prefix = "category:categoryby1Id:")
	public JSONObject getBaseCategoryListByCategory1Id(Long category1Id) {
		//1.处理1级分类
		//1.1 根据1级分类ID查询分类视图得到所有"1级分类"列表
		List<BaseCategoryView> baseCategory1List = baseCategoryViewMapper.selectList(
				new LambdaQueryWrapper<BaseCategoryView>()
						.eq(BaseCategoryView::getCategory1Id, category1Id)
		);
		if (CollUtil.isNotEmpty(baseCategory1List)) {
			BaseCategoryView baseCategoryView = baseCategory1List.get(0);
			//1.2 创建1级分类JSON对象
			JSONObject jsonObject1 = new JSONObject();
			//1.2.1 封装1级分类ID
			jsonObject1.put("categoryId", baseCategoryView.getCategory1Id());
			//1.2.2 封装1级分类名称
			jsonObject1.put("categoryName", baseCategoryView.getCategory1Name());

			//2.处理2级分类
			//2.1 对"1级分类列表"按二级分类ID进行分组，得到Map
			Map<Long, List<BaseCategoryView>> map2 = baseCategory1List
					.stream()
					.collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));

			//2.2 遍历Map，创建二级分类JSON对象集合
			List<JSONObject> jsonObject2List = new ArrayList<>();
			for (Map.Entry<Long, List<BaseCategoryView>> entry2 : map2.entrySet()) {
				//2.2.1 创建二级分类JSON对象
				JSONObject jsonObject2 = new JSONObject();
				//2.2.2 封装二级分类ID
				Long category2Id = entry2.getKey();
				//2.2.3 封装二级分类名称
				String category2Name = entry2.getValue().get(0).getCategory2Name();
				jsonObject2.put("categoryId", category1Id);
				jsonObject2.put("categoryName", category2Name);
				//3.处理3级分类
				//3.1 创建3级分类JSON对象集合
				List<JSONObject> jsonObject3List = new ArrayList<>();
				//3.2 遍历"2级分类列表"，封装三级分类对象
				for (BaseCategoryView categoryView : entry2.getValue()) {
					//3.2.1 创建3级分类JSON对象
					JSONObject jsonObject3 = new JSONObject();
					//3.2.2 封装3级分类ID
					jsonObject3.put("categoryId", categoryView.getCategory3Id());
					//3.2.3 封装3级分类名称
					jsonObject3.put("categoryName", categoryView.getCategory3Name());
					jsonObject3List.add(jsonObject3);
				}
				//2.3 将三级分类集合加入到二级分类对象"categoryChild"中
				jsonObject2.put("categoryChild", jsonObject3List);
				jsonObject2List.add(jsonObject2);
			}
			//2.3 将三级分类集合加入到二级分类对象"categoryChild"中
			jsonObject1.put("categoryChild", jsonObject2List);
			return jsonObject1;
		}
		return null;
	}
}
