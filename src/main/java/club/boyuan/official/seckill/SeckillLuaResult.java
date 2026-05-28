package club.boyuan.official.seckill;

/**
 * Redis Lua 预扣库存返回值。
 */
public enum SeckillLuaResult {
    /** 预扣成功 */
    SUCCESS(1),
    /** 名额已满 */
    FULL(0),
    /** remain 缓存未预热 */
    CACHE_MISS(-1),
    /** 用户在本周期已有进行中的预约请求 */
    USER_LOCKED(-2);

    private final int code;

    SeckillLuaResult(int code) {
        this.code = code;
    }

    public static SeckillLuaResult fromCode(long code) {
        for (SeckillLuaResult value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return CACHE_MISS;
    }
}
