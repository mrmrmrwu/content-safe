package io.github.wrqtec.content.safe.model;

import io.github.wrqtec.content.safe.api.util.Asserts;

/**
 * 异步任务的供应商侧引用。不可变
 *
 * @param providerTaskId 供应商侧任务 ID
 * @param dataId         业务侧数据唯一标识
 * @author L.David
 */
public record AsyncTaskRef(String providerTaskId, String dataId) {

    public AsyncTaskRef {
        Asserts.notBlank(providerTaskId, "providerTaskId");
    }
}
