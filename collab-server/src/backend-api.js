import { config } from './config.js';

async function call(path, init = {}) {
  const response = await fetch(`${config.backendBaseUrl}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-Service-Token': config.serviceToken,
      ...(init.headers ?? {}),
    },
  });

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`调用 ${path} 失败：HTTP ${response.status} ${text}`);
  }

  const body = text ? JSON.parse(text) : {};
  if (body.code !== undefined && body.code !== 200) {
    throw new Error(`调用 ${path} 失败：${body.code} ${body.message}`);
  }
  return body.data;
}

/**
 * 拉取播种数据：列取自评分维度，行取自已分配的面试名单。
 */
export function fetchSeed(cycleId) {
  return call(`/api/internal/evaluation/board/${cycleId}/seed`);
}

/**
 * 物化回写。返回 { accepted, rejected, rejectReasons }。
 */
export function materialize(payload) {
  return call('/api/internal/evaluation/materialize', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
