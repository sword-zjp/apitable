package com.vikadata.boot.autoconfigure.social.feishu;

import java.lang.reflect.Method;

import com.vikadata.boot.autoconfigure.social.feishu.annotation.FeishuEventListener;

/**
 * Wrapping class of event listener
 *
 * @author Shawn Deng
 */
public class FeishuEventInvocation extends BaseInvocation {

    private final FeishuEventListener eventListenerAnnotation;

    private final Class<?> eventType;

    public FeishuEventInvocation(Method method, Object o, Class<?> eventType) {
        super(method, o);
        this.eventType = eventType;
        this.eventListenerAnnotation = method.getAnnotation(FeishuEventListener.class);
    }

    public Class<?> getEventType() {
        return eventType;
    }
}
