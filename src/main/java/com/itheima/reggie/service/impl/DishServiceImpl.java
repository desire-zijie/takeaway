package com.itheima.reggie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.common.CustomException;
import com.itheima.reggie.dto.DishDto;
import com.itheima.reggie.entity.Dish;
import com.itheima.reggie.entity.DishFlavor;
import com.itheima.reggie.entity.Setmeal;
import com.itheima.reggie.entity.SetmealDish;
import com.itheima.reggie.mapper.DishMapper;
import com.itheima.reggie.service.DishFlavorService;
import com.itheima.reggie.service.DishService;
import com.itheima.reggie.service.SetmealDishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

  @Autowired private DishFlavorService dishFlavorService;

  @Autowired private SetmealDishService setmealDishService;

  /**
   * 新增菜品，同时保存对应的口味数据
   *
   * @param dishDto
   */
  @Override
  @Transactional
  public void saveWithFlavor(DishDto dishDto) {
    // 保存菜品的基本信息到菜品表dish
    this.save(dishDto);

    Long dishId = dishDto.getId(); // 菜品id

    // 菜品口味
    List<DishFlavor> flavors = dishDto.getFlavors();
    flavors =
        flavors.stream()
            .map(
                (item) -> {
                  item.setDishId(dishId);
                  return item;
                })
            .collect(Collectors.toList());

    // 保存菜品口味数据到菜品口味表dish_flavor
    dishFlavorService.saveBatch(flavors);
  }

  /**
   * 根据id查询菜品信息和对应的口味信息
   *
   * @param id
   * @return
   */
  @Override
  public DishDto getByIdWithFlavor(Long id) {
    // 查询菜品基本信息，从dish表查询
    Dish dish = this.getById(id);

    DishDto dishDto = new DishDto();
    BeanUtils.copyProperties(dish, dishDto);

    // 查询当前菜品对应的口味信息，从dish_flavor表查询
    LambdaQueryWrapper<DishFlavor> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(DishFlavor::getDishId, dish.getId());
    List<DishFlavor> flavors = dishFlavorService.list(queryWrapper);
    dishDto.setFlavors(flavors);

    return dishDto;
  }

  @Override
  @Transactional
  public void updateWithFlavor(DishDto dishDto) {
    // 更新dish表基本信息
    this.updateById(dishDto);

    // 清理当前菜品对应口味数据---dish_flavor表的delete操作
    LambdaQueryWrapper<DishFlavor> queryWrapper = new LambdaQueryWrapper();
    queryWrapper.eq(DishFlavor::getDishId, dishDto.getId());

    dishFlavorService.remove(queryWrapper);

    // 添加当前提交过来的口味数据---dish_flavor表的insert操作
    List<DishFlavor> flavors = dishDto.getFlavors();

    flavors =
        flavors.stream()
            .map(
                (item) -> {
                  item.setDishId(dishDto.getId());
                  return item;
                })
            .collect(Collectors.toList());

    dishFlavorService.saveBatch(flavors);
  }

  @Override
  public void updateDishStatusById(Integer status, List<Long> ids) {
    LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<>();
    wrapper.in(ids != null, Dish::getId, ids);
    List<Dish> dishList = this.list(wrapper);
    for (Dish dish : dishList) {
      if (dish != null) {
        dish.setStatus(status);
        this.updateById(dish);
      }
      //
    }
  }

  /**
   * 套餐批量删除和单个删除
   *
   * @param ids
   */
  @Override
  @Transactional
  public void deleteByIds(List<Long> ids) {

    // 构造条件查询器
    LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
    // 先查询该菜品是否在售卖，如果是则抛出业务异常
    queryWrapper.in(ids != null, Dish::getId, ids);
    List<Dish> list = this.list(queryWrapper);
    for (Dish dish : list) {
      Integer status = dish.getStatus();
      // 如果不是在售卖,则可以删除
      if (status == 0) {
        this.removeById(dish.getId());
      } else {
        // 此时应该回滚,因为可能前面的删除了，但是后面的是正在售卖
        throw new CustomException("删除菜品中有正在售卖菜品,无法全部删除");
      }
    }
  }

  @Override
  @Transactional
  public void delete(List<Long> ids) {
    LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<>();
    wrapper.in(Dish::getId, ids);
    wrapper.eq(Dish::getStatus, 1);
    int count = this.count(wrapper);
    if (count > 0) {
      throw new CustomException("菜品正在售卖，无法删除");
    }
    LambdaQueryWrapper<SetmealDish> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.in(SetmealDish::getDishId, ids);
    int count1 = setmealDishService.count(queryWrapper);
    if (count1 > 0) {
      throw new CustomException("有套餐存在该菜品，无法删除");
    }
    // 删除菜品  这里的删除是逻辑删除
    this.removeByIds(ids);
    // 删除菜品对应的口味  也是逻辑删除
    LambdaQueryWrapper<DishFlavor> dishFlavorLambdaQueryWrapper = new LambdaQueryWrapper<>();
    dishFlavorLambdaQueryWrapper.in(ids != null, DishFlavor::getDishId, ids);
    dishFlavorService.remove(dishFlavorLambdaQueryWrapper);
  }
}
