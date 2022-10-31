package com.vikadata.api.modular.social.service.impl;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import javax.annotation.Resource;

import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.vikadata.api.AbstractIsvTest;
import com.vikadata.api.FileHelper;
import com.vikadata.api.component.clock.ClockManager;
import com.vikadata.api.enums.social.SocialPlatformType;
import com.vikadata.api.mock.bean.MockUserSpace;
import com.vikadata.api.modular.finance.model.SocialOrderContext;
import com.vikadata.api.modular.finance.service.ISocialWecomOrderService;
import com.vikadata.api.modular.finance.strategy.SocialOrderStrategyFactory;
import com.vikadata.api.modular.finance.strategy.impl.WeComOrderServiceImpl;
import com.vikadata.api.modular.social.factory.SocialFactory;
import com.vikadata.api.modular.space.model.vo.SpaceSubscribeVo;
import com.vikadata.api.util.billing.WeComPlanConfigManager;
import com.vikadata.api.util.billing.model.ProductChannel;
import com.vikadata.clock.ClockUtil;
import com.vikadata.social.wecom.event.order.WeComOrderPaidEvent;
import com.vikadata.social.wecom.event.order.WeComOrderRefundEvent;
import com.vikadata.social.wecom.model.WxCpIsvAuthInfo.EditionInfo.Agent;
import com.vikadata.social.wecom.model.WxCpIsvGetOrder;
import com.vikadata.social.wecom.model.WxCpIsvPermanentCodeInfo;
import com.vikadata.system.config.billing.Plan;
import com.vikadata.system.config.billing.Price;

import static com.vikadata.api.constants.TimeZoneConstants.DEFAULT_TIME_ZONE;
import static com.vikadata.api.util.billing.BillingConfigManager.getFreePlan;
import static java.lang.Thread.sleep;

/**
 * <p>
 * {@link WeComOrderServiceImpl} unit tests
 * </p>
 */
class WeComOrderServiceImplTests extends AbstractIsvTest {

    private static final String APP_ID = "ww0506baa4d734acb9";

    private static final SocialPlatformType PLATFORM = SocialPlatformType.WECOM;

    @Resource
    private ISocialWecomOrderService iSocialWecomOrderService;

