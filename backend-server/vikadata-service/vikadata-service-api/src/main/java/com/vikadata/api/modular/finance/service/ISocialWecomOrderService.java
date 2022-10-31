package com.vikadata.api.modular.finance.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

import com.vikadata.entity.SocialWecomOrderEntity;
import com.vikadata.social.wecom.event.order.WeComOrderPaidEvent;

/**
 * <p>
 * 订阅计费系统-企微商店渠道订单
 * </p>
 */
public interface ISocialWecomOrderService extends IService<SocialWecomOrderEntity> {

    /**
     * 创建订单
     *
     * @param paidEvent 订单信息
     * @return 创建后的数据
     */
    SocialWecomOrderEntity createOrder(WeComOrderPaidEvent paidEvent);

    /**
     * 查询所有的订单
     *
     * @param suiteId 应用套件 ID
     * @param paidCorpId 授权的企业 ID
     * @param orderStatuses 查询的订单状态。可以为空
     * @return 符合条件的所有订单
     */
    List<SocialWecomOrderEntity> getAllOrders(String suiteId, String paidCorpId, List<Integer> orderStatuses);

    /**
     * 查询订单信息
     *
     * @param orderId 企微订单号
     * @return 订单信息
     */
    SocialWecomOrderEntity getByOrderId(String orderId);

    /**
     * Get first paid order
     *
     * @param suiteId Wecom isv suite ID
     * @param paidCorpId Paid corporation ID
     * @return Tenant's first paid order
     * @author Codeman
     * @date 2022-08-25 17:01:44
     */
    SocialWecomOrderEntity getFirstPaidOrder(String suiteId, String paidCorpId);

    /**
     * 获取租户最后一个支付成功的订单
     *
     * @param suiteId 应用套件 ID
     * @param paidCorpId 授权的企业 ID
     * @return 租户的最后一个支付成功的订单
     */
    SocialWecomOrderEntity getLastPaidOrder(String suiteId, String paidCorpId);

    /**
     * modify order status by order id
     * @param orderId social order id
     * @param orderStatus order status
     */
    void updateOrderStatusByOrderId(String orderId, int orderStatus);

    /**
     * Get the latest non-refundable subscription for the current subscription
     * @param spaceId space id
     * @param suiteId suite id
     * @param paidCorpId paid corp id
     * @return subscriptionId
     */
    List<String> getUnRefundedLastSubscriptionIds(String spaceId, String suiteId, String paidCorpId);
}
