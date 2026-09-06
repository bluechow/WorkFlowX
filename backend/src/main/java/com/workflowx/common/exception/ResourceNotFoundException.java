package com.workflowx.common.exception;

/** 资源不存在，404。 */
public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String message) {
        super(404, message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super(404, resource + " not found: " + id);
    }
}
