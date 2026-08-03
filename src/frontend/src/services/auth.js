const TOKEN_STORAGE_KEY = 'fraud-rule-engine.oauth.tokens'
const LOCAL_TOKEN_STORAGE_KEY = 'fraud-rule-engine.local.tokens'
const AUTH_TRANSACTION_KEY = 'fraud-rule-engine.oauth.transaction'
const EXPIRY_SKEW_MS = 60 * 1000
const AUTH_TRANSACTION_TTL_MS = 10 * 60 * 1000

export class AuthenticationError extends Error {
  constructor(message, code = 'authentication_error') {
    super(message)
    this.name = 'AuthenticationError'
    this.code = code
  }
}

function encodeBase64Url(bytes) {
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return window.btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
}

function generateRandomBase64Url(byteLength) {
  const bytes = new Uint8Array(byteLength)
  window.crypto.getRandomValues(bytes)
  return encodeBase64Url(bytes)
}

async function createPkceCodeChallenge(codeVerifier) {
  const digest = await window.crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(codeVerifier)
  )
  return encodeBase64Url(new Uint8Array(digest))
}

function normalizeCognitoDomain(configuredDomain) {
  const rawDomain = String(configuredDomain || '').trim().replace(/\/+$/g, '')
  if (!rawDomain) {
    throw new AuthenticationError('The Cognito login domain is not configured.', 'configuration_error')
  }

  const candidate = /^https:\/\//i.test(rawDomain) ? rawDomain : `https://${rawDomain}`
  let parsed
  try {
    parsed = new URL(candidate)
  } catch (error) {
    throw new AuthenticationError('The Cognito login domain is invalid.', 'configuration_error')
  }

  if (parsed.protocol !== 'https:') {
    throw new AuthenticationError('The Cognito login domain must use HTTPS.', 'configuration_error')
  }
  return parsed.origin
}

function currentApplicationRedirectUri() {
  return `${window.location.origin}/`
}

