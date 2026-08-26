import { realApi } from './realApi'
import { mockApi } from './mockApi'
import type { User, Device, Sensor, DataPoint } from './mockApi'

// 智能双模自适应 API 网关 (比赛演示防翻车保障):
// 优先调用后端 realApi，若后端未启动、连接超时或发生网络异常，自动平滑降级至 mockApi。
// 降级带有冷却窗口（FALLBACK_TTL_MS）：窗口过后自动重新探测 realApi，
// 避免后端恢复后仍永久停留在 mock 模式显示假数据。

const isFallbackActive = { value: false }
let fallbackUntil = 0
const FALLBACK_TTL_MS = 60_000

// 写操作与认证接口绝不降级到 mock：
// mock 的用户态与真实登录不一致，静默返回假成功/空列表会破坏真实数据视图
const NO_FALLBACK_PREFIXES = [
  'create', 'update', 'delete', 'batch', 'send', 'register',
  'regenerate', 'login', 'logout', 'changePassword'
]

function shouldNeverFallback(propKey: string | symbol): boolean {
  const key = String(propKey)
  return NO_FALLBACK_PREFIXES.some(p => key.toLowerCase().startsWith(p))
}

/** 当前是否处于 mock 降级窗口（供调用方防御空数据覆盖） */
export function isInFallback(): boolean {
  return inFallbackWindow()
}

function inFallbackWindow(): boolean {
  if (!isFallbackActive.value) return false
  if (Date.now() >= fallbackUntil) {
    // 冷却结束，重新探测真实后端
    isFallbackActive.value = false
    return false
  }
  return true
}

function enterFallback() {
  isFallbackActive.value = true
  fallbackUntil = Date.now() + FALLBACK_TTL_MS
}

function createSafeApi(): typeof realApi {
  const handler: ProxyHandler<typeof realApi> = {
    get(target, propKey, receiver) {
      const realMethod = Reflect.get(target, propKey, receiver)
      const mockMethod = Reflect.get(mockApi, propKey, mockApi)

      if (typeof realMethod !== 'function') {
        return realMethod
      }

      return async (...args: any[]) => {
        // 写操作/认证接口：只走真实后端，失败直接抛错，绝不静默降级
        if (shouldNeverFallback(propKey)) {
          return await realMethod.apply(target, args)
        }

        // 降级冷却窗口内直接走 mock；窗口过期后重新探测 realApi
        if (inFallbackWindow() && typeof mockMethod === 'function') {
          try {
            return await mockMethod.apply(mockApi, args)
          } catch (e) {
            console.warn(`[MockApi] Call to ${String(propKey)} failed:`, e)
          }
        }

        try {
          return await realMethod.apply(target, args)
        } catch (err: any) {
          console.warn(`[SmartApi] realApi.${String(propKey)} error, auto falling back to mockApi:`, err?.message || err)
          enterFallback()
          if (typeof mockMethod === 'function') {
            return await mockMethod.apply(mockApi, args)
          }
          throw err
        }
      }
    }
  }

  return new Proxy(realApi, handler)
}

export const api = createSafeApi()
export type { User, Device, Sensor, DataPoint } from './mockApi'
