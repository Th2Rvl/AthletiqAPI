package com.athletiq.group;

import com.athletiq.common.exception.BusinessRuleException;
import com.athletiq.common.exception.ResourceNotFoundException;

/** Codes et messages d'erreur des groupes. Le front s'appuie sur les codes. */
public final class GroupErrors {

    public static final String GROUP_NOT_FOUND = "GROUP_NOT_FOUND";
    public static final String GROUP_MEMBER_NOT_FOUND = "GROUP_MEMBER_NOT_FOUND";
    public static final String GROUP_NAME_ALREADY_EXISTS = "GROUP_NAME_ALREADY_EXISTS";

    private GroupErrors() {
    }

    /** Groupe inexistant ou appartenant à un autre coach : 404, sans dire lequel (RM4). */
    public static ResourceNotFoundException groupNotFound() {
        return new ResourceNotFoundException(GROUP_NOT_FOUND, "Ce groupe n'existe plus");
    }

    public static ResourceNotFoundException memberNotFound() {
        return new ResourceNotFoundException(GROUP_MEMBER_NOT_FOUND, "Cet athlète n'est pas dans ce groupe");
    }

    public static BusinessRuleException nameAlreadyExists() {
        return new BusinessRuleException(GROUP_NAME_ALREADY_EXISTS, "Vous avez déjà un groupe de ce nom", "name");
    }
}
