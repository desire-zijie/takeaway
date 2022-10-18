package com.itheima.reggie.dto;

import com.itheima.reggie.entity.OrderDetail;
import com.itheima.reggie.entity.Orders;
import lombok.Data;

import java.util.List;

/**
 * @author DZJ
 * @create 2022-10-15 16:42
 * @Description
 */
@Data
public class OrderDto extends Orders {

    private List<OrderDetail> orderDetails;
}
