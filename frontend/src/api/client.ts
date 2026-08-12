const TOKEN_KEY = 'recon.token'

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

export function storedToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function storeToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

interface RequestOptions {
  method?: string
  body?: unknown
  formData?: FormData
  query?: Record<string, string | number | undefined | null>
}

/**
 * The API answers errors as RFC 9457 problem details, so failures surface with the server's own
 * machine-readable code rather than a generic "request failed".
 */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(path, window.location.origin)
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  }

  const headers: Record<string, string> = {}
  const token = storedToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(url, {
    method: options.method ?? 'GET',
    headers,
    body: options.formData ?? (options.body === undefined ? undefined : JSON.stringify(options.body)),
  })

  if (response.status === 401) {
    clearToken()
  }

  if (!response.ok) {
    let code = 'REQUEST_FAILED'
    let detail = `${response.status} ${response.statusText}`
    try {
      const problem = await response.json()
      code = problem.code ?? code
      detail = problem.detail ?? detail
    } catch {
      // A non-JSON error body leaves the status line as the best available message.
    }
    throw new ApiError(response.status, code, detail)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
