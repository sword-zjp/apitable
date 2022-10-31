package com.vikadata.api.modular.space.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;
import javax.validation.Valid;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

import com.vikadata.api.annotation.ApiResource;
import com.vikadata.api.annotation.Notification;
import com.vikadata.api.annotation.PostResource;
import com.vikadata.api.component.TaskManager;
import com.vikadata.api.component.notification.NotificationRenderField;
import com.vikadata.api.component.notification.NotificationTemplateId;
import com.vikadata.api.config.properties.ConstProperties;
import com.vikadata.api.constants.NotificationConstants;
import com.vikadata.api.context.LoginContext;
import com.vikadata.api.context.SessionContext;
import com.vikadata.api.enums.space.SpaceApplyStatus;
import com.vikadata.api.component.notification.NotifyMailFactory;
import com.vikadata.api.component.notification.NotifyMailFactory.MailWithLang;
import com.vikadata.api.holder.NotificationRenderFieldHolder;
import com.vikadata.api.model.ro.space.SpaceJoinApplyRo;
import com.vikadata.api.model.ro.space.SpaceJoinProcessRo;
import com.vikadata.api.modular.organization.mapper.MemberMapper;
import com.vikadata.api.modular.space.mapper.SpaceMapper;
import com.vikadata.api.modular.space.service.ISpaceApplyService;
import com.vikadata.api.modular.space.service.ISpaceMemberRoleRelService;
import com.vikadata.api.modular.user.model.UserLangDTO;
import com.vikadata.api.modular.user.service.IUserService;
import com.vikadata.core.support.ResponseData;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.vikadata.api.constants.MailPropConstants.SUBJECT_SPACE_APPLY;
import static com.vikadata.api.constants.NotificationConstants.APPLY_ID;
import static com.vikadata.api.constants.NotificationConstants.APPLY_STATUS;

@RestController
@Api(tags = "Space - Apply Joining Space Api")
@ApiResource(path = "/space/apply")
public class SpaceApplyController {

    @Resource
    private ISpaceApplyService iSpaceApplyService;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private ISpaceMemberRoleRelService spaceMemberRoleRelService;

    @Resource
    private MemberMapper memberMapper;

    @Resource
    private ConstProperties constProperties;

    @Resource
    private IUserService userService;

    @Notification(templateId = NotificationTemplateId.SPACE_JOIN_APPLY)
    @PostResource(path = "/join", requiredPermission = false)
    @ApiOperation(value = "Applying to join the space")
    public ResponseData<Void> apply(@RequestBody @Valid SpaceJoinApplyRo ro) {
        Long userId = SessionContext.getUserId();
        // generate application records
        Long applyId = iSpaceApplyService.create(userId, ro.getSpaceId());
        // generate and send notifications
        NotificationRenderFieldHolder.set(NotificationRenderField.builder().spaceId(ro.getSpaceId()).bodyExtras(
            Dict.create().set(APPLY_ID, applyId).set(APPLY_STATUS, SpaceApplyStatus.PENDING.getStatus())).fromUserId(userId).build());
        List<Long> memberIds = spaceMemberRoleRelService.getMemberId(ro.getSpaceId(),
            ListUtil.toList(NotificationConstants.TO_MANAGE_MEMBER_RESOURCE_CODE));
        memberIds.add(spaceMapper.selectSpaceMainAdmin(ro.getSpaceId()));
        List<String> emails = memberMapper.selectEmailByBatchMemberId(memberIds);
        if (CollUtil.isNotEmpty(emails)) {
            Dict dict = Dict.create();
            dict.set("USER_NAME", LoginContext.me().getLoginUser().getNickName());
            dict.set("SPACE_NAME", spaceMapper.selectSpaceNameBySpaceId(ro.getSpaceId()));
            dict.set("URL", StrUtil.format(constProperties.getServerDomain() + "/space/{}/notification", ro.getSpaceId()));
            dict.set("YEARS", LocalDate.now().getYear());
            final String defaultLang = LocaleContextHolder.getLocale().toLanguageTag();
            List<UserLangDTO> emailsWithLang = userService.getLangByEmails(defaultLang, emails);
            List<MailWithLang> tos = emailsWithLang.stream()
                    .map(emailWithLang -> new MailWithLang(emailWithLang.getLocale(), emailWithLang.getEmail()))
                    .collect(Collectors.toList());
            TaskManager.me().execute(() -> NotifyMailFactory.me().sendMail(SUBJECT_SPACE_APPLY, dict, dict, tos));
        }
        return ResponseData.success();
    }

    @PostResource(path = "/process", requiredPermission = false)
    @ApiOperation(value = "Process joining application")
    public ResponseData<Void> process(@RequestBody @Valid SpaceJoinProcessRo ro) {
        Long userId = SessionContext.getUserId();
        iSpaceApplyService.process(userId, ro.getNotifyId(), ro.getAgree());
        return ResponseData.success();
    }

}
