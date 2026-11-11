package io.github.wrqtec.content.safe.api.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.wrqtec.content.safe.api.AuditResult;
import io.github.wrqtec.content.safe.api.RiskHit;

/**
 * 视频命中截断：长视频逐帧/逐音频段展开可达数百上千条命中，不截断会让
 * {@link AuditResult} 体积失控并沿事件、任务存储链路放大。统一在归一化出口收口：
 * 超限结果按置信度降序保留 TopN，截断标记（{@code truncated}/{@code totalHits}）并入
 * {@code raw}，调用方据此识别被裁剪的结果。
 *
 * <p>截断只影响 {@code hits} 明细，不影响 {@code suggestion}——建议值在供应商
 * 归一化层由完整命中集合推导，先于截断发生。</p>
 *
 * <p>各 provider 模块的视频归一化出口均应委托本类，禁止自行复制截断逻辑。</p>
 *
 * @author L.David
 */
public final class RiskHitCaps {

    /** 视频结果默认命中保留上限 */
    public static final int DEFAULT_VIDEO_LIMIT = 50;

    /** 置信度降序（null 视为最低、排在末尾），同分保持原相对顺序（稳定排序） */
    private static final Comparator<RiskHit> CONFIDENCE_DESC = (a, b) -> {
        Double da = a.confidence();
        Double db = b.confidence();
        if (da == null && db == null) {
            return 0;
        }
        if (da == null) {
            return 1;
        }
        if (db == null) {
            return -1;
        }
        return db.compareTo(da);
    };

    private RiskHitCaps() {
    }

    /** 按 {@link #DEFAULT_VIDEO_LIMIT} 截断视频命中（未超限原样返回） */
    public static AuditResult capVideoHits(AuditResult result) {
        return capVideoHits(result, DEFAULT_VIDEO_LIMIT);
    }

    /**
     * 命中数超过 {@code limit} 时按置信度降序保留前 {@code limit} 条，
     * {@code raw} 追加 {@code truncated=true} 与 {@code totalHits=原始条数}；
     * 未超限（或 limit 非正）原样返回
     *
     * @param result 归一化后的终态结果
     * @param limit  保留上限
     * @return 原结果或截断副本
     */
    public static AuditResult capVideoHits(AuditResult result, int limit) {
        if (result == null || limit <= 0 || result.hits().size() <= limit) {
            return result;
        }
        List<RiskHit> sorted = new ArrayList<>(result.hits());
        sorted.sort(CONFIDENCE_DESC);
        Map<String, Object> raw = new LinkedHashMap<>(result.raw());
        raw.put("truncated", true);
        raw.put("totalHits", result.hits().size());
        return result.withHits(List.copyOf(sorted.subList(0, limit))).withRaw(raw);
    }
}
