package club.boyuan.official.sse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsyncTaskSseEnvelope {

    private AsyncTaskChannel channel;
    private String key;
    private String payloadJson;
    private boolean terminal;
}
