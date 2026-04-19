/**
 * useCredentialMatch —— 根据 Git URL 在用户凭证库里匹配可用凭证
 *
 * 用法：
 *   const { match } = useCredentialMatch()
 *   const credStatus = computed(() => match(form.gitUrl))
 *   // credStatus.value: { state: 'matched' | 'missing' | 'idle', host, credential?, others? }
 */
import { ref, onMounted } from 'vue'
import { codeAnalysisApi } from '@/api/codeAnalysis'
import type { RepoAuthCredential } from '@/types/codeAnalysis'

export type CredMatchState = 'idle' | 'matched' | 'missing'

export interface CredMatchResult {
  state: CredMatchState
  host: string
  credential?: RepoAuthCredential
  others?: RepoAuthCredential[]
  count?: number
}

/** 从 Git URL 提取 host（含端口） */
export function extractHost(url: string | null | undefined): string {
  if (!url) return ''
  const u = url.trim()
  if (u.startsWith('git@')) {
    const colon = u.indexOf(':', 4)
    return colon > 4 ? u.slice(4, colon).toLowerCase() : ''
  }
  const m = u.match(/^https?:\/\/([^\/]+)/i)
  return m ? m[1].toLowerCase() : ''
}

export function useCredentialMatch() {
  const credentials = ref<RepoAuthCredential[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  async function reload() {
    loading.value = true
    try {
      credentials.value = await codeAnalysisApi.listCredentials()
    } catch {
      // 401 / 未登录或其他错误都忽略，空列表即可
    } finally {
      loading.value = false
      loaded.value = true
    }
  }

  onMounted(reload)

  /** 纯函数：给 Git URL 返回匹配结果 */
  function match(gitUrl: string | null | undefined): CredMatchResult {
    const host = extractHost(gitUrl || '')
    if (!host) return { state: 'idle', host: '' }

    const sameHost = credentials.value.filter(
      c => (c.gitHost || '').toLowerCase() === host
    )
    const defaultCred = sameHost.find(c => c.isDefault === 1) || sameHost[0]

    if (defaultCred) {
      return {
        state: 'matched',
        host,
        credential: defaultCred,
        count: sameHost.length,
        others: sameHost.filter(c => c.id !== defaultCred.id),
      }
    }
    return { state: 'missing', host }
  }

  return { credentials, loaded, loading, reload, match }
}