    @Test
    void testFilterWecomEditionAgentNotNull() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_unlimited_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        Assertions.assertNotNull(agent);
    }

    @Test
    void testFilterWecomEditionAgentNull() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_trail_expired.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        Assertions.assertNull(agent);
    }

    @Test
    void testFormatWecomTailEditionOrderPaidEventForUnlimited() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_unlimited_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        assert agent != null;
        WeComOrderPaidEvent event = SocialFactory.formatWecomTailEditionOrderPaidEvent("ww0506baa4d734acb9",
                permanentCodeInfo.getAuthCorpInfo().getCorpId(), ClockManager.me().getLocalDateTimeNow(), agent);
        // unlimited to add 100 years
        Assertions.assertEquals((long) event.getEndTime(), ClockUtil.secondToLocalDateTime(event.getBeginTime(),
                DEFAULT_TIME_ZONE).plusYears(100).toEpochSecond(DEFAULT_TIME_ZONE));
    }

    @Test
    void testFormatWecomTailEditionOrderPaidEventFor15Days() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        assert agent != null;
        WeComOrderPaidEvent event = SocialFactory.formatWecomTailEditionOrderPaidEvent("ww0506baa4d734acb9",
                permanentCodeInfo.getAuthCorpInfo().getCorpId(), ClockManager.me().getLocalDateTimeNow(), agent);
        Assertions.assertEquals((long) event.getEndTime(), agent.getExpiredTime());
    }

    /**
     * Test build standard trial context
     *
     * @author Codeman
     * @date 2022-08-30 11:31:33
     */
    @Test
    void testBuildSocialOrderContextForTrail() {
        // 1 prepare wecom isv env
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        prepareSocialBindInfo(permanentCodeInfo.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        // 2 build context
        assert agent != null;
        WeComOrderPaidEvent event = SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo.getAuthCorpInfo().getCorpId(), ClockManager.me().getLocalDateTimeNow(), agent);
        SocialOrderContext context = SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).buildSocialOrderContext(event);
        Assertions.assertNotNull(context);
    }

    /**
     * Test paid for standard trial edition
     *
     * @author Codeman
     * @date 2022-08-30 11:32:42
     */
    @Test
    void testRetrieveOrderPaidEventForTrail() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        assert agent != null;
        agent.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        WeComOrderPaidEvent event = SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo.getAuthCorpInfo().getCorpId(),
                ClockUtil.secondToLocalDateTime(agent.getExpiredTime(), testTimeZone).minusDays(15), agent);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(event);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Assertions.assertTrue(subscribeVo.getOnTrial());
        Assertions.assertEquals(WeComPlanConfigManager.getPaidPlanFromWeComTrial().getId(), subscribeVo.getPlan());
    }

    @Test
    void testRetrieveOrderPaidEventForTrailUnlimited() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_unlimited_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        assert agent != null;
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        WeComOrderPaidEvent event = SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).toLocalDateTime(), agent);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(event);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Assertions.assertTrue(subscribeVo.getOnTrial());
        Assertions.assertEquals(WeComPlanConfigManager.getPaidPlanFromWeComTrial().getId(), subscribeVo.getPlan());
    }

    @Test
    void testRetrieveOrderPaidEventForTrailUnlimitedToUpgrade() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_unlimited_trail.json");
        Agent agent1 = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).toLocalDateTime(), agent1));
        // upgrade
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        LocalDateTime expireTime = getClock().getNow(testTimeZone).plusDays(15).toLocalDateTime();
        agent.setExpiredTime(expireTime.toEpochSecond(testTimeZone));
        WeComOrderPaidEvent event = SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).toLocalDateTime(), agent);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(event);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Assertions.assertTrue(subscribeVo.getOnTrial());
        Assertions.assertEquals(WeComPlanConfigManager.getPaidPlanFromWeComTrial().getId(), subscribeVo.getPlan());
        Assertions.assertEquals(expireTime.toLocalDate(), subscribeVo.getDeadline());
    }

    @Test
    void testRetrieveOrderPaidEventForTrailToUpgrade30Days() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent1 = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent1.setExpiredTime(getClock().getNow(testTimeZone).minusDays(1).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                ClockUtil.secondToLocalDateTime(agent1.getExpiredTime(), testTimeZone).minusDays(15), agent1));
        // upgrade
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        LocalDateTime expireTime = getClock().getNow(testTimeZone).plusDays(30).toLocalDateTime();
        agent.setExpiredTime(expireTime.toEpochSecond(testTimeZone));
        WeComOrderPaidEvent event = SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).toLocalDateTime(), agent);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(event);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Assertions.assertTrue(subscribeVo.getOnTrial());
        Assertions.assertEquals(WeComPlanConfigManager.getPaidPlanFromWeComTrial().getId(), subscribeVo.getPlan());
        Assertions.assertEquals(expireTime.toLocalDate(), subscribeVo.getDeadline());
    }

    /**
     * Test for new standard edition with 10 people and 1 year
     *
     * @author Codeman
     * @date 2022-08-30 14:11:08
     */
    @Test
    void testStandardTenPeopleAndOneYearOrder() {
        // trail
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent1 = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent1.setExpiredTime(getClock().getNow(testTimeZone).plusDays(15).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).toLocalDateTime(), agent1));
        // pay for ten people
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusDays(paidEvent.getOrderPeriod()).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // 4 assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertFalse(subscribeVo.getOnTrial());
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for renewal standard edition with 10 people and 1 year
     *
     * @author Codeman
     * @date 2022-08-30 16:36:31
     */
    @Test
    void testStandardTenPeopleAndOneYearRenew() {
        // trail
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent1 = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent1.setExpiredTime(getClock().getNow(testTimeZone).minusYears(1).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(380).toLocalDateTime(), agent1));
        // pay for ten people
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).minusYears(1).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        //  handle renewal paid event
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_renewal.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).plusDays(1).toEpochSecond());
        paidEvent.setEndTime(ClockUtil.secondToLocalDateTime(paidEvent.getBeginTime(), testTimeZone).plusYears(2).toEpochSecond(testTimeZone));
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // 3 assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
        Assertions.assertEquals(ClockUtil.secondToLocalDateTime(paidEvent.getEndTime(), testTimeZone).toLocalDate(),
                subscribeVo.getDeadline());
    }

    /**
     * Test for upgrade standard edition with 10 people and 1 year
     *
     * @author Codeman
     * @date 2022-08-30 17:53:40
     */
    @Test
    void testStandardTenPeopleAndOneYearUpgrade() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent1 = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent1.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(380).toLocalDateTime(), agent1));
        // pay for ten people
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle upgrade paid event
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_upgrade.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertEquals(subscribeVo.getMaxSeats(), paidEvent.getUserCount());
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for upgrade standard edition with 10 people and 1 year, then refund
     *
     * @author Codeman
     * @date 2022-08-30 18:51:11
     */
    @Test
    void testStandardTenPeopleAndOneYearUpgradeRefund() throws InterruptedException {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent1 = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent1.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(380).toLocalDateTime(), agent1));
        // pay for ten people
        sleep(1000);
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle upgrade paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_upgrade.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // 5 handle upgrade refund event
        WeComOrderRefundEvent refundEvent = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_upgrade_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent);
        // 6 assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent1.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertEquals(subscribeVo.getMaxSeats(), paidEvent1.getUserCount());
        Assertions.assertEquals(ClockUtil.secondToLocalDateTime(paidEvent.getEndTime(), testTimeZone).toLocalDate(),
                subscribeVo.getDeadline());
    }

    /**
     * Test for renew standard edition with 10 people and 1 year, then refund
     *
     * @author Codeman
     * @date 2022-08-31 16:45:15
     */
    @Test
    void testStandardTenPeopleAndOneYearRenewalRefund() throws InterruptedException {
        // trail
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent1 = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent1.setExpiredTime(getClock().getNow(testTimeZone).minusYears(1).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(380).toLocalDateTime(), agent1));
        // pay for ten people
        sleep(1000);
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).minusYears(1).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        //  handle renewal paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_renewal.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).plusDays(1).toEpochSecond());
        paidEvent.setEndTime(ClockUtil.secondToLocalDateTime(paidEvent.getBeginTime(), testTimeZone).plusYears(2).toEpochSecond(testTimeZone));
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // handle renewal refund event
        WeComOrderRefundEvent refundEvent = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_renewal_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent1.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
        Assertions.assertEquals(ClockUtil.secondToLocalDateTime(paidEvent1.getEndTime(), testTimeZone).toLocalDate(),
                subscribeVo.getDeadline());
    }

    /**
     * Test for new standard edition with 10 people and 1 year, then refund
     *
     * @author Codeman
     * @date 2022-08-31 16:50:21
     */
    @Test
    void testStandardTenPeopleAndOneYearRefund() throws InterruptedException {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent1 = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent1.setExpiredTime(getClock().getNow(testTimeZone).plusDays(15).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent1));
        // pay for ten people
        sleep(1000);
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle new refund event
        WeComOrderRefundEvent refundEvent = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_new_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Assertions.assertTrue(subscribeVo.getOnTrial());
        Assertions.assertEquals(WeComPlanConfigManager.getPaidPlanFromWeComTrial().getId(), subscribeVo.getPlan());
        Assertions.assertEquals(ClockUtil.secondToLocalDateTime(agent1.getExpiredTime(), testTimeZone).toLocalDate(),
                subscribeVo.getDeadline());
    }

    @Test
    void testStandardTenPeopleAndOneYearUpgradeAndRenewRefund() throws InterruptedException {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent1 = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent1.setExpiredTime(getClock().getNow(testTimeZone).plusDays(1).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent1));
        // pay for ten people
        sleep(1000);
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle upgrade paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent2 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_upgrade.json");
        paidEvent2.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent2.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent2);
        //  handle renewal paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent3 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_renewal.json");
        paidEvent3.setBeginTime(getClock().getNow(testTimeZone).plusDays(1).toEpochSecond());
        paidEvent3.setEndTime(ClockUtil.secondToLocalDateTime(paidEvent3.getBeginTime(), testTimeZone).plusYears(2).toEpochSecond(testTimeZone));
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent3);
        // handle renewal refund event
        WeComOrderRefundEvent refundEvent1 = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_renewal_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent1.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent1);
        // handle upgrade refund event
        WeComOrderRefundEvent refundEvent2 = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_upgrade_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent2.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent2);
        // handle new refund event
        WeComOrderRefundEvent refundEvent3 = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_new_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent3.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent3);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Assertions.assertTrue(subscribeVo.getOnTrial());
        Assertions.assertEquals(WeComPlanConfigManager.getPaidPlanFromWeComTrial().getId(), subscribeVo.getPlan());
        Assertions.assertEquals(ClockUtil.secondToLocalDateTime(agent1.getExpiredTime(), testTimeZone).toLocalDate(),
                subscribeVo.getDeadline());
    }


    /**
     * Test for new standard edition with 10 people and 2 year
     *
     * @author Codeman
     * @date 2022-08-31 17:45:25
     */
    @Test
    void testStandardTenPeopleAndTwoYearOrder() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent));
        // handle new paid event
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_10_2_per_year_new.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertFalse(subscribeVo.getOnTrial());
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for new standard edition with 10 people and 3 year
     *
     * @author Codeman
     * @date 2022-08-31 17:47:29
     */
    @Test
    void testStandardTenPeopleAndThreeYearOrder() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent));
        // handle new paid event
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_10_3_per_year_new.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertFalse(subscribeVo.getOnTrial());
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for new standard edition with 20 people and 1 year
     *
     * @author Codeman
     * @date 2022-08-31 17:47:29
     */
    @Test
    void testStandardTwentyPeopleAndOneYearOrder() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent));
        // handle new paid event
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_20_1_per_year_new.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertFalse(subscribeVo.getOnTrial());
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for new standard edition with 20 people and 2 year
     *
     * @author Codeman
     * @date 2022-08-31 17:47:29
     */
    @Test
    void testStandardTwentyPeopleAndTwoYearOrder() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent));
        // handle new paid event
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_20_2_per_year_new.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertFalse(subscribeVo.getOnTrial());
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for new standard edition with 20 people and 3 year
     *
     * @author Codeman
     * @date 2022-08-31 17:47:29
     */
    @Test
    void testStandardTwentyPeopleAndThreeYearOrder() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent));
        // handle new paid event
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/standard_20_3_per_year_new.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // 5 assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertFalse(subscribeVo.getOnTrial());
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for change standard edition with 10 people and 1 year to enterprise edition with 120 people and 1 year
     *
     * @author Codeman
     * @date 2022-08-31 16:45:15
     */
    @Test
    void testStandardTenPeopleAndOneYearChange() throws InterruptedException {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent));
        // pay for ten people
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle new paid event
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/enterprise_120_1_per_year_change.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // 5 assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertFalse(subscribeVo.getOnTrial());
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for change standard edition with 10 people and 1 year to enterprise edition with 120 people and 1 year, then refund
     *
     * @author Codeman
     * @date 2022-08-31 16:45:15
     */
    @Test
    void testStandardTenPeopleAndOneYearChangeRefund() throws InterruptedException {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent));
        // pay for ten people
        sleep(1000);
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle new paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/enterprise_120_1_per_year_change.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // 5 handle change edition refund event
        WeComOrderRefundEvent refundEvent = getOrderRefundEvent("social/wecom/order/enterprise_120_1_per_year_change_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent);
        // 6 assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent1.getEditionId(),
                paidEvent1.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent1.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for change standard edition with 10 people and 1 year to enterprise edition with 120 people and 1 year, then refund
     *
     * @author Codeman
     * @date 2022-08-31 16:45:15
     */
    @Test
    void testStandardTenPeopleAndOneYearChangeRefundThree() throws Exception {
        // pay for ten people
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        MockUserSpace userSpace = prepareSocialBindInfo(paidEvent1.getPaidCorpId(), APP_ID, PLATFORM, ISV);
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle 10 upgrade to 20 paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent2 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_upgrade.json");
        paidEvent2.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent2.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent2);
        // 5 handle upgrade refund event
        sleep(1000);
        WeComOrderRefundEvent refundEvent1 = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_upgrade_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent1.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent1);
        // pay for 120 people
        sleep(1000);
        WeComOrderPaidEvent paidEvent3 = getOrderPaidEvent("social/wecom/order/enterprise_120_1_per_year_change.json");
        paidEvent3.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent3.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent3);
        // handle refund event
        WeComOrderRefundEvent refundEvent2 = getOrderRefundEvent("social/wecom/order/enterprise_120_1_per_year_change_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent2.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent2);
        // handle new refund event
        WeComOrderRefundEvent refundEvent = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_new_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Plan plan = getFreePlan(ProductChannel.WECOM);
        Assertions.assertEquals(plan.getId(), subscribeVo.getPlan());
    }

    @Test
    void testStandardTenPeopleAndOneYearChangeRefundThreeWithTrail() throws Exception {
        // trail
        WxCpIsvPermanentCodeInfo permanentCodeInfo = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo.getEditionInfo());
        agent.setExpiredTime(getClock().getNow(testTimeZone).plusDays(15).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).toLocalDateTime(), agent));
        // pay for ten people
        sleep(1000);
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle 10 upgrade to 20 paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent2 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_upgrade.json");
        paidEvent2.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent2.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent2);
        // handle upgrade refund event
        sleep(1000);
        WeComOrderRefundEvent refundEvent1 = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_upgrade_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent1.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent1);
        // pay for 120 people
        sleep(1000);
        WeComOrderPaidEvent paidEvent3 = getOrderPaidEvent("social/wecom/order/enterprise_120_1_per_year_change.json");
        paidEvent3.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent3.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent3);
        // handle refund event
        WeComOrderRefundEvent refundEvent2 = getOrderRefundEvent("social/wecom/order/enterprise_120_1_per_year_change_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent2.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent2);
        // handle new refund event
        WeComOrderRefundEvent refundEvent = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_new_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Assertions.assertTrue(subscribeVo.getOnTrial());
    }

    @Test
    void testStandardTenPeopleAndOneYearChangeRefundThreeAllRefund() throws Exception {
        // pay for ten people
        sleep(1000);
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        MockUserSpace userSpace = prepareSocialBindInfo(paidEvent1.getPaidCorpId(), APP_ID, PLATFORM, ISV);
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle 10 upgrade to 20 paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent2 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_upgrade.json");
        paidEvent2.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent2.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent2);
        // handle upgrade refund event
        sleep(1000);
        WeComOrderRefundEvent refundEvent1 = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_upgrade_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent1.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent1);
        // pay for 120 people
        sleep(1000);
        WeComOrderPaidEvent paidEvent3 = getOrderPaidEvent("social/wecom/order/enterprise_120_1_per_year_change.json");
        paidEvent3.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent3.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent3);
        // handle refund event
        WeComOrderRefundEvent refundEvent2 = getOrderRefundEvent("social/wecom/order/enterprise_120_1_per_year_change_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent2.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent2);
        // handle new refund event
        WeComOrderRefundEvent refundEvent = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_new_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent);
        // pay for ten people
        sleep(1000);
        WeComOrderPaidEvent paidEvent4 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        paidEvent4.setOrderId(IdWorker.get32UUID());
        paidEvent4.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent4.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent4);
        // handle 10 upgrade to 20 paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent5 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_upgrade.json");
        paidEvent5.setOrderId(IdWorker.get32UUID());
        paidEvent5.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent5.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent5);
        // handle upgrade refund event
        sleep(1000);
        WeComOrderRefundEvent refundEvent4 = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_upgrade_refund.json");
        refundEvent4.setOrderId(paidEvent5.getOrderId());
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent4.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent4);
        // handle new refund event
        WeComOrderRefundEvent refundEvent5 = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_new_refund.json");
        refundEvent5.setOrderId(paidEvent4.getOrderId());
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent5.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent5);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Plan plan = getFreePlan(ProductChannel.WECOM);
        Assertions.assertEquals(plan.getId(), subscribeVo.getPlan());
    }

    @Test
    void testStandardTenPeopleAndOneYearChangeRefundTwice() throws Exception {
        // pay for ten people
        WeComOrderPaidEvent paidEvent1 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_new.json");
        MockUserSpace userSpace = prepareSocialBindInfo(paidEvent1.getPaidCorpId(), APP_ID, PLATFORM, ISV);
        paidEvent1.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent1.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent1);
        // handle 10 upgrade to 20 paid event
        sleep(1000);
        WeComOrderPaidEvent paidEvent2 = getOrderPaidEvent("social/wecom/order/standard_10_1_per_year_upgrade.json");
        paidEvent2.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent2.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent2);
        // 5 handle upgrade refund event
        sleep(1000);
        WeComOrderRefundEvent refundEvent1 = getOrderRefundEvent("social/wecom/order/standard_10_1_per_year_upgrade_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent1.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent1);
        // pay for 120 people
        sleep(1000);
        WeComOrderPaidEvent paidEvent3 = getOrderPaidEvent("social/wecom/order/enterprise_120_1_per_year_change.json");
        paidEvent3.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent3.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent3);
        // handle refund event
        WeComOrderRefundEvent refundEvent2 = getOrderRefundEvent("social/wecom/order/enterprise_120_1_per_year_change_refund.json");
        iSocialWecomOrderService.updateOrderStatusByOrderId(refundEvent2.getOrderId(), 5);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderRefundEvent(refundEvent2);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent1.getEditionId(),
                paidEvent1.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent1.getOrderPeriod()));
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Test for new enterprise edition with 120 people and 1 year
     *
     * @author Codeman
     * @date 2022-08-30 14:11:08
     */
    @Test
    void testEnterpriseOneHundredAndTwentyPeopleAndOneYearOrder() {
        WxCpIsvPermanentCodeInfo permanentCodeInfo1 = getWxCpPermanentCodeInfo("social/wecom/create_auth_for_15_days_trail.json");
        Agent agent = SocialFactory.filterWecomEditionAgent(permanentCodeInfo1.getEditionInfo());
        agent.setExpiredTime(getClock().getNow(testTimeZone).toEpochSecond());
        MockUserSpace userSpace = prepareSocialBindInfo(permanentCodeInfo1.getAuthCorpInfo().getCorpId(), APP_ID, PLATFORM, ISV);
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(SocialFactory.formatWecomTailEditionOrderPaidEvent(APP_ID,
                permanentCodeInfo1.getAuthCorpInfo().getCorpId(),
                getClock().getNow(testTimeZone).minusDays(15).toLocalDateTime(), agent));
        // handle new paid event
        WeComOrderPaidEvent paidEvent = getOrderPaidEvent("social/wecom/order/enterprise_120_1_per_year_new.json");
        paidEvent.setBeginTime(getClock().getNow(testTimeZone).toEpochSecond());
        paidEvent.setEndTime(getClock().getNow(testTimeZone).plusYears(1).toEpochSecond());
        SocialOrderStrategyFactory.getService(SocialPlatformType.WECOM).retrieveOrderPaidEvent(paidEvent);
        // assert subscription
        SpaceSubscribeVo subscribeVo = iSpaceSubscriptionService.getSpaceSubscription(userSpace.getSpaceId());
        Price price = WeComPlanConfigManager.getPriceByWeComEditionIdAndMonth(paidEvent.getEditionId(),
                paidEvent.getUserCount(), SocialFactory.getWeComOrderMonth(paidEvent.getOrderPeriod()));
        Assertions.assertNotNull(price);
        Assertions.assertFalse(subscribeVo.getOnTrial());
        Assertions.assertEquals(price.getPlanId(), subscribeVo.getPlan());
    }

    /**
     * Get paid event info from json file
     *
     * @param filePath Json file path
     * @return Paid event info
     * @author Codeman
     * @date 2022-08-30 11:21:47
     */
    private WeComOrderPaidEvent getOrderPaidEvent(String filePath) {
        InputStream resourceAsStream = FileHelper.getInputStreamFromResource(filePath);
        String jsonString = IoUtil.read(resourceAsStream, StandardCharsets.UTF_8);
        WxCpIsvGetOrder order = JSONUtil.toBean(jsonString, WxCpIsvGetOrder.class);
        return SocialFactory.formatOrderPaidEventFromWecomOrder(order);
    }

    private WxCpIsvPermanentCodeInfo getWxCpPermanentCodeInfo(String filePath) {
        InputStream resourceAsStream = FileHelper.getInputStreamFromResource(filePath);
        String jsonString = IoUtil.read(resourceAsStream, StandardCharsets.UTF_8);
        return JSONUtil.toBean(jsonString, WxCpIsvPermanentCodeInfo.class);
    }

    /**
     * Get refund event info from json file
     *
     * @param filePath Json file path
     * @return Refund event info
     * @author Codeman
     * @date 2022-08-30 18:53:50
     */
    private WeComOrderRefundEvent getOrderRefundEvent(String filePath) {
        InputStream resourceAsStream = FileHelper.getInputStreamFromResource(filePath);
        String jsonString = IoUtil.read(resourceAsStream, StandardCharsets.UTF_8);
        return JSONUtil.toBean(jsonString, WeComOrderRefundEvent.class);
    }

}
