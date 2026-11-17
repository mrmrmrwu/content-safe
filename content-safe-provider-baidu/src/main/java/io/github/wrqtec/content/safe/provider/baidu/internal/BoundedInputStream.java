package io.github.wrqtec.content.safe.provider.baidu.internal;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 响应体硬上限流：超过 maxBytes 直接抛 {@link IOException}，
 * 防止异常端点/代理返回超大报文撑爆内存（解析前截断，不落盘不留原文）
 *
 * @author L.David
 */
final class BoundedInputStream extends FilterInputStream {

    /** 默认响应上限：4 MiB（审核/令牌响应正常远小于此） */
    static final int DEFAULT_MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private final long maxBytes;
    private long readBytes;

    BoundedInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            count(1);
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            count(n);
        }
        return n;
    }

    private void count(long n) throws IOException {
        readBytes += n;
        if (readBytes > maxBytes) {
            throw new IOException("response body exceeds limit of " + maxBytes + " bytes");
        }
    }
}
