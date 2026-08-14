package club.boyuan.official.infra.storage;

import java.io.InputStream;

/**
 * COS 对象内容及元数据，用于流式返回给客户端。
 */
public record CosFile(InputStream inputStream, String contentType, long contentLength) {
}
