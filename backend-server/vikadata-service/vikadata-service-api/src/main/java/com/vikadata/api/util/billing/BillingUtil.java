package com.vikadata.api.util.billing;

import java.util.Collections;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;

import com.vikadata.api.util.billing.model.ProductChannel;
import com.vikadata.api.util.billing.model.SubscribePlanInfo;
import com.vikadata.system.config.billing.Plan;

import static com.vikadata.api.util.billing.model.BillingConstants.CATALOG_VERSION;

/**
 * billing util
 *
 * @author Shawn Deng
 */
public class BillingUtil {

    public static SubscribePlanInfo channelDefaultSubscription(ProductChannel channel) {
        Plan freePlan = BillingConfigManager.getFreePlan(channel);
        if (freePlan == null) {
            throw new RuntimeException("free plan is missing");
        }
        return SubscribePlanInfo.builder().version(CATALOG_VERSION)
                .product(freePlan.getProduct())
                // lark/dingtalk/wecom base is not expired，set null
                .deadline(null)
                .basePlan(freePlan)
                .addOnPlans(Collections.emptyList()).build();
    }

    public static String legacyPlanId(String planId) {
        String[] elements = planId.split("_");
        if (elements.length < 2) {
            throw new IllegalStateException("plan parse error");
        }
        String element = elements[1];
        if (NumberUtil.isNumber(element)) {
            return StrUtil.join("_", elements[0], elements[1]);
        }
        return planId;
    }
}