function decodeBase64UrlSegment(encodedSegment) {
  const normalized = encodedSegment.replace(/-/g, '+').replace(/_/g, '/')
  const padding = '='.repeat((4 - (normalized.length % 4)) % 4)
  const binary = window.atob(`${normalized}${padding}`)
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

export function decodeJwtClaims(token) {
  if (!token || typeof token !== 'string') return {}
  const segments = token.split('.')
  if (segments.length !== 3) return {}

  try {
    return JSON.parse(decodeBase64UrlSegment(segments[1]))
  } catch (error) {
    return {}
  }
}

function parseClaimValues(rawClaimValue, separatorPattern) {
  const claimValues = typeof rawClaimValue === 'string'
    ? [rawClaimValue]
    : Array.isArray(rawClaimValue) ? rawClaimValue.filter((entry) => typeof entry === 'string') : []

  return claimValues
    .flatMap((entry) => entry.trim().split(separatorPattern))
    .filter(Boolean)
}

function parseSpaceDelimitedClaimValues(rawClaimValue) {
  return parseClaimValues(rawClaimValue, /\s+/)
}

function parseLocalClaimValues(rawClaimValue) {
  return parseClaimValues(rawClaimValue, /[\s,]+/)
}

function normalizeConfiguredRoles(configuredRoleValues) {
  if (!Array.isArray(configuredRoleValues)) return []
  return configuredRoleValues
    .filter((entry) => typeof entry === 'string')
    .map((entry) => entry.trim())
    .filter(Boolean)
}

function removeDuplicateValues(values) {
  return [...new Set(values)]
}

function extractCognitoGroups(accessTokenClaims) {
  return removeDuplicateValues(parseSpaceDelimitedClaimValues(accessTokenClaims['cognito:groups']))
}

function extractLocalRoles(accessTokenClaims) {
  return removeDuplicateValues(parseLocalClaimValues(accessTokenClaims.roles))
}

function extractOauthScopes(accessTokenClaims) {
  return removeDuplicateValues([
    ...parseSpaceDelimitedClaimValues(accessTokenClaims.scope),
    ...parseSpaceDelimitedClaimValues(accessTokenClaims.scp)
  ])
}

function extractLocalPermissions(accessTokenClaims) {
  return removeDuplicateValues(parseLocalClaimValues(accessTokenClaims.permissions))
}

function buildAuthenticatedSession(tokenSet) {
  const accessClaims = decodeJwtClaims(tokenSet.accessToken)
  const idClaims = decodeJwtClaims(tokenSet.idToken)
  const scopes = extractOauthScopes(accessClaims)
  const permissions = extractLocalPermissions(accessClaims)
  const groups = extractCognitoGroups(accessClaims)
  const roles = extractLocalRoles(accessClaims)
  const displayName = idClaims.name || accessClaims.name || idClaims.email ||
    accessClaims.email || idClaims.preferred_username || accessClaims.preferred_username ||
    idClaims['cognito:username'] || accessClaims.username || accessClaims.sub || 'Signed-in user'

  return {
    expiresAt: tokenSet.expiresAt,
    scopes,
    permissions,
    groups,
    roles,
    profile: {
      displayName,
      email: idClaims.email || accessClaims.email || '',
      username: idClaims['cognito:username'] || idClaims.preferred_username ||
        accessClaims.preferred_username || accessClaims.username || ''
    }
  }
}

function readSessionStorageJson(storageKey) {
  try {
    const serializedValue = window.sessionStorage.getItem(storageKey)
    return serializedValue ? JSON.parse(serializedValue) : null
  } catch (error) {
    return null
  }
}

function writeSessionStorageJson(storageKey, storedValue) {
  try {
    window.sessionStorage.setItem(storageKey, JSON.stringify(storedValue))
  } catch (error) {
    throw new AuthenticationError(
      'This browser could not save the secure sign-in session. Allow session storage and try again.',
      'storage_error'
    )
  }
}

function removeAuthorizationParametersFromUrl() {
  const url = new URL(window.location.href)
  ;['code', 'state', 'error', 'error_description', 'error_uri'].forEach((parameter) => {
    url.searchParams.delete(parameter)
  })
  const query = url.searchParams.toString()
  window.history.replaceState({}, document.title, `${url.pathname}${query ? `?${query}` : ''}${url.hash}`)
}

function resolveCognitoTokenExpiryTime(tokenResponse) {
  const accessClaims = decodeJwtClaims(tokenResponse.access_token)
  if (Number.isFinite(Number(accessClaims.exp))) {
    return Number(accessClaims.exp) * 1000
  }
  const expiresIn = Number(tokenResponse.expires_in)
  return Date.now() + (Number.isFinite(expiresIn) ? expiresIn : 3600) * 1000
}

function resolveLocalTokenExpiryTime(tokenResponse) {
  const accessToken = tokenResponse.accessToken || tokenResponse.access_token
  const accessClaims = decodeJwtClaims(accessToken)
  if (Number.isFinite(Number(accessClaims.exp))) {
    return Number(accessClaims.exp) * 1000
  }
  const expiresIn = Number(tokenResponse.expiresIn ?? tokenResponse.expires_in)
  if (!Number.isFinite(expiresIn) || expiresIn <= 0) {
    throw new AuthenticationError(
      'The local login response did not include a valid token expiry.',
      'invalid_token_response'
    )
  }
  return Date.now() + expiresIn * 1000
}

function validateLocalLoginEndpoint(configuredEndpoint) {
  const endpoint = String(configuredEndpoint || '/api/v1/auth/login').trim()
  if (!endpoint.startsWith('/api/') || endpoint.startsWith('//')) {
    throw new AuthenticationError(
      'The local login endpoint must be a same-origin /api path.',
      'configuration_error'
    )
  }
  return endpoint
}

export function evaluatePermission(permissionRequirement, authenticatedSession, authenticationDisabled) {
  const requiredPermission = typeof permissionRequirement?.permission === 'string'
    ? permissionRequirement.permission.trim()
    : ''
  const requiredScope = typeof permissionRequirement?.scope === 'string'
    ? permissionRequirement.scope.trim()
    : ''
  const requiredRoles = normalizeConfiguredRoles(permissionRequirement?.roles)
  const grantedPermissions = parseLocalClaimValues(authenticatedSession?.permissions)
  const grantedScopes = parseSpaceDelimitedClaimValues(authenticatedSession?.scopes)
  const grantedGroups = parseSpaceDelimitedClaimValues(authenticatedSession?.groups)
  const grantedRoles = parseLocalClaimValues(authenticatedSession?.roles)
  const permissionClaimGranted = Boolean(requiredPermission) &&
    grantedPermissions.includes(requiredPermission)
  const hostedScopeGranted = Boolean(requiredScope) && grantedScopes.includes(requiredScope)
  const permissionGranted = authenticationDisabled || (requiredScope
    ? hostedScopeGranted
    : permissionClaimGranted)
  const hostedRoleGranted = requiredRoles.length === 0 ||
    requiredRoles.some((role) => grantedGroups.includes(role))
  const localRoleGranted = requiredRoles.length === 0 ||
    requiredRoles.some((role) => grantedRoles.includes(role))
  const roleGranted = authenticationDisabled || (requiredScope
    ? hostedRoleGranted
    : localRoleGranted)

  return {
    allowed: permissionGranted && roleGranted,
    permissionGranted,
    permissionClaimGranted,
    hostedScopeGranted,
    roleGranted,
    hostedRoleGranted,
    localRoleGranted,
    requiredPermission,
    requiredScope,
    requiredRoles
  }
}

export class CognitoPkceClient {
  constructor(frontendConfig) {
    this.domain = normalizeCognitoDomain(frontendConfig.cognito.domain)
    this.clientId = String(frontendConfig.cognito.clientId || '').trim()
    this.scopes = Array.isArray(frontendConfig.oauthScopes)
      ? frontendConfig.oauthScopes.map(String).filter(Boolean)
      : []
    this.callbackUri = currentApplicationRedirectUri()
    this.tokenSet = this.readStoredTokenSet()
    this.refreshPromise = null

    if (!this.clientId) {
      throw new AuthenticationError('The Cognito application client ID is not configured.', 'configuration_error')
    }
  }

  readStoredTokenSet() {
    const tokenSet = readSessionStorageJson(TOKEN_STORAGE_KEY)
    if (!tokenSet?.accessToken || !Number.isFinite(Number(tokenSet.expiresAt))) return null
    return tokenSet
  }

  getSession() {
    return this.tokenSet ? buildAuthenticatedSession(this.tokenSet) : null
  }

  clearSession() {
    this.tokenSet = null
    this.refreshPromise = null
    window.sessionStorage.removeItem(TOKEN_STORAGE_KEY)
    window.sessionStorage.removeItem(AUTH_TRANSACTION_KEY)
  }

  async restoreSession() {
    if (!this.tokenSet) return null
    if (Date.now() >= Number(this.tokenSet.expiresAt) - EXPIRY_SKEW_MS) {
      if (!this.tokenSet.refreshToken) {
        this.clearSession()
        return null
      }
      try {
        await this.refreshTokens()
      } catch (error) {
        this.clearSession()
        throw new AuthenticationError(
          'Your previous session has expired. Sign in again to continue.',
          'session_expired'
        )
      }
    }
    return this.getSession()
  }

  hasOauthAuthorizationResponse() {
    const parameters = new URLSearchParams(window.location.search)
    return parameters.has('code') || parameters.has('error')
  }

  async handleOauthAuthorizationResponse() {
    if (!this.hasOauthAuthorizationResponse()) return null

    const parameters = new URLSearchParams(window.location.search)
    const returnedState = parameters.get('state') || ''
    const transaction = readSessionStorageJson(AUTH_TRANSACTION_KEY)
    const transactionExpired = !transaction?.createdAt ||
      Date.now() - Number(transaction.createdAt) > AUTH_TRANSACTION_TTL_MS

    if (transactionExpired || !transaction?.state || transaction.state !== returnedState) {
      this.clearSession()
      removeAuthorizationParametersFromUrl()
      throw new AuthenticationError(
        'The sign-in response could not be verified. Please start sign-in again.',
        'state_mismatch'
      )
    }

    if (parameters.has('error')) {
      const message = parameters.get('error_description') || 'Cognito did not complete sign-in.'
      window.sessionStorage.removeItem(AUTH_TRANSACTION_KEY)
      removeAuthorizationParametersFromUrl()
      throw new AuthenticationError(message, parameters.get('error') || 'authorization_error')
    }

    const code = parameters.get('code')
    if (!code || !transaction.verifier) {
      window.sessionStorage.removeItem(AUTH_TRANSACTION_KEY)
      removeAuthorizationParametersFromUrl()
      throw new AuthenticationError('The sign-in response was incomplete.', 'invalid_response')
    }

    try {
      const response = await this.requestTokens({
        grant_type: 'authorization_code',
        client_id: this.clientId,
        code,
        redirect_uri: this.callbackUri,
        code_verifier: transaction.verifier
      })
      const idClaims = decodeJwtClaims(response.id_token)
      if (transaction.nonce && idClaims.nonce !== transaction.nonce) {
        throw new AuthenticationError(
          'The identity response could not be verified. Please sign in again.',
          'nonce_mismatch'
        )
      }
      this.storeTokenResponse(response)
      return this.getSession()
    } finally {
      window.sessionStorage.removeItem(AUTH_TRANSACTION_KEY)
      removeAuthorizationParametersFromUrl()
    }
  }

  async startOauthAuthorization(authorizationMode = 'login') {
    if (!window.crypto?.subtle) {
      throw new AuthenticationError(
        'This browser does not support the security features required for sign-in.',
        'browser_not_supported'
      )
    }

    const verifier = generateRandomBase64Url(64)
    const state = generateRandomBase64Url(32)
    const nonce = generateRandomBase64Url(32)
    const challenge = await createPkceCodeChallenge(verifier)
    writeSessionStorageJson(AUTH_TRANSACTION_KEY, {
      verifier,
      state,
      nonce,
      createdAt: Date.now()
    })

    const authorizationPath = authorizationMode === 'signup' ? '/signup' : '/oauth2/authorize'
    const authorizationUrl = new URL(authorizationPath, this.domain)
    authorizationUrl.searchParams.set('response_type', 'code')
    authorizationUrl.searchParams.set('client_id', this.clientId)
    authorizationUrl.searchParams.set('redirect_uri', this.callbackUri)
    authorizationUrl.searchParams.set('scope', this.scopes.join(' '))
    authorizationUrl.searchParams.set('state', state)
    authorizationUrl.searchParams.set('nonce', nonce)
    authorizationUrl.searchParams.set('code_challenge', challenge)
    authorizationUrl.searchParams.set('code_challenge_method', 'S256')
    window.location.assign(authorizationUrl.toString())
  }

  async getValidAccessToken() {
    if (!this.tokenSet) {
      throw new AuthenticationError('Sign in to continue.', 'not_authenticated')
    }
    if (Date.now() >= Number(this.tokenSet.expiresAt) - EXPIRY_SKEW_MS) {
      await this.refreshTokens()
    }
    return this.tokenSet.accessToken
  }

  async refreshTokens(forceRefresh = false) {
    if (!this.tokenSet?.refreshToken) {
      throw new AuthenticationError('Your session cannot be refreshed.', 'session_expired')
    }
    if (!forceRefresh && Date.now() < Number(this.tokenSet.expiresAt) - EXPIRY_SKEW_MS) {
      return this.getSession()
    }
    if (this.refreshPromise) return this.refreshPromise

    const refreshToken = this.tokenSet.refreshToken
    this.refreshPromise = this.requestTokens({
      grant_type: 'refresh_token',
      client_id: this.clientId,
      refresh_token: refreshToken
    }).then((response) => {
      this.storeTokenResponse(response, refreshToken)
      return this.getSession()
    }).catch((error) => {
      this.clearSession()
      throw error
    }).finally(() => {
      this.refreshPromise = null
    })

    return this.refreshPromise
  }

  storeTokenResponse(tokenResponse, existingRefreshToken = '') {
    if (!tokenResponse?.access_token) {
      throw new AuthenticationError('Cognito returned an incomplete token response.', 'invalid_token_response')
    }
    this.tokenSet = {
      accessToken: tokenResponse.access_token,
      idToken: tokenResponse.id_token || this.tokenSet?.idToken || '',
      refreshToken: tokenResponse.refresh_token || existingRefreshToken,
      tokenType: tokenResponse.token_type || 'Bearer',
      expiresAt: resolveCognitoTokenExpiryTime(tokenResponse)
    }
    writeSessionStorageJson(TOKEN_STORAGE_KEY, this.tokenSet)
  }

  async requestTokens(tokenRequestParameters) {
    let response
    try {
      response = await window.fetch(`${this.domain}/oauth2/token`, {
        method: 'POST',
        mode: 'cors',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: new URLSearchParams(tokenRequestParameters)
      })
    } catch (error) {
      throw new AuthenticationError(
        'The Cognito token service could not be reached. Check your connection and try again.',
        'token_service_unavailable'
      )
    }

    let responseBody = {}
    try {
      responseBody = await response.json()
    } catch (error) {
      responseBody = {}
    }

    if (!response.ok) {
      throw new AuthenticationError(
        responseBody.error_description || 'Cognito could not complete the token request.',
        responseBody.error || 'token_request_failed'
      )
    }
    return responseBody
  }

  logout() {
    const refreshToken = this.tokenSet?.refreshToken
    this.clearSession()

    if (refreshToken) {
      window.fetch(`${this.domain}/oauth2/revoke`, {
        method: 'POST',
        mode: 'cors',
        keepalive: true,
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: new URLSearchParams({
          token: refreshToken,
          client_id: this.clientId
        })
      }).catch(() => {})
    }

    const logoutUrl = new URL('/logout', this.domain)
    logoutUrl.searchParams.set('client_id', this.clientId)
    logoutUrl.searchParams.set('logout_uri', this.callbackUri)
    window.location.assign(logoutUrl.toString())
  }
}

export class LocalJwtClient {
  constructor(frontendConfig) {
    this.loginEndpoint = validateLocalLoginEndpoint(frontendConfig.local?.loginEndpoint)
    this.tokenSet = this.readStoredTokenSet()
  }

  readStoredTokenSet() {
    const tokenSet = readSessionStorageJson(LOCAL_TOKEN_STORAGE_KEY)
    if (!tokenSet?.accessToken || !Number.isFinite(Number(tokenSet.expiresAt))) return null
    return tokenSet
  }

  getSession() {
    return this.tokenSet ? buildAuthenticatedSession(this.tokenSet) : null
  }

  clearSession() {
    this.tokenSet = null
    window.sessionStorage.removeItem(LOCAL_TOKEN_STORAGE_KEY)
  }

  async restoreSession() {
    if (!this.tokenSet) return null
    if (Date.now() >= Number(this.tokenSet.expiresAt) - EXPIRY_SKEW_MS) {
      this.clearSession()
      throw new AuthenticationError(
        'Your previous local session has expired. Sign in again to continue.',
        'session_expired'
      )
    }
    return this.getSession()
  }

  async login(username, password) {
    const normalizedUsername = String(username || '').trim()
    if (!normalizedUsername || !password) {
      throw new AuthenticationError('Enter both your username and password.', 'invalid_credentials')
    }

    let response
    try {
      response = await window.fetch(this.loginEndpoint, {
        method: 'POST',
        credentials: 'same-origin',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          username: normalizedUsername,
          password
        })
      })
    } catch (error) {
      throw new AuthenticationError(
        'The local login service could not be reached. Check that the application is running.',
        'login_service_unavailable'
      )
    }

    let responseBody = {}
    try {
      responseBody = await response.json()
    } catch (error) {
      responseBody = {}
    }

    if (!response.ok) {
      const message = response.status === 401
        ? 'The username or password is incorrect.'
        : responseBody.message || 'Local sign-in could not be completed.'
      throw new AuthenticationError(message, response.status === 401 ? 'invalid_credentials' : 'login_failed')
    }

    const accessToken = responseBody.accessToken || responseBody.access_token
    const tokenType = responseBody.tokenType || responseBody.token_type || 'Bearer'
    if (!accessToken || String(tokenType).toLowerCase() !== 'bearer') {
      throw new AuthenticationError(
        'The local login service returned an invalid token response.',
        'invalid_token_response'
      )
    }

    const expiresAt = resolveLocalTokenExpiryTime(responseBody)
    if (expiresAt <= Date.now() + EXPIRY_SKEW_MS) {
      throw new AuthenticationError(
        'The local login service returned an expired token.',
        'invalid_token_response'
      )
    }

    this.tokenSet = {
      accessToken,
      idToken: '',
      tokenType: 'Bearer',
      expiresAt
    }
    writeSessionStorageJson(LOCAL_TOKEN_STORAGE_KEY, this.tokenSet)
    return this.getSession()
  }

  async getValidAccessToken() {
    if (!this.tokenSet) {
      throw new AuthenticationError('Sign in to continue.', 'not_authenticated')
    }
    if (Date.now() >= Number(this.tokenSet.expiresAt) - EXPIRY_SKEW_MS) {
      this.clearSession()
      throw new AuthenticationError('Your local session has expired.', 'session_expired')
    }
    return this.tokenSet.accessToken
  }

  async refreshTokens() {
    this.clearSession()
    throw new AuthenticationError(
      'Local development sessions cannot be refreshed. Sign in again to continue.',
      'session_expired'
    )
  }

  logout() {
    this.clearSession()
  }
}
