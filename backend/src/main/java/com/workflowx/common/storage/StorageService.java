package com.workflowx.common.storage;

import java.io.InputStream;

/**
 * 对象存储抽象（Phase 8; ADR-018）。
 * 只负责字节流的存取，不感知 Issue/项目/权限等业务规则（分层边界）。
 */
public interface StorageService {

    /** 上传对象；objectKey 必须由服务端生成（AttachmentService 负责）。 */
    void upload(String objectKey, InputStream stream, long size, String contentType);

    /**
     * 读取对象。调用方负责关闭返回的流。
     *
     * @throws com.workflowx.common.exception.ResourceNotFoundException 对象不存在
     */
    StoredObject download(String objectKey);

    /** 删除对象；对象不存在时幂等成功（删除链路的最终一致语义）。 */
    void delete(String objectKey);

    boolean exists(String objectKey);

    /** 读取对象的元数据句柄。 */
    record StoredObject(InputStream stream, long size, String contentType) {
    }
}
