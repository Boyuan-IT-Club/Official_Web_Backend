import { createServer, watchLocks, watchRoster } from './server.js';
import { closePool } from './db.js';
import { config } from './config.js';

const hocuspocus = createServer();
const lockTimer = watchLocks(hocuspocus);
const rosterTimer = watchRoster(hocuspocus);

await hocuspocus.listen();
console.info(`[collab] 协同评价服务已启动，监听 ${config.port}`);

async function shutdown(signal) {
  console.info(`[collab] 收到 ${signal}，正在退出`);
  clearInterval(lockTimer);
  clearInterval(rosterTimer);
  try {
    // destroy 会触发各文档的 onStoreDocument，把未落库的快照写完再退出
    await hocuspocus.destroy();
    await closePool();
  } catch (error) {
    console.error(`[collab] 退出时清理失败：${error.message}`);
  }
  process.exit(0);
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
