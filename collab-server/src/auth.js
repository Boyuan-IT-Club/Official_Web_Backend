import jwt from 'jsonwebtoken';
import { ADMIN_PERMISSIONS, REQUIRED_PERMISSIONS, config } from './config.js';

/**
 * 校验前端带来的 JWT，与 Java 后端同一把 HS256 密钥。
 *
 * 已知边界：Java 侧的登出是把令牌写进 Redis 吊销名单，本服务不查该名单，
 * 因此在令牌自然过期前，已登出用户持有的旧令牌仍能建立协同连接。
 * 面试官是可信小群体、且连接只能操作评价表，这个残留风险可接受；
 * 若要收紧，在此接入 Redis 查询吊销名单即可。
 */
export function authenticateToken(token) {
  if (!token) {
    throw new Error('缺少令牌');
  }

  const claims = jwt.verify(token, config.jwtSecret, { algorithms: ['HS256'] });

  const permissions = Array.isArray(claims.permissionCodes) ? claims.permissionCodes : [];
  if (!REQUIRED_PERMISSIONS.some((code) => permissions.includes(code))) {
    throw new Error('缺少面试评价权限');
  }

  const userId = Number(claims.userId);
  if (!Number.isInteger(userId)) {
    throw new Error('令牌缺少 userId');
  }

  return {
    userId,
    username: claims.sub,
    isAdmin: ADMIN_PERMISSIONS.some((code) => permissions.includes(code)),
  };
}

/**
 * 从文档名解析周期ID：约定文档名形如 eval-board:3。
 */
export function parseCycleId(documentName) {
  const match = /^eval-board:(\d+)$/.exec(documentName ?? '');
  if (!match) {
    throw new Error(`非法的文档名 ${documentName}`);
  }
  return Number(match[1]);
}
