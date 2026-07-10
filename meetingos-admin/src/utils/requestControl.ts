export interface LatestRequestToken {
  readonly key: string;
  readonly generation: number;
  readonly signal: AbortSignal;
}

/**
 * 管理“最后一次请求生效”的代际，并集中持有请求的 AbortController。
 * 同一个 key 再次开始请求时会使旧 token 失效；reset 用于账号切换时一次性终止全部在途请求。
 */
export class LatestRequestRegistry {
  private readonly generations = new Map<string, number>();
  private readonly controllersByKey = new Map<string, AbortController>();
  private readonly activeControllers = new Set<AbortController>();

  begin(key: string, abortPrevious = true): LatestRequestToken {
    if (abortPrevious) {
      this.controllersByKey.get(key)?.abort();
    }

    const controller = new AbortController();
    const generation = (this.generations.get(key) || 0) + 1;
    this.generations.set(key, generation);
    this.controllersByKey.set(key, controller);
    this.activeControllers.add(controller);
    return { key, generation, signal: controller.signal };
  }

  isCurrent(token: LatestRequestToken) {
    return !token.signal.aborted && this.generations.get(token.key) === token.generation;
  }

  cancel(key: string) {
    this.controllersByKey.get(key)?.abort();
    this.controllersByKey.delete(key);
    this.generations.set(key, (this.generations.get(key) || 0) + 1);
  }

  finish(token: LatestRequestToken) {
    const controller = this.controllersByKey.get(token.key);
    if (controller?.signal === token.signal) {
      this.controllersByKey.delete(token.key);
    }
    for (const activeController of this.activeControllers) {
      if (activeController.signal === token.signal) {
        this.activeControllers.delete(activeController);
        break;
      }
    }
  }

  reset() {
    // 账号退出或切换时必须中止所有代际（包括已经被新请求替代、但仍在网络层执行的旧请求）。
    this.activeControllers.forEach((controller) => controller.abort());
    this.activeControllers.clear();
    this.controllersByKey.clear();
    this.generations.clear();
  }

  get activeRequestCount() {
    return this.activeControllers.size;
  }
}

/**
 * 将模块级 Promise Map 与请求代际放在同一生命周期中，避免登出后复用上一个账号的在途 Promise。
 */
export class RequestRuntime {
  readonly requests = new LatestRequestRegistry();
  private readonly promiseCaches = new Set<Map<unknown, unknown>>();

  createPromiseCache<K, V>() {
    const cache = new Map<K, V>();
    this.promiseCaches.add(cache as Map<unknown, unknown>);
    return cache;
  }

  reset() {
    this.requests.reset();
    this.promiseCaches.forEach((cache) => cache.clear());
  }
}
