export class ApiError extends Error {
  constructor(message, status = 0, payload = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
    this.fieldErrors = payload?.errors || {}
  }
}

async function parseResponseBody(httpResponse) {
  if (httpResponse.status === 204) return null
  const responseText = await httpResponse.text()
  if (!responseText) return null
  try {
    return JSON.parse(responseText)
  } catch (error) {
    return responseText
  }
}

function assertValidPermissionRequirement(permissionRequirement, permissionKey) {
  const validPermission = typeof permissionRequirement?.permission === 'string' &&
    /^[A-Z][A-Z0-9_]*$/.test(permissionRequirement.permission.trim())
  const validScope = permissionRequirement?.scope === undefined || permissionRequirement?.scope === null ||
    typeof permissionRequirement.scope === 'string'
  const validRoles = Array.isArray(permissionRequirement?.roles) &&
    permissionRequirement.roles.every((role) => typeof role === 'string' && role.trim())

  if (!validPermission || !validScope || !validRoles) {
    throw new Error(`frontend-config permissions.${permissionKey} is invalid.`)
  }
}

export function validateFrontendConfig(frontendConfig) {
  if (!frontendConfig || typeof frontendConfig !== 'object') {
    throw new Error('frontend-config did not return a JSON object.')
  }
  if (typeof frontendConfig.authenticationRequired !== 'boolean' ||
      typeof frontendConfig.loginConfigured !== 'boolean' ||
      typeof frontendConfig.signupEnabled !== 'boolean' ||
      typeof frontendConfig.provider !== 'string' ||
      !frontendConfig.permissions) {
    throw new Error('frontend-config is missing required authentication fields.')
  }

  const authenticationProvider = frontendConfig.provider.toLowerCase()
  if (authenticationProvider === 'cognito') {
    if (!frontendConfig.cognito ||
        typeof frontendConfig.cognito.domain !== 'string' ||
        typeof frontendConfig.cognito.clientId !== 'string' ||
        !Array.isArray(frontendConfig.oauthScopes)) {
      throw new Error('frontend-config is missing required Cognito fields.')
    }
  } else if (authenticationProvider === 'local') {
    if (frontendConfig.local !== undefined &&
        (!frontendConfig.local || typeof frontendConfig.local !== 'object' ||
         (frontendConfig.local.loginEndpoint !== undefined &&
          typeof frontendConfig.local.loginEndpoint !== 'string'))) {
      throw new Error('frontend-config local login configuration is invalid.')
    }
  } else if (frontendConfig.authenticationRequired) {
    throw new Error(`frontend-config authentication provider “${frontendConfig.provider}” is not supported.`)
  }

  assertValidPermissionRequirement(frontendConfig.permissions.transactionWrite, 'transactionWrite')
  assertValidPermissionRequirement(frontendConfig.permissions.assessmentRead, 'assessmentRead')
  return frontendConfig
}

export async function loadFrontendConfig() {
  let response
  try {
    response = await window.fetch('/api/v1/frontend-config', {
      method: 'GET',
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    })
  } catch (error) {
    throw new ApiError('The application API could not be reached.', 0)
  }

  const payload = await parseResponseBody(response)
  if (!response.ok) {
    throw new ApiError(
      payload?.message || `Frontend configuration failed with HTTP ${response.status}.`,
      response.status,
      payload
    )
  }
  return validateFrontendConfig(payload)
}

function defaultErrorMessageForStatus(httpStatus) {
  if (httpStatus === 401) return 'Your session is no longer authenticated. Sign in again to continue.'
  if (httpStatus === 403) return 'The backend denied this action for your current permissions.'
  if (httpStatus === 404) return 'The requested resource was not found.'
  if (httpStatus >= 500) return 'The service could not complete the request. Try again shortly.'
  return 'The request could not be completed.'
}

export function createApiClient({ authenticationRequired, authClient, onSessionChanged }) {
  async function sendApiRequest(apiPath, requestOptions = {}, allowRefreshRetry = true) {
    if (!apiPath.startsWith('/api/')) {
      throw new Error('API requests must use a same-origin /api path.')
    }

    const headers = new Headers(requestOptions.headers || {})
    headers.set('Accept', 'application/json')
    if (requestOptions.body !== undefined && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }

    if (authenticationRequired) {
      const accessToken = await authClient.getValidAccessToken()
      headers.set('Authorization', `Bearer ${accessToken}`)
      onSessionChanged(authClient.getSession())
    }

    let response
    try {
      response = await window.fetch(apiPath, {
        ...requestOptions,
        credentials: 'same-origin',
        headers,
        body: requestOptions.body === undefined ? undefined : JSON.stringify(requestOptions.body)
      })
    } catch (error) {
      throw new ApiError('The application API could not be reached.', 0)
    }

    if (response.status === 401 && authenticationRequired && allowRefreshRetry) {
      try {
        await authClient.refreshTokens(true)
        onSessionChanged(authClient.getSession())
        return sendApiRequest(apiPath, requestOptions, false)
      } catch (error) {
        authClient.clearSession()
        onSessionChanged(null)
      }
    }

    const payload = await parseResponseBody(response)
    if (!response.ok) {
      throw new ApiError(
        payload?.message || defaultErrorMessageForStatus(response.status),
        response.status,
        payload
      )
    }
    return payload
  }

  return {
    get(apiPath) {
      return sendApiRequest(apiPath, { method: 'GET' })
    },
    post(apiPath, requestBody) {
      return sendApiRequest(apiPath, { method: 'POST', body: requestBody })
    }
  }
}
