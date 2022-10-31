package com.vikadata.api.control.permission.space.resource;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * <p>
 * Space Resource code for widget permission validate
 * </p>
 *
 * @author Pengap
 */
@Getter
@AllArgsConstructor
public enum ResourceCode {

    UPDATE_SPACE("UPDATE_SPACE"),

    DELETE_SPACE("DELETE_SPACE"),

    MANAGE_WORKBENCH_SETTING("MANAGE_WORKBENCH_SETTING"),

    ADD_MEMBER("ADD_MEMBER"),

    INVITE_MEMBER("INVITE_MEMBER"),

    READ_MEMBER("READ_MEMBER"),

    UPDATE_MEMBER("UPDATE_MEMBER"),

    DELETE_MEMBER("DELETE_MEMBER"),

    CREATE_TEAM("CREATE_TEAM"),

    READ_TEAM("READ_TEAM"),

    UPDATE_TEAM("UPDATE_TEAM"),

    DELETE_TEAM("DELETE_TEAM"),

    READ_MAIN_ADMIN("READ_MAIN_ADMIN"),

    UPDATE_MAIN_ADMIN("UPDATE_MAIN_ADMIN"),

    CREATE_SUB_ADMIN("CREATE_SUB_ADMIN"),

    READ_SUB_ADMIN("READ_SUB_ADMIN"),

    UPDATE_SUB_ADMIN("UPDATE_SUB_ADMIN"),

    DELETE_SUB_ADMIN("DELETE_SUB_ADMIN"),

    MANAGE_MEMBER_SETTING("MANAGE_MEMBER_SETTING"),

    CREATE_TEMPLATE("CREATE_TEMPLATE"),

    DELETE_TEMPLATE("DELETE_TEMPLATE"),

    MANAGE_SHARE_SETTING("MANAGE_SHARE_SETTING"),

    MANAGE_FILE_SETTING("MANAGE_FILE_SETTING"),

    MANAGE_ADVANCE_SETTING("MANAGE_ADVANCE_SETTING"),

    MANAGE_INTEGRATION_SETTING("MANAGE_INTEGRATION_SETTING"),

    UNPUBLISH_WIDGET("UNPUBLISH_WIDGET"),

    TRANSFER_WIDGET("TRANSFER_WIDGET");

    private final String code;
}
